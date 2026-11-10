#!/usr/bin/env python3
"""paramiko-based `ssh` replacement for git (sandbox me ssh binary nahi hai).
Git ise core.sshCommand ke through call karta hai: <wrapper> [-p PORT] [-o X] HOST 'COMMAND'
Bidirectional pumping (threads) — git push/pull ka sideband protocol iske bina deadlock hota hai."""
import sys
import threading
import pathlib
import paramiko

KEY = "/home/z/my-project/.deploy-key/id_ed25519"

args = sys.argv[1:]
port, host, cmd = 22, None, []
i = 0
while i < len(args):
    a = args[i]
    if a == "-p":
        port = int(args[i + 1])
        i += 2
        continue
    if a == "-o":
        i += 2
        continue
    if a.startswith("-"):
        i += 1
        continue
    if host is None:
        host = a
    else:
        cmd.append(a)
    i += 1

if not host:
    sys.stderr.write("gh-ssh: host missing\n")
    sys.exit(2)

# scp-style syntax me username host ke saath aata hai (git@github.com) — alag karo
if "@" in host:
    host = host.split("@", 1)[1]
if host.startswith("[") and "]" in host:  # [host]:port syntax
    host, maybe_port = host[1:].split("]", 1)
    if maybe_port.startswith(":"):
        port = int(maybe_port[1:])

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

# Sandbox DNS AF_UNSPEC par fail hota hai — IPv4-only resolve karo, retry ke saath
import socket
import time

last_err = None
ip = None
for attempt in range(3):
    try:
        infos = socket.getaddrinfo(host, port, socket.AF_INET, socket.SOCK_STREAM)
        ip = infos[0][4][0]
        break
    except Exception as e:
        last_err = e
        time.sleep(1.5)
if ip is None:
    sys.stderr.write(f"gh-ssh: DNS resolve fail: {last_err}\n")
    sys.exit(3)

last_err = None
for attempt in range(3):
    try:
        client.connect(
            ip,
            port=port,
            username="git",
            key_filename=KEY,
            look_for_keys=False,
            allow_agent=False,
            timeout=20,
            banner_timeout=20,
        )
        break
    except Exception as e:
        last_err = e
        time.sleep(1.5)
else:
    sys.stderr.write(f"gh-ssh: connect fail: {last_err}\n")
    sys.exit(3)
stdin, stdout, stderr = client.exec_command(" ".join(cmd), bufsize=0)
chan = stdout.channel


def pump_stdin():
    try:
        while True:
            data = sys.stdin.buffer.read(32768)
            if not data:
                break
            chan.sendall(data)
    except Exception:
        pass
    finally:
        try:
            chan.shutdown_write()
        except Exception:
            pass


def pump_stderr():
    try:
        for chunk in iter(lambda: stderr.readline(), b""):
            sys.stderr.buffer.write(chunk)
            sys.stderr.buffer.flush()
    except Exception:
        pass


t_in = threading.Thread(target=pump_stdin, daemon=True)
t_err = threading.Thread(target=pump_stderr, daemon=True)
t_in.start()
t_err.start()

try:
    while True:
        data = chan.recv(32768)
        if not data:
            break
        sys.stdout.buffer.write(data)
        sys.stdout.buffer.flush()
except Exception:
    pass

t_in.join(timeout=10)
rc = 0
try:
    rc = chan.recv_exit_status()
except Exception:
    rc = 1
client.close()
sys.exit(rc)
