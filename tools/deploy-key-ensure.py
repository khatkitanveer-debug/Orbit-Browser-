#!/usr/bin/env python3
"""Orbit Browser deploy key ENSURE (seed-based, deterministic).

Sandbox reset par .deploy-key/ (gitignored) udd jata hai. Raw private key
store karna environment policy me allowed nahi — isliye sirf 32-byte RANDOM
SEED local git config (orbit.seedB64) me rehta hai (.git survive karta hai).
Ed25519 key us seed se DETERMINISTICALLY derive hoti hai — wahi seed = wahi
key = wahi public key, isliye GitHub Deploy key registration kabhi nahi badalti.

  1. .deploy-key/ maujood? -> kuch nahi (bas pub print)
  2. seed git config me?   -> key DERIVE karo (wahi purani wali)
  3. kuch nahi?            -> naya seed + naya key (GitHub me naya pub dalna hoga)

Usage: python3 tools/deploy-key-ensure.py
"""
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
from pathlib import Path
import base64
import os
import subprocess
import sys

REPO = "/home/z/my-project"
KEYDIR = Path(REPO) / ".deploy-key"
PRIV = KEYDIR / "id_ed25519"
PUB = KEYDIR / "id_ed25519.pub"


def git(*args: str) -> str:
    return subprocess.run(
        ["git", "-C", REPO, *args], capture_output=True, text=True,
    ).stdout.strip()


def key_from_seed(seed: bytes) -> Ed25519PrivateKey:
    return Ed25519PrivateKey.from_private_bytes(seed)


def write_files(key: Ed25519PrivateKey) -> None:
    priv_pem = key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.OpenSSH,
        serialization.NoEncryption(),
    )
    pub = key.public_key().public_bytes(
        serialization.Encoding.OpenSSH,
        serialization.PublicFormat.OpenSSH,
    )
    KEYDIR.mkdir(mode=0o700, exist_ok=True)
    PRIV.write_bytes(priv_pem)
    os.chmod(PRIV, 0o600)
    PUB.write_text(pub.decode() + " orbit-deploy-key\n")


def main() -> None:
    if PRIV.exists():
        print("Key file maujood hai — theek hai.")
    else:
        seed_b64 = git("config", "--get", "orbit.seedB64")
        if seed_b64:
            seed = base64.b64decode(seed_b64)
            if len(seed) != 32:
                print("seed corrupt — naya banata hoon", file=sys.stderr)
                seed = os.urandom(32)
                subprocess.run(
                    ["git", "-C", REPO, "config", "orbit.seedB64", base64.b64encode(seed).decode()],
                    check=True,
                )
            print("Seed git config se mila — wahi purani key DERIVE ho gayi (GitHub registration valid).")
        else:
            seed = os.urandom(32)
            subprocess.run(
                ["git", "-C", REPO, "config", "orbit.seedB64", base64.b64encode(seed).decode()],
                check=True,
            )
            print("NAYA seed ban gaya (git config me) — NAYA public key GitHub me dalna hoga.")
        write_files(key_from_seed(seed))
    print("---PUBLIC KEY (GitHub Deploy keys me honi chahiye, Allow write access)---")
    print(PUB.read_text().strip())


if __name__ == "__main__":
    main()
