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

// --- Code-block chrome: language label + copy button ----------------------
(function () {
  "use strict";

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
    btn.addEventListener("click", function () {
      var code = block.querySelector("pre.highlight code, pre code, pre");
      var text = code ? code.innerText : "";
      var done = function () {
        btn.textContent = "Copied";
        btn.classList.add("sqkon-copied");
        setTimeout(function () {
          btn.textContent = "Copy";
          btn.classList.remove("sqkon-copied");
        }, 1500);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done);
      } else {
        var ta = document.createElement("textarea");
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand("copy"); done(); } catch (e) {}
        document.body.removeChild(ta);
      }
    });
    header.appendChild(btn);

    block.insertBefore(header, block.firstChild);
  }

  function init() {
    document.querySelectorAll("div.highlighter-rouge").forEach(addChrome);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();

// --- Right-hand "On this page" TOC -----------------------------------------
(function () {
  "use strict";

  function init() {
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
    headings.forEach(function (h) {
      var li = document.createElement("li");
      li.className = "sqkon-toc__" + h.tagName.toLowerCase();
      var a = document.createElement("a");
      a.href = "#" + h.id;
      a.textContent = h.textContent.trim();
      li.appendChild(a);
      list.appendChild(li);
      links[h.id] = a;
    });
    aside.appendChild(list);
    main.insertAdjacentElement("afterend", aside);
    document.documentElement.classList.add("sqkon-has-toc");

    // Scroll-spy: highlight the heading nearest the top of the viewport.
    var activeId = null;
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) { activeId = entry.target.id; }
      });
      Object.keys(links).forEach(function (id) {
        links[id].classList.toggle("sqkon-toc--active", id === activeId);
      });
    }, { rootMargin: "0px 0px -75% 0px" });
    headings.forEach(function (h) { observer.observe(h); });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
