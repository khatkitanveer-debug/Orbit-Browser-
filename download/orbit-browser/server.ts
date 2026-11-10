// Orbit Browser — minimal app server (AI Studio compatible)
// - Dev mode: Vite dev server (HMR) express ke through
// - Production: dist/ ki static files serve karta hai
// Ek hi port par sab kuch: process.env.PORT || 3000

import express, { Request, Response } from 'express';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import { createServer as createViteServer } from 'vite';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const port = Number(process.env.PORT) || 3000;
const isProd = process.env.NODE_ENV === 'production' || !!fs.existsSync(path.join(__dirname, 'dist'));

async function start() {
  if (!isProd) {
    // ---------- DEV: Vite middleware (HMR enabled) ----------
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    // ---------- PROD: static dist/ ----------
    const dist = path.join(__dirname, 'dist');
    app.use(express.static(dist));
    app.get('*', (_req: Request, res: Response) => {
      res.sendFile(path.join(dist, 'index.html'));
    });
  }

  app.listen(port, () => {
    console.log(`Orbit Browser chal raha hai: http://localhost:${port} (${isProd ? 'production' : 'dev'})`);
  });
}

start().catch((err) => {
  console.error('Server start fail:', err);
  process.exit(1);
});
