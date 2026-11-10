import React from 'react';

interface OrbitLogoProps {
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export const OrbitLogo: React.FC<OrbitLogoProps> = ({ size = 'lg', className = '' }) => {
  const dimensions = {
    sm: { box: 'w-10 h-10', planet: 'w-4 h-4', ring: 'w-8 h-8', text: 'text-base' },
    md: { box: 'w-16 h-16', planet: 'w-6 h-6', ring: 'w-14 h-14', text: 'text-xl' },
    lg: { box: 'w-24 h-24 sm:w-28 sm:h-28', planet: 'w-8 h-8 sm:w-10 sm:h-10', ring: 'w-20 h-20 sm:w-24 sm:h-24', text: 'text-3xl sm:text-4xl' }
  }[size];

  return (
    <div className={`flex flex-col items-center justify-center select-none ${className}`}>
      {/* Celestial Glowing Orbital Sphere */}
      <div className={`relative ${dimensions.box} flex items-center justify-center`}>
        {/* Outer ambient glow field */}
        <div className="absolute inset-0 rounded-full bg-cyan-500/15 blur-xl animate-orbit-glow" />

        {/* Outer Elliptical Orbital Ring */}
        <div className={`absolute ${dimensions.ring} rounded-full border border-cyan-400/30 transform -rotate-45 animate-orbit-spin-slow`} />
        
        {/* Inner Counter-Rotating Orbital Ring */}
        <div className={`absolute w-3/4 h-3/4 rounded-full border border-sky-300/40 transform rotate-12 animate-orbit-spin-reverse border-dashed`} />

        {/* Orbiting Satellite Particle */}
        <div className="absolute w-full h-full animate-orbit-spin-slow">
          <div className="w-2 h-2 rounded-full bg-cyan-300 shadow-[0_0_8px_#38bdf8] top-0 left-1/2 -translate-x-1/2 -translate-y-1/2 absolute" />
        </div>

        {/* Core Celestial Body */}
        <div className={`relative ${dimensions.planet} rounded-full bg-gradient-to-tr from-cyan-600 via-sky-400 to-white shadow-[0_0_24px_rgba(56,189,248,0.7)] flex items-center justify-center`}>
          <div className="w-1.5 h-1.5 rounded-full bg-white/90" />
        </div>
      </div>

      {/* Minimal Brand Typography */}
      {size === 'lg' && (
        <div className="mt-4 text-center">
          <h1 className={`${dimensions.text} font-bold tracking-tight text-white flex items-center justify-center gap-1.5`}>
            <span>Orbit</span>
            <span className="text-[10px] font-mono uppercase tracking-widest text-cyan-400 font-semibold px-1.5 py-0.5 rounded bg-cyan-500/10 border border-cyan-500/20">
              Chromium
            </span>
          </h1>
          <p className="text-xs text-slate-400 mt-1 font-mono tracking-wide">
            Ultra-Fast · High-Privacy · Zero Telemetry
          </p>
        </div>
      )}
    </div>
  );
};
