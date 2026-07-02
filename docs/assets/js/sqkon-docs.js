// Sqkon docs enhancements. Every module is progressive enhancement —
// the site renders fully without this file.
(function () {
  "use strict";

  // --- Theme toggle -------------------------------------------------------
  // The pre-paint script in head_custom.html has already set
  // data-sqkon-theme and swapped the stylesheet if needed; this module
  // only handles subsequent user toggles.
  var LIGHT_CSS = "just-the-docs-default";
  var DARK_CSS = "just-the-docs-mercury-dark";

  function themeLink() {
    return document.querySelector('link[rel="stylesheet"][href*="' + LIGHT_CSS + '"], link[rel="stylesheet"][href*="' + DARK_CSS + '"]');
  }

  function applyTheme(theme) {
    var link = themeLink();
    if (!link) return;
    link.href = theme === "dark"
      ? link.href.replace(LIGHT_CSS, DARK_CSS)
      : link.href.replace(DARK_CSS, LIGHT_CSS);
    document.documentElement.setAttribute("data-sqkon-theme", theme);
    try { localStorage.setItem("sqkon-theme", theme); } catch (e) { /* private mode */ }
  }

  function initThemeToggle() {
    var btn = document.getElementById("sqkon-theme-toggle");
    if (!btn) return;
    btn.addEventListener("click", function () {
      var current = document.documentElement.getAttribute("data-sqkon-theme") === "dark" ? "dark" : "light";
      applyTheme(current === "dark" ? "light" : "dark");
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initThemeToggle);
  } else {
    initThemeToggle();
  }
})();
