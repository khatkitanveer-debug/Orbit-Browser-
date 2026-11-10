#!/usr/bin/env python3
"""Orbit Browser deploy key generator — sirf ek baar chalana hai.
Private key sandbox me hi rehti hai (.deploy-key/), public key GitHub me paste hoti hai."""
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
from pathlib import Path
import os

keydir = Path("/home/z/my-project/.deploy-key")
keydir.mkdir(mode=0o700, exist_ok=True)
priv_path = keydir / "id_ed25519"
pub_path = keydir / "id_ed25519.pub"

if priv_path.exists():
    print("Key pehle se hai — dobara nahi bana raha.")
else:
    key = Ed25519PrivateKey.generate()
    pem = key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.OpenSSH,
        serialization.NoEncryption(),
    )
    priv_path.write_bytes(pem)
    os.chmod(priv_path, 0o600)
    pub = key.public_key().public_bytes(
        serialization.Encoding.OpenSSH,
        serialization.PublicFormat.OpenSSH,
    )
    pub_path.write_text(pub.decode() + " orbit-deploy-key\n")
    print("Naya key-pair ban gaya.")

print("---PUBLIC KEY (GitHub me paste karni hai)---")
print(pub_path.read_text().strip())
