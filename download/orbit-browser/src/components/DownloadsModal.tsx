import React from 'react';
import { Download, CheckCircle2, FileText, Trash2, X, HardDrive } from 'lucide-react';
import { DownloadItem } from '../types/browser';

interface DownloadsModalProps {
  isOpen: boolean;
  onClose: () => void;
  downloads: DownloadItem[];
  onClearDownloads: () => void;
  onStartSampleDownload: () => void;
}

export const DownloadsModal: React.FC<DownloadsModalProps> = ({
  isOpen,
  onClose,
  downloads,
  onClearDownloads,
  onStartSampleDownload
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div 
        className="w-full max-w-lg bg-[#0E1726] border border-white/10 rounded-3xl p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-150 text-slate-200 flex flex-col max-h-[85vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Download className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">Downloads Manager</h3>
              <p className="text-xs text-slate-400">Multi-threaded Parallel File Engine</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Quick action bar */}
        <div className="flex items-center justify-between py-3">
          <button
            onClick={onStartSampleDownload}
            className="px-3.5 py-1.5 text-xs font-semibold text-cyan-300 bg-cyan-950/70 hover:bg-cyan-900 border border-cyan-500/30 rounded-xl transition-colors cursor-pointer flex items-center gap-1.5"
          >
            <Download className="w-3.5 h-3.5 text-cyan-400" />
            <span>Test Download (Orbit-SDK.zip)</span>
          </button>

          {downloads.length > 0 && (
            <button
              onClick={onClearDownloads}
              className="text-xs text-slate-500 hover:text-rose-400 transition-colors cursor-pointer"
            >
              Clear list
            </button>
          )}
        </div>

        {/* Downloads List */}
        <div className="flex-1 overflow-y-auto space-y-2 pr-1 py-1">
          {downloads.length === 0 ? (
            <div className="py-16 text-center text-xs text-slate-500 space-y-2">
              <HardDrive className="w-8 h-8 mx-auto text-slate-600" />
              <p>No downloads yet.</p>
              <p className="text-[11px] text-slate-600">Files downloaded while browsing will be securely saved here.</p>
            </div>
          ) : (
            downloads.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between p-3.5 rounded-xl bg-white/5 border border-white/5 text-xs"
              >
                <div className="flex items-center gap-3 truncate flex-1">
                  <div className="w-8 h-8 rounded-lg bg-cyan-500/10 flex items-center justify-center text-cyan-400 shrink-0">
                    <FileText className="w-4 h-4" />
                  </div>
                  <div className="truncate">
                    <h4 className="font-semibold text-white truncate">{item.fileName}</h4>
                    <div className="flex items-center gap-2 text-[11px] text-slate-400 mt-0.5 font-mono">
                      <span>{item.fileSize}</span>
                      <span>·</span>
                      <span className="text-emerald-400">{item.status}</span>
                    </div>
                  </div>
                </div>

                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 ml-2" />
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
