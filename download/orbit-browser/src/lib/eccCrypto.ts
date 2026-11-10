/**
 * Elliptic Curve Cryptography (ECC) Utility
 * Native Web Crypto API (SubtleCrypto) implementation for P-256, P-384, and P-521.
 * Generates ECDH / ECDSA key pairs and converts them to PEM, JWK, and Hex fingerprints.
 */

export type EccNamedCurve = 'P-256' | 'P-384' | 'P-521';
export type EccAlgorithmType = 'ECDH' | 'ECDSA';

export interface EccKeyPairResult {
  curve: EccNamedCurve;
  algorithm: EccAlgorithmType;
  publicKeyPem: string;
  privateKeyPem: string;
  publicKeyJwk: JsonWebKey;
  privateKeyJwk: JsonWebKey;
  fingerprintSha256: string;
  generatedAt: string;
}

// Convert ArrayBuffer to Base64
function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return window.btoa(binary);
}

// Format binary buffer into PEM block with standard 64-char wrapping
function toPem(buffer: ArrayBuffer, type: 'PUBLIC KEY' | 'PRIVATE KEY'): string {
  const base64 = arrayBufferToBase64(buffer);
  const formatted = base64.match(/.{1,64}/g)?.join('\n') || base64;
  return `-----BEGIN ${type}-----\n${formatted}\n-----END ${type}-----`;
}

// Format SHA-256 hash as colon-separated hex fingerprint
function toHexFingerprint(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, '0').toUpperCase())
    .join(':');
}

/**
 * Generate a new ECC Key Pair locally in hardware/browser memory.
 */
export async function generateEccKeyPair(
  curve: EccNamedCurve = 'P-256',
  algorithm: EccAlgorithmType = 'ECDH'
): Promise<EccKeyPairResult> {
  const isEcdh = algorithm === 'ECDH';

  const keyGenAlgorithm: EcKeyGenParams = {
    name: algorithm,
    namedCurve: curve,
  };

  const keyUsages: KeyUsage[] = isEcdh 
    ? ['deriveKey', 'deriveBits'] 
    : ['sign', 'verify'];

  // Generate native CryptoKeyPair
  const keyPair = await window.crypto.subtle.generateKey(
    keyGenAlgorithm,
    true, // extractable
    keyUsages
  );

  // Export Public Key as SPKI
  const spkiBuffer = await window.crypto.subtle.exportKey('spki', keyPair.publicKey);
  const publicKeyPem = toPem(spkiBuffer, 'PUBLIC KEY');

  // Export Private Key as PKCS#8
  const pkcs8Buffer = await window.crypto.subtle.exportKey('pkcs8', keyPair.privateKey);
  const privateKeyPem = toPem(pkcs8Buffer, 'PRIVATE KEY');

  // Export as JWK
  const publicKeyJwk = await window.crypto.subtle.exportKey('jwk', keyPair.publicKey);
  const privateKeyJwk = await window.crypto.subtle.exportKey('jwk', keyPair.privateKey);

  // Compute SHA-256 Fingerprint over SPKI public key
  const digestBuffer = await window.crypto.subtle.digest('SHA-256', spkiBuffer);
  const fingerprintSha256 = `SHA256:${toHexFingerprint(digestBuffer)}`;

  return {
    curve,
    algorithm,
    publicKeyPem,
    privateKeyPem,
    publicKeyJwk,
    privateKeyJwk,
    fingerprintSha256,
    generatedAt: new Date().toISOString()
  };
}

/**
 * Quick demonstration of local message encryption using ECDH derived AES-256-GCM
 */
export async function testLocalEccEncryption(message: string): Promise<{ ciphertextHex: string; decrypted: string }> {
  // Generate temporary recipient key pair
  const recipientKeys = await window.crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' },
    false,
    ['deriveKey']
  );

  // Generate ephemeral sender key pair
  const senderKeys = await window.crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' },
    false,
    ['deriveKey']
  );

  // Derive shared AES-GCM key
  const senderSharedKey = await window.crypto.subtle.deriveKey(
    { name: 'ECDH', public: recipientKeys.publicKey },
    senderKeys.privateKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt']
  );

  const recipientSharedKey = await window.crypto.subtle.deriveKey(
    { name: 'ECDH', public: senderKeys.publicKey },
    recipientKeys.privateKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['decrypt']
  );

  // Encrypt with AES-GCM
  const iv = window.crypto.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(message);
  const encrypted = await window.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    senderSharedKey,
    encoded
  );

  const ciphertextHex = Array.from(new Uint8Array(encrypted))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');

  // Decrypt
  const decryptedBuffer = await window.crypto.subtle.decrypt(
    { name: 'AES-GCM', iv },
    recipientSharedKey,
    encrypted
  );

  const decrypted = new TextDecoder().decode(decryptedBuffer);

  return { ciphertextHex, decrypted };
}
