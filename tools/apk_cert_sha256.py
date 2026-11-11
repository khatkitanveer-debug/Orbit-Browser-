#!/usr/bin/env python3
"""APK cert SHA-256 + versionName/versionCode checker (pure python, no SDK).

1) APK Signing Block: v2 (0x7109871a) / v3 (0xf05368c0) se pehla X509 cert
   nikal ke SHA-256 deta hai. NOTE: signers list DOUBLE length-prefixed hoti hai
   -> walk(walk(value)[0]) hi sahi unpack hai.
2) Binary AndroidManifest.xml se versionCode (0x0101021b) + versionName
   (0x0101021c) attributes parse karta hai.
Cross-check ke liye openssl command bhi print hota hai.
"""
import hashlib
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

def u32(b, o): return struct.unpack_from("<I", b, o)[0]
def u64(b, o): return struct.unpack_from("<Q", b, o)[0]

def walk(block):
    """Length-prefixed TLV walk: yields (payload, offset_after_len)."""
    out, o = [], 0
    while o + 4 <= len(block):
        n = u32(block, o); o += 4
        if n == 0 or o + n > len(block): break
        out.append((block[o:o + n], o))
        o += n
    return out

def signing_block(data):
    # EOCD -> CD offset -> magic 'APK Sig Block 42' just before CD
    eocd = data.rfind(b"PK\x05\x06")
    cd_off = u32(data, eocd + 16)
    return data[cd_off - 24: cd_off], cd_off - 24  # block starts at magic-8? magic at sb_end-24

def extract_cert_der(data: bytes) -> bytes:
    # robust: find the v3/v2 block ID directly; pair_len sits 8 bytes before ID
    def find_block(pid: int):
        pat = struct.pack("<I", pid)
        i = data.find(pat)
        if i == -1: return None
        pair_len = u64(data, i - 8)
        return data[i + 4: i + pair_len]      # payload = pair_len - 4 bytes after ID
    v3val = find_block(0xf05368c0)
    value = v3val or find_block(0x7109871a)
    assert value is not None, "no v2/v3 signature"
    signers = walk(walk(value)[0][0])       # outer length-prefix!
    signer = walk(signers[0][0])
    signed_data = walk(signer[0][0])
    if v3val is not None:
        certs = walk(signed_data[0][0])     # v3: certs field first
    else:
        certs = walk(signed_data[1][0])     # v2: digests, then certs
    return certs[0][0]

def axml_attrs(data: bytes):
    """Minimal AXML parse: return {name: (vtype, val, raw_str)} for manifest element."""
    sp_off = 8
    assert u16(data, sp_off) == 0x0001, "not a string pool"     # RES_STRING_POOL_TYPE
    utf8 = bool(u16(data, sp_off + 16) & 0x100)                  # UTF8_FLAG
    str_count = u32(data, sp_off + 8)
    strs_base = sp_off + u32(data, sp_off + 20)                  # stringsStart rel. to chunk
    strings = []
    for i in range(str_count):
        off = strs_base + u32(data, sp_off + 28 + 4 * i)
        if utf8:
            # u16len (or u8 pair), u8len, then bytes
            n = data[off + 1] if data[off] & 0x80 else data[off]
            ln = data[off + 3] if data[off + 2] & 0x80 else data[off + 2]
            p = off + 2 + (2 if data[off] & 0x80 else 0)
            ln2 = data[p + 1] if data[p] & 0x80 else data[p]
            p += 2 if data[p] & 0x80 else 1
            strings.append(data[p:p + ln2].decode("utf-8", "replace"))
        else:
            n = u16(data, off)
            strings.append(data[off + 2: off + 2 + 2 * n].decode("utf-16-le", "replace"))
    o = 8
    while o + 8 <= len(data):
        ctype = u16(data, o)
        csize = u32(data, o + 4)
        if ctype == 0x0102:  # RES_XML_START_ELEMENT
            base = o + 16                       # attrExt start
            name_idx = u32(data, base + 4)
            attr_start = u16(data, base + 8)
            attr_count = u16(data, base + 12)
            el_name = strings[name_idx] if name_idx < len(strings) else "?"
            if el_name == "manifest":
                out = {}
                for i in range(attr_count):
                    a = base + attr_start + i * 20
                    nm = u32(data, a + 4)
                    raw = u32(data, a + 8)
                    vtype = data[a + 15]
                    val = u32(data, a + 16)
                    aname = strings[nm] if nm < len(strings) else "?"
                    raws = strings[raw] if (raw != 0xFFFFFFFF and raw < len(strings)) else None
                    out[aname] = (vtype, val, raws)
                return out
        o += csize
    return {}

def u16(b, o): return struct.unpack_from("<H", b, o)[0]

def main():
    apk = Path(sys.argv[1])
    data = apk.read_bytes()
    der = extract_cert_der(data)
    sha = hashlib.sha256(der).hexdigest()
    print("APK           :", apk.name, f"({len(data)/1e6:.2f} MB)")
    print("CERT SHA-256  :", sha)
    open("/tmp/cert.der", "wb").write(der)
    r = subprocess.run(["openssl", "x509", "-inform", "DER", "-in", "/tmp/cert.der",
                        "-noout", "-subject", "-fingerprint", "-sha256"],
                       capture_output=True, text=True)
    print(r.stdout.strip() or r.stderr.strip())
    zf = zipfile.ZipFile(apk)
    mf = zf.read("AndroidManifest.xml")
    attrs = axml_attrs(mf)
    vc = attrs.get("versionCode", (None, None, None))
    vn = attrs.get("versionName", (None, None, None))
    print("versionCode   :", vc[1])
    print("versionName   :", vn[2] or (vn[1] if isinstance(vn[1], int) else "?"))

if __name__ == "__main__":
    main()
