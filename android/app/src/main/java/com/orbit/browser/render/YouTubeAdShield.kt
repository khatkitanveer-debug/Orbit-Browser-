package com.orbit.browser.render

/**
 * v1.3.4 — YouTube Ad Shield (document-start bootstrap #3).
 *
 * LEGALITY: ye normal CLIENT-SIDE ad blocking hai — wahi kaam jo Brave
 * browser default me karta hai aur Firefox+uBlock Origin users karte
 * hain (Germany ka supreme court AdBlock Plus ke case me ad-blocking ko
 * legal bata chuka hai; India me bhi koi rok nahi). Content ya DRM
 * touch nahi hota, koi video download/rip nahi hota — sirf apne browser
 * ke andar ad-delivery filter hoti hai. YouTube ki ToS ke against hai —
 * worst case unka "ad blocker detected" popup aa sakta hai; isliye
 * kill-switch hai: ota/hotfix.json me "ytShield": false → bina APK
 * update shield off (hot-patch channel).
 *
 * Techniques (sab uBlock/AdGuard ke open-source scripts jaise):
 *   1. CSS      — masthead/feed/search/display ad containers hide
 *   2. JSON     — ytInitialPlayerResponse setter trap: adPlacements/
 *                 adSlots/playerAds strip → player ad schedule hi nahi
 *                 banata, isliye video ads chalte hi nahi
 *   3. Network  — fetch + XHR hooks youtubei player API par — responses
 *                 ke JSON se ads strip (Kotlin block comments NEST hote
 *                 hain, isliye comment ke andar comment-opener likhna
 *                 mana hai — isliye yahan path bina star ke likha)
 *   4. DOM sweep — phir bhi ad slip ho jaye to skip button turant
 *                 auto-click (YouTube ka apna "Skip" button hi dabate
 *                 hain — koi stream modification nahi)
 *
 * Server-side ads (YouTube ka naya SSAP — ad stream ke andar bake)
 * client se nahi rukte — wo limitation sach hai. 90%+ cases me ye
 * kaafi hai.
 *
 * Sirf youtube/youtu.be hosts par active — baaki sites par pehli line
 * me return, zero cost.
 */
object YouTubeAdShield {
    val SCRIPT: String = """
        (function () {
          try {
            var h = location.hostname;
            if (!(h === 'youtube.com' || h === 'youtu.be' ||
                  h.endsWith('.youtube.com') || h.endsWith('.youtu.be') ||
                  h.endsWith('.youtube-nocookie.com'))) return;

            // ——— 1. CSS hide — sirf AD containers, content kabhi nahi ———
            var css = [
              '#masthead-ad',
              'ytd-ad-slot-renderer',
              'ytd-in-feed-ad-layout-renderer',
              'ytd-display-ad-renderer',
              'ytd-promoted-sparkles-web-renderer',
              'ytd-promoted-video-renderer',
              'ytd-search-pyv-renderer',
              'ytd-banner-promo-renderer',
              '#player-ads',
              'ytd-companion-slot-renderer',
              'ytd-player-legacy-desktop-watch-ads-renderer',
              '.ytp-ad-text-overlay',
              '.ytp-ad-overlay-container',
              '.ytp-ad-player-overlay'
            ].join(',') + '{display:none !important; height:0 !important;}';
            var st = document.createElement('style');
            st.textContent = css;
            (document.head || document.documentElement).appendChild(st);

            // ——— 2. JSON strip — player response se ad schedule nikalo ———
            var strip = function (o) {
              try {
                if (!o || typeof o !== 'object') return o;
                delete o.adPlacements;
                delete o.adSlots;
                delete o.playerAds;
                if (o.playerConfig) { delete o.playerConfig.adPlacements; }
              } catch (e) {}
              return o;
            };
            // YouTube ytInitialPlayerResponse baad me set karta hai — hamara
            // setter use jaate hi ads nikaal deta hai
            try {
              var ipr;
              Object.defineProperty(window, 'ytInitialPlayerResponse', {
                configurable: true,
                get: function () { return ipr; },
                set: function (v) { ipr = strip(v); }
              });
            } catch (e) {}

            // ——— 3. Network hooks — /youtubei/v1/* ke JSON se ads strip ———
            try {
              var of = window.fetch;
              window.fetch = function (input, init) {
                var url = typeof input === 'string' ? input : (input && input.url) || '';
                var p = of.call(this, input, init);
                if (url.indexOf('/youtubei/v1/') !== -1) {
                  p = p.then(function (res) {
                    try {
                      var cl = res.clone();
                      return cl.json().then(function (j) {
                        strip(j);
                        return new Response(JSON.stringify(j), {
                          status: res.status, statusText: res.statusText,
                          headers: { 'Content-Type': 'application/json' }
                        });
                      }).catch(function () { return res; });
                    } catch (e2) { return res; }
                  });
                }
                return p;
              };
            } catch (e) {}
            try {
              var XO = XMLHttpRequest.prototype.open;
              var XS = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function (m, u) {
                this.__oyUrl = u || '';
                return XO.apply(this, arguments);
              };
              XMLHttpRequest.prototype.send = function () {
                var xhr = this;
                var rt = xhr.responseType;
                if (xhr.__oyUrl && xhr.__oyUrl.indexOf('/youtubei/v1/') !== -1 &&
                    (rt === '' || rt === 'text')) {
                  xhr.addEventListener('readystatechange', function () {
                    try {
                      if (xhr.readyState === 4 && xhr.responseText) {
                        var j = JSON.parse(xhr.responseText);
                        strip(j);
                        var s = JSON.stringify(j);
                        Object.defineProperty(xhr, 'responseText', { value: s, configurable: true });
                        Object.defineProperty(xhr, 'response', { value: s, configurable: true });
                      }
                    } catch (e) {}
                  });
                }
                return XS.apply(this, arguments);
              };
            } catch (e) {}

            // ——— 4. DOM sweep — slip hua ad? Skip button turant dabao ———
            var pending = false;
            var sweep = function () {
              pending = false;
              try {
                var b = document.querySelectorAll(
                  '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                for (var i = 0; i < b.length; i++) b[i].click();
                var ov = document.querySelectorAll('.ytp-ad-text-overlay, .ytp-ad-overlay-container');
                for (var k = 0; k < ov.length; k++) ov[k].style.display = 'none';
              } catch (e) {}
            };
            var ask = function () {
              if (!pending) { pending = true; setTimeout(sweep, 120); }
            };
            try {
              new MutationObserver(ask).observe(document.documentElement,
                { childList: true, subtree: true });
            } catch (e) { setInterval(sweep, 500); }

            // ——— 5. v1.9.4 BLACK-SCREEN KILL — video ad aate hi TURANT khatam ———
            // Pehle sirf currentTime seek tha — kuch players par seekable na hone
            // se seek fail hota tha aur AD KA BLACK FRAME 15-30s tak dikh jata tha
            // (user report: "ad dikhti nahi, black screen jab tak ad chale").
            // Ab 4-layer kill + sound-restore + 120ms fast loop:
            //   a) seekable.end (buffered jo hai uske END par — duration se SAFE)
            //   b) player API seekTo (YouTube ka apna exposed API)
            //   c) skip button ko PointerEvent + MouseEvent dono se fire
            //      (kuch builds sirf synthetic pointer events ko maante hai)
            //   d) ad khatam hote hi ORIGINAL video unmute + normal rate + play
            //      (mute-state leak fix — "video mute ho gaya" bug)
            var adWasKilled = false;
            var fireEvents = function (el) {
              try {
                var opts = { bubbles: true, cancelable: true, view: window };
                try { el.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch (e1) {}
                try { el.dispatchEvent(new PointerEvent('pointerup', opts)); } catch (e2) {}
                try { el.dispatchEvent(new MouseEvent('mousedown', opts)); } catch (e3) {}
                try { el.dispatchEvent(new MouseEvent('mouseup', opts)); } catch (e4) {}
                try { el.dispatchEvent(new MouseEvent('click', opts)); } catch (e5) {}
              } catch (e) {}
            };
            var hardKill = function () {
              try {
                var pl = document.querySelector('.html5-video-player.ad-showing');
                if (pl) {
                  adWasKilled = true;
                  var p = pl.querySelector('video');
                  if (p) {
                    try { p.muted = true; } catch (e2) {}
                    try { p.playbackRate = 16; } catch (e2) {}
                    var end = 0;
                    try {
                      if (p.seekable && p.seekable.length) {
                        end = p.seekable.end(p.seekable.length - 1);
                      }
                    } catch (e4) {}
                    if (!end || !isFinite(end)) {
                      try { if (p.duration && isFinite(p.duration)) end = p.duration; } catch (e5) {}
                    }
                    if (end && isFinite(end) && end > 0) {
                      try { p.currentTime = Math.max(0, end - 0.05); } catch (e3) {}
                    }
                    try { if (p.paused) { p.play(); } } catch (e6) {}
                  }
                  try {
                    var mp = document.getElementById('movie_player');
                    if (mp && typeof mp.seekTo === 'function') {
                      mp.seekTo(typeof mp.getDuration === 'function' ? mp.getDuration() : 100000, true);
                    }
                  } catch (e7) {}
                }
                var sk = document.querySelectorAll(
                  '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, ' +
                  '.ytp-ad-skip-button-container button, .ytp-ad-skip-button-slot button');
                for (var i = 0; i < sk.length; i++) fireEvents(sk[i]);
                var oc = document.querySelector('.ytp-ad-overlay-close-button');
                if (oc) fireEvents(oc);
                // ad khatam → original video ki sound/rate RESTORE (mute-leak fix)
                if (adWasKilled) {
                  var orig = document.querySelector('.html5-video-player:not(.ad-showing) video');
                  if (orig) {
                    adWasKilled = false;
                    try { orig.muted = false; } catch (e8) {}
                    try { orig.playbackRate = 1; } catch (e9) {}
                    try { if (orig.paused) orig.play(); } catch (e10) {}
                  }
                }
              } catch (e) {}
            };
            setInterval(hardKill, 120);
          } catch (e) {}
        })();
    """.trimIndent()
}
