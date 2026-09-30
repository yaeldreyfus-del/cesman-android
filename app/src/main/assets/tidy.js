(function () {
  if (window.__cesmanTidy) return; window.__cesmanTidy = true;
  // 1. Styles : pas de défilement horizontal, pas de flash bleu au toucher, défilement fluide
  var css = document.createElement('style');
  css.textContent =
    'html,body{overflow-x:hidden!important;max-width:100vw!important;}' +
    '*{-webkit-tap-highlight-color:transparent!important;}' +
    'html{scroll-behavior:smooth;-webkit-text-size-adjust:100%;}' +
    '#WIX_ADS,[data-testid="WixAdsDesktopRoot"],[data-testid="WixAdsMobileRoot"]{display:none!important;}' +
    'img{content-visibility:auto;}';
  (document.head || document.documentElement).appendChild(css);

  // 2. Supprime les lignes vides des textes Wix (paragraphes ne contenant que des espaces)
  var EMPTY = /^[\s ​‌‍﻿]*$/;
  function tidy(root) {
    var nodes = (root || document).querySelectorAll(
      '[data-testid="richTextElement"] p, [data-testid="richTextElement"] h1, [data-testid="richTextElement"] h2,' +
      '[data-testid="richTextElement"] h3, [data-testid="richTextElement"] h4, [data-testid="richTextElement"] h5,' +
      '[data-testid="richTextElement"] h6, [data-testid="richTextElement"] li, .wixui-rich-text p');
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (n.dataset.cesmanChecked) continue;
      n.dataset.cesmanChecked = '1';
      if (EMPTY.test(n.textContent) && !n.querySelector('img,svg,iframe,video,input,button')) {
        n.style.setProperty('display', 'none', 'important');
      }
    }
  }
  tidy();
  var pending = false;
  new MutationObserver(function () {
    if (pending) return; pending = true;
    requestAnimationFrame(function () { pending = false; tidy(); });
  }).observe(document.documentElement, { childList: true, subtree: true });
})();
