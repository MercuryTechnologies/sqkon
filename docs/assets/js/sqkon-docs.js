// Sqkon docs enhancements. Every module is progressive enhancement —
// the site renders fully without this file.
(function () {
  "use strict";

  // Run fn once the DOM is ready (or immediately if it already is).
  function onReady(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn);
    } else {
      fn();
    }
  }

  // Copy text to the clipboard, calling onDone only on success. Tries the
  // async Clipboard API first (absent on insecure/http:// contexts, and can
  // reject — e.g. Firefox when the document isn't focused, or denied
  // permission), then falls back to a hidden-textarea execCommand.
  function copyText(text, onDone) {
    function fallback() {
      var ta = document.createElement("textarea");
      ta.value = text;
      ta.setAttribute("readonly", "");
      ta.style.position = "fixed";
      ta.style.top = "-9999px";
      document.body.appendChild(ta);
      ta.select();
      var ok = false;
      try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
      document.body.removeChild(ta);
      if (ok) onDone();
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(onDone, fallback);
    } else {
      fallback();
    }
  }

  // Wire a copy button: on click, copy `text` and briefly show `copiedLabel`.
  function wireCopyButton(btn, getText, copiedLabel) {
    var original = btn.textContent;
    btn.addEventListener("click", function () {
      copyText(getText(), function () {
        btn.textContent = copiedLabel;
        btn.classList.add("sqkon-copied");
        setTimeout(function () {
          btn.textContent = original;
          btn.classList.remove("sqkon-copied");
        }, 1500);
      });
    });
  }

  // --- Theme toggle -------------------------------------------------------
  // The pre-paint script in head_custom.html has already set
  // data-sqkon-theme and swapped the stylesheet if needed; this module
  // only handles subsequent user toggles.
  var LIGHT_CSS = "just-the-docs-default";
  var DARK_CSS = "just-the-docs-mercury-dark";

  function applyTheme(theme) {
    var link = document.querySelector('link[rel="stylesheet"][href*="' + LIGHT_CSS + '"], link[rel="stylesheet"][href*="' + DARK_CSS + '"]');
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

  // --- Code-block chrome: language label + copy button --------------------
  var LANG_LABELS = {
    kotlin: "Kotlin", bash: "Bash", sh: "Shell", shell: "Shell",
    yaml: "YAML", yml: "YAML", toml: "TOML", sql: "SQL", json: "JSON",
    groovy: "Groovy", xml: "XML", ruby: "Ruby", plaintext: "Text"
  };

  function labelFor(block) {
    var m = block.className.match(/language-([a-z0-9]+)/);
    if (!m) return null;
    if (m[1] === "mermaid") return undefined; // rendered as a diagram — skip
    return LANG_LABELS[m[1]] || (m[1].charAt(0).toUpperCase() + m[1].slice(1));
  }

  function addChrome(block) {
    if (block.querySelector(".sqkon-code-header")) return;
    if (block.closest(".sqkon-no-chrome")) return;
    var label = labelFor(block);
    if (label === undefined) return;

    var header = document.createElement("div");
    header.className = "sqkon-code-header";

    var lang = document.createElement("span");
    lang.className = "sqkon-code-lang";
    lang.textContent = label || "Code";
    header.appendChild(lang);

    var btn = document.createElement("button");
    btn.className = "sqkon-code-copy";
    btn.type = "button";
    btn.textContent = "Copy";
    btn.setAttribute("aria-label", "Copy code to clipboard");
    wireCopyButton(btn, function () {
      var code = block.querySelector("pre.highlight code, pre code, pre");
      return code ? code.innerText : "";
    }, "Copied");
    header.appendChild(btn);

    block.insertBefore(header, block.firstChild);
  }

  function initCodeChrome() {
    document.querySelectorAll("div.highlighter-rouge").forEach(addChrome);
  }

  // --- Right-hand "On this page" TOC --------------------------------------
  function initToc() {
    var main = document.querySelector("#main-content > main");
    if (!main || document.querySelector(".hero")) return; // no TOC on the landing page

    var headings = Array.prototype.filter.call(
      main.querySelectorAll("h2[id], h3[id]"),
      function (h) { return h.id && h.id !== "table-of-contents"; }
    );
    if (headings.length < 2) return;

    var aside = document.createElement("aside");
    aside.className = "sqkon-toc";
    aside.setAttribute("aria-label", "On this page");

    var title = document.createElement("p");
    title.className = "sqkon-toc__title";
    title.textContent = "On this page";
    aside.appendChild(title);

    var list = document.createElement("ul");
    var links = {};
    var order = [];
    headings.forEach(function (h) {
      var li = document.createElement("li");
      li.className = "sqkon-toc__" + h.tagName.toLowerCase();
      var a = document.createElement("a");
      a.href = "#" + h.id;
      a.textContent = h.textContent.trim();
      li.appendChild(a);
      list.appendChild(li);
      links[h.id] = a;
      order.push(h.id);
    });
    aside.appendChild(list);
    main.insertAdjacentElement("afterend", aside);
    document.documentElement.classList.add("sqkon-has-toc");

    // Scroll-spy: highlight the topmost heading currently within the top band
    // (document order), and clear the highlight once none remain — e.g. after
    // scrolling past the last heading.
    var intersecting = {};
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        intersecting[entry.target.id] = entry.isIntersecting;
      });
      var activeId = null;
      for (var i = 0; i < order.length; i++) {
        if (intersecting[order[i]]) { activeId = order[i]; break; }
      }
      order.forEach(function (id) {
        links[id].classList.toggle("sqkon-toc--active", id === activeId);
      });
    }, { rootMargin: "0px 0px -75% 0px" });
    headings.forEach(function (h) { observer.observe(h); });
  }

  // --- Maven-coordinate copy chip (landing hero) --------------------------
  function initCoordChips() {
    document.querySelectorAll("button.sqkon-coord[data-copy]").forEach(function (btn) {
      wireCopyButton(btn, function () { return btn.getAttribute("data-copy"); }, "Copied ✓");
    });
  }

  onReady(function () {
    initThemeToggle();
    initCodeChrome();
    initToc();
    initCoordChips();
  });
})();
