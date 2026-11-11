import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./styles/tokens.css";
import "./styles/app.css";
import "./styles/home.css";

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);

// Dev-only QA handle: seed/clear history from the console while testing the
// Module 2 feed in the web preview. Never ships — dead-code-eliminated by
// the DEV guard in production builds.
if (import.meta.env.DEV) {
  void Promise.all([import("./core/historyDB"), import("./core/bookmarksDB")]).then(([h, b]) => {
    (window as unknown as Record<string, unknown>).__orbitDev = {
      recordVisit: h.recordVisit,
      clearHistory: h.clearHistory,
      addBookmark: b.addBookmark,
    };
  });
}
