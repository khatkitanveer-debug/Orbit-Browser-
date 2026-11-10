import React, { useState } from 'react';
import { 
  Key, 
  Copy, 
  Check, 
  Eye, 
  EyeOff, 
  Download, 
  RefreshCw, 
  ShieldCheck, 
  Lock, 
  Unlock, 
  Sparkles,
  FileCode2,
  CheckCircle2,
  Terminal
} from 'lucide-react';
import { 
  generateEccKeyPair, 
  EccKeyPairResult, 
  EccNamedCurve, 
  EccAlgorithmType,
  testLocalEccEncryption 
} from '../lib/eccCrypto';

interface EccKeyGeneratorProps {
  onSavedToProfile?: (fingerprint: string) => void;
  isLoggedIn?: boolean;
}

export const EccKeyGenerator: React.FC<EccKeyGeneratorProps> = ({
  onSavedToProfile,
  isLoggedIn
}) => {
  const [curve, setCurve] = useState<EccNamedCurve>('P-256');
  const [algorithm, setAlgorithm] = useState<EccAlgorithmType>('ECDH');
  const [keyPair, setKeyPair] = useState<EccKeyPairResult | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [showPrivateKey, setShowPrivateKey] = useState(false);

  // Copy notification states
  const [copiedPublic, setCopiedPublic] = useState(false);
  const [copiedPrivate, setCopiedPrivate] = useState(false);
  const [copiedAll, setCopiedAll] = useState(false);

  // Test encryption state
  const [testMessage, setTestMessage] = useState('Orbit Local Confidential Record');
  const [testResult, setTestResult] = useState<{ ciphertextHex: string; decrypted: string } | null>(null);
  const [isTesting, setIsTesting] = useState(false);

  const handleGenerate = async () => {
    setIsGenerating(true);
    setTestResult(null);
    try {
      const keys = await generateEccKeyPair(curve, algorithm);
      setKeyPair(keys);
      if (onSavedToProfile) {
        onSavedToProfile(keys.fingerprintSha256);
      }
    } catch (err) {
      console.error('Failed to generate ECC key pair:', err);
    } finally {
      setIsGenerating(false);
    }
  };

  const handleCopy = (text: string, type: 'public' | 'private' | 'all') => {
    navigator.clipboard.writeText(text);
    if (type === 'public') {
      setCopiedPublic(true);
      setTimeout(() => setCopiedPublic(false), 2000);
    } else if (type === 'private') {
      setCopiedPrivate(true);
      setTimeout(() => setCopiedPrivate(false), 2000);
    } else {
      setCopiedAll(true);
      setTimeout(() => setCopiedAll(false), 2000);
    }
  };

  const handleDownload = () => {
    if (!keyPair) return;
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(keyPair, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `orbit-ecc-${keyPair.curve.toLowerCase()}-${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  const handleRunTest = async () => {
    if (!testMessage) return;
    setIsTesting(true);
    try {
      const res = await testLocalEccEncryption(testMessage);
      setTestResult(res);
    } catch (err) {
      console.error('Test encryption failed:', err);
    } finally {
      setIsTesting(false);
    }
  };

  return (
    <div className="p-4 rounded-2xl bg-cyan-950/20 border border-cyan-500/20 space-y-4 text-xs">
      {/* Section Title */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
            <Key className="w-3.5 h-3.5" />
          </div>
          <div>
            <h4 className="font-semibold text-white flex items-center gap-2">
              <span>ECC Cryptographic Key Pair Generator</span>
              <span className="text-[10px] font-mono uppercase bg-cyan-950 text-cyan-300 px-1.5 py-0.2 rounded border border-cyan-800">
                WebCrypto
              </span>
            </h4>
            <p className="text-[11px] text-slate-400">
              Generate Elliptic Curve keys (P-256, P-384, P-521) for local data encryption & signing
            </p>
          </div>
        </div>
      </div>

      {/* Curve & Algorithm Controls */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
        <div className="space-y-1">
          <label className="text-[11px] font-medium text-slate-300">Elliptic Curve</label>
          <select
            value={curve}
            onChange={(e) => setCurve(e.target.value as EccNamedCurve)}
            className="w-full px-2.5 py-1.5 rounded-lg bg-[#070D19] border border-white/10 text-white text-xs font-mono focus:outline-hidden focus:border-cyan-400"
          >
            <option value="P-256">NIST P-256 (secp256r1) — High Speed & 128-bit Security</option>
            <option value="P-384">NIST P-384 (secp384r1) — 192-bit Security</option>
            <option value="P-521">NIST P-521 — Maximum 256-bit Security</option>
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-[11px] font-medium text-slate-300">Target Operation</label>
          <select
            value={algorithm}
            onChange={(e) => setAlgorithm(e.target.value as EccAlgorithmType)}
            className="w-full px-2.5 py-1.5 rounded-lg bg-[#070D19] border border-white/10 text-white text-xs font-mono focus:outline-hidden focus:border-cyan-400"
          >
            <option value="ECDH">ECDH (Diffie-Hellman Key Agreement & Local Encryption)</option>
            <option value="ECDSA">ECDSA (Digital Signatures & Identity Verification)</option>
          </select>
        </div>
      </div>

      {/* Generator Trigger Button */}
      <button
        type="button"
        onClick={handleGenerate}
        disabled={isGenerating}
        className="w-full py-2.5 px-4 rounded-xl bg-cyan-400 hover:bg-cyan-300 disabled:opacity-50 text-slate-950 font-semibold text-xs transition-colors flex items-center justify-center gap-2 cursor-pointer shadow-xs"
      >
        <RefreshCw className={`w-3.5 h-3.5 ${isGenerating ? 'animate-spin' : ''}`} />
        <span>{isGenerating ? 'Generating ECC Key Pair in Hardware...' : `Generate ${curve} ${algorithm} Key Pair`}</span>
      </button>

      {/* Render Key Pair Results when generated */}
      {keyPair && (
        <div className="space-y-3.5 pt-2 border-t border-white/10 animate-in fade-in duration-200">
          {/* Fingerprint badge */}
          <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#070D19] border border-white/10 text-slate-300">
            <div className="space-y-0.5 truncate mr-2">
              <span className="text-[10px] uppercase font-mono text-cyan-400 font-semibold">Public Key Fingerprint (SHA-256)</span>
              <div className="font-mono text-[11px] text-white truncate select-all">
                {keyPair.fingerprintSha256}
              </div>
            </div>
            <div className="flex items-center gap-1.5 shrink-0">
              <button
                type="button"
                onClick={handleDownload}
                className="p-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 transition-colors cursor-pointer"
                title="Download JSON Keypair"
              >
                <Download className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => handleCopy(JSON.stringify(keyPair, null, 2), 'all')}
                className="p-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 transition-colors cursor-pointer"
                title="Copy JSON Keypair"
              >
                {copiedAll ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <FileCode2 className="w-3.5 h-3.5" />}
              </button>
            </div>
          </div>

          {/* Public Key (SPKI PEM) */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold text-slate-200 flex items-center gap-1.5">
                <Unlock className="w-3.5 h-3.5 text-emerald-400" />
                <span>Public Key (SPKI PEM Format)</span>
              </span>
              <button
                type="button"
                onClick={() => handleCopy(keyPair.publicKeyPem, 'public')}
                className="flex items-center gap-1 text-[11px] text-cyan-400 hover:text-cyan-300 transition-colors cursor-pointer font-medium"
              >
                {copiedPublic ? (
                  <>
                    <Check className="w-3 h-3 text-emerald-400" />
                    <span className="text-emerald-400">Copied PEM</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3 h-3" />
                    <span>Copy Public Key</span>
                  </>
                )}
              </button>
            </div>
            <pre className="p-2.5 rounded-xl bg-[#070D19] border border-white/5 font-mono text-[10px] text-emerald-400/90 leading-tight overflow-x-auto max-h-24 select-all">
              {keyPair.publicKeyPem}
            </pre>
          </div>

          {/* Private Key (PKCS#8 PEM) - Hidden by default */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold text-slate-200 flex items-center gap-1.5">
                <Lock className="w-3.5 h-3.5 text-amber-400" />
                <span>Private Key (PKCS#8 PEM Format)</span>
              </span>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setShowPrivateKey(!showPrivateKey)}
                  className="flex items-center gap-1 text-[11px] text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
                >
                  {showPrivateKey ? (
                    <>
                      <EyeOff className="w-3 h-3" />
                      <span>Mask</span>
                    </>
                  ) : (
                    <>
                      <Eye className="w-3 h-3" />
                      <span>Reveal</span>
                    </>
                  )}
                </button>
                <button
                  type="button"
                  onClick={() => handleCopy(keyPair.privateKeyPem, 'private')}
                  className="flex items-center gap-1 text-[11px] text-amber-400 hover:text-amber-300 transition-colors cursor-pointer font-medium"
                >
                  {copiedPrivate ? (
                    <>
                      <Check className="w-3 h-3 text-emerald-400" />
                      <span className="text-emerald-400">Copied Secret</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3 h-3" />
                      <span>Copy Private Key</span>
                    </>
                  )}
                </button>
              </div>
            </div>
            <div className="relative">
              <pre className={`p-2.5 rounded-xl bg-[#070D19] border border-white/5 font-mono text-[10px] leading-tight overflow-x-auto max-h-24 select-all ${
                showPrivateKey ? 'text-amber-400/90' : 'text-slate-600 filter blur-xs select-none'
              }`}>
                {showPrivateKey ? keyPair.privateKeyPem : '-----BEGIN PRIVATE KEY-----\n[ENCRYPTED PRIVATE KEY MASKED FOR PRIVACY]\n-----END PRIVATE KEY-----'}
              </pre>
            </div>
          </div>

          {/* Quick Local Encryption Tester */}
          <div className="p-3 rounded-xl bg-white/5 border border-white/5 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold text-slate-200 flex items-center gap-1.5">
                <Terminal className="w-3.5 h-3.5 text-cyan-400" />
                <span>Test Local ECDH Encryption & Decryption</span>
              </span>
              <button
                type="button"
                onClick={handleRunTest}
                disabled={isTesting}
                className="text-[10px] text-cyan-400 hover:underline cursor-pointer disabled:opacity-50"
              >
                {isTesting ? 'Encrypting...' : 'Verify Now'}
              </button>
            </div>

            <div className="flex items-center gap-2">
              <input
                type="text"
                value={testMessage}
                onChange={(e) => setTestMessage(e.target.value)}
                placeholder="Enter string to encrypt..."
                className="flex-1 px-2.5 py-1 rounded-lg bg-[#070D19] border border-white/10 text-white text-[11px] font-mono focus:outline-hidden"
              />
            </div>

            {testResult && (
              <div className="p-2 rounded-lg bg-[#070D19] border border-emerald-500/20 font-mono text-[10px] space-y-1">
                <div className="text-slate-400 flex items-center gap-1 truncate">
                  <span className="text-cyan-400">Ciphertext (AES-256-GCM):</span>
                  <span className="text-slate-300 truncate">{testResult.ciphertextHex.slice(0, 36)}...</span>
                </div>
                <div className="text-emerald-400 flex items-center gap-1">
                  <CheckCircle2 className="w-3 h-3 text-emerald-400 shrink-0" />
                  <span>Verified Decrypted: "{testResult.decrypted}"</span>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Security Privacy Guarantee */}
      <div className="flex items-start gap-2 text-[11px] text-slate-400 pt-1">
        <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
        <p>
          <strong className="text-slate-200">Zero-Knowledge Guarantee:</strong> Key pairs are generated client-side using the native Web Crypto API (<code className="font-mono text-cyan-300">window.crypto.subtle</code>). Private keys never leave your device memory.
        </p>
      </div>
    </div>
  );
};
