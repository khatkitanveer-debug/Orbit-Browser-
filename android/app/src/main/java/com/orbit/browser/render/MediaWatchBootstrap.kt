package com.orbit.browser.render

/**
 * Document-start bootstrap #2 (Module 3) — background playback enabler.
 *
 * Two jobs, both JioSphere-parity:
 *
 *  1. VISIBILITY LOCK — sites call pause() the moment they see
 *     document.hidden=true (lock screen / app switch). We pin the Page
 *     Visibility API to "visible" and swallow visibilitychange so media
 *     keeps decoding in the background.
 *
 *  2. MEDIA WATCH — page play/pause/ended events are reported to the
 *     native `OrbitMediaSink` JS interface, which owns the foreground
 *     service lifecycle. Event-driven, zero polling.
 *
 * Paired with PlaybackService (FGS mediaPlayback) this is what keeps
 * YouTube / JioSaavn audible on the lock screen.
 */
object MediaWatchBootstrap {
    val SCRIPT: String = """
        (function () {
          try {
            // ——— 1. Page-visibility lock ———
            try {
              Object.defineProperty(document, 'hidden', {
                get: function () { return false; }, configurable: true
              });
              Object.defineProperty(document, 'visibilityState', {
                get: function () { return 'visible'; }, configurable: true
              });
              Object.defineProperty(document, 'webkitHidden', {
                get: function () { return false; }, configurable: true
              });
              Object.defineProperty(document, 'webkitVisibilityState', {
                get: function () { return 'visible'; }, configurable: true
              });
            } catch (e) {}
            try {
              var swallow = function (e) { e.stopImmediatePropagation(); };
              document.addEventListener('visibilitychange', swallow, true);
              document.addEventListener('webkitvisibilitychange', swallow, true);
              // Page Lifecycle freeze — Chromium hidden page freeze karta hai,
              // media wale pages par ye event kabhi act nahi hone dena
              document.addEventListener('freeze', swallow, true);
            } catch (e) {}

            // ——— 2. Media watch → native sink ———
            var isMedia = function (t) {
              return !!t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO');
            };
            document.addEventListener('play', function (e) {
              if (!isMedia(e.target)) return;
              try {
                var s = window.OrbitMediaSink;
                if (s) s.onPlaybackStarted(document.title || location.hostname, location.href);
              } catch (err) {}
            }, true);
            var reportPause = function (e) {
              if (!isMedia(e.target)) return;
              try {
                var s = window.OrbitMediaSink;
                if (s) s.onPlaybackPaused();
              } catch (err) {}
            };
            document.addEventListener('pause', reportPause, true);
            document.addEventListener('ended', reportPause, true);
          } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Native → page control (notification buttons, headset, media card).
     * Plays every paused media element or pauses every playing one —
     * exactly what a lockscreen play/pause means for a web page.
     */
    fun controlScript(play: Boolean): String {
        val playLiteral = if (play) "true" else "false"
        return """
            (function () {
              try {
                var list = document.querySelectorAll('video, audio');
                for (var i = 0; i < list.length; i++) {
                  var m = list[i];
                  if ($playLiteral) {
                    if (m.paused && m.play) {
                      var p = m.play();
                      if (p && p.catch) p.catch(function () {});
                    }
                  } else {
                    if (!m.paused) m.pause();
                  }
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }
}
