package com.orbit.browser.render

/**
 * Document-start bootstrap for every content WebView.
 *
 * Chrome-parity guarantee: if a site ships no viewport meta (lazy desktop
 * pages, legacy portals), we inject one BEFORE first layout so the page fits
 * the screen via the standard Chrome path (WideViewPort + OverviewMode).
 * Runs before any page script, zero flicker, zero reflow.
 */
object ViewportBootstrap {
    val SCRIPT: String = """
        (function () {
          try {
            if (!document.querySelector('meta[name="viewport"]')) {
              var m = document.createElement('meta');
              m.setAttribute('name', 'viewport');
              m.setAttribute('content',
                'width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5, viewport-fit=cover');
              (document.head || document.documentElement).appendChild(m);
            }
          } catch (e) {}
        })();
    """.trimIndent()

    /** Runs on every origin — same coverage as Chrome's renderer defaults. */
    val ALLOWED_ORIGIN_RULES: Set<String> = setOf("*")
}
