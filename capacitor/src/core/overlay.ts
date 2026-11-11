/**
 * Overlay accounting (Module 2).
 *
 * The hardware back button must close sheets/dialogs BEFORE the browser
 * handles back (content back → home → exit). Both Capacitor backButton
 * listeners fire regardless of order, so the browser checks this counter:
 * while it is > 0, the browser back-path is a no-op and the overlay's own
 * listener does the closing.
 */

let openOverlays = 0;

export function overlayPush(): () => void {
  openOverlays++;
  return () => {
    openOverlays = Math.max(0, openOverlays - 1);
  };
}

export function isOverlayOpen(): boolean {
  return openOverlays > 0;
}
