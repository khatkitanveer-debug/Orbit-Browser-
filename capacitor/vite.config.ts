import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Orbit shell — optimized for instant cold start on Android WebView:
// single chunk, no preloading tricks, ES2022 output (WebView 131+ baseline).
export default defineConfig({
  plugins: [react()],
  build: {
    target: "es2022",
    sourcemap: false,
    assetsInlineLimit: 8192,
    chunkSizeWarningLimit: 700,
  },
  server: { host: true, port: 5173 },
  preview: { host: true, port: 4173 },
});
