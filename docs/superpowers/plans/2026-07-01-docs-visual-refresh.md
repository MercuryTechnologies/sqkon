# Sqkon Docs Visual Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reskin the Jekyll docs site to the approved design — split hero landing, dark code blocks with copy buttons, right-hand "On this page" TOC, and a light/dark theme toggle — without forking the Just-the-Docs theme or touching content.

**Architecture:** Everything rides on Just-the-Docs 0.12's supported extension points: two color-scheme SCSS files (light `mercury`, new `mercury-dark`), one component shadow (`components/sidebar.html`, +1 button), `head_custom.html` for fonts and a no-FOUC theme script, one vanilla-JS file for all enhancement (toggle, code chrome, TOC), and a rewritten `docs/index.md` hero. `scripts/validate-docs.rb` is the test harness — each task adds a failing assertion first, then makes it pass.

**Tech Stack:** Jekyll 4.4, Just-the-Docs 0.12.0 (gem, not forked), SCSS, vanilla JS, Ruby/Nokogiri validator.

**Spec:** `docs/superpowers/specs/2026-07-01-docs-visual-refresh-design.md`

## Global Constraints

- Touch ONLY files under `docs/` plus `scripts/validate-docs.rb`. No library/Gradle changes.
- Do NOT edit any content markdown except `docs/index.md`. All 33 pages must render unmodified.
- Fonts: self-hosted, open-licensed only (Inter = OFL, IBM Plex Mono = OFL). NEVER bundle or hotlink Arcadia/Tiempos (Mercury's proprietary fonts). Total font payload ≤ 180 KB.
- All JS is progressive enhancement: with JS disabled the site must render fully readable (no TOC, no copy buttons, theme = light).
- Keep the JTD gem at `~> 0.12` — no fork, no vendoring. Shadowed files get a comment naming the gem version and the divergence.
- Commit prefix `docs:` (no release-please version bump — correct for docs-only work).
- Light palette: canvas `#ffffff`, sidebar `#fafaf8`, text `#272735`, secondary `#535461`, accent `#5266eb` (hover `#4354c8`), inline-code bg `#f4f2ee`, soft border `#e7e6e0`.
- Dark palette: base `#232329`, panels/sidebar `#28282f`, text `#f5f4f0`, secondary `#a3a3ab`, muted `#6e6e78`, accent `#8a93f5`, code bg `#1b1b20`, borders `rgba(255,255,255,0.08)`.
- Code blocks are DARK in BOTH schemes: block bg `#272735` (light scheme) / `#1b1b20` (dark scheme), keywords `#8a93f5`, strings `#a7d78a`, comments `#7f849c`, base text `#e7e6e0`.

## The test loop (used by every task)

```bash
cd docs
bundle install            # once, first task only
bundle exec jekyll build  # must exit 0
bundle exec ruby ../scripts/validate-docs.rb _site
```

If `bundle install` fails on Ruby version, the CI uses Ruby 3.3 — install via `rbenv`/`mise` if needed. The local build does NOT include the Dokka `/api` output; that's CI-only and irrelevant to these tasks.

For visual checks: `bundle exec jekyll serve` then open `http://127.0.0.1:4000/sqkon/`.

---

### Task 1: Self-hosted fonts (Inter + IBM Plex Mono)

**Files:**
- Create: `docs/assets/fonts/inter-var-latin.woff2`, `docs/assets/fonts/ibm-plex-mono-400-latin.woff2`, `docs/assets/fonts/ibm-plex-mono-700-latin.woff2`
- Modify: `docs/_includes/head_custom.html` (add preloads)
- Modify: `docs/_sass/custom/custom.scss` (add `@font-face`, use vars)
- Modify: `docs/_sass/color_schemes/mercury.scss` (set JTD font variables)
- Test: `scripts/validate-docs.rb`

**Interfaces:**
- Produces: font families `"Inter"` (weight 100–900 variable) and `"IBM Plex Mono"` (400, 700) available site-wide; JTD vars `$body-font-family` / `$mono-font-family` set in the `mercury` scheme. Task 3 must repeat the two font vars in `mercury-dark.scss`.

- [ ] **Step 1: Write the failing validator assertion**

Append to `scripts/validate-docs.rb`, immediately BEFORE the `if errors.empty?` block:

```ruby
# --- Visual refresh: self-hosted fonts ---
preloads = doc.css('link[rel="preload"][as="font"]')
errors << "expected 2 font preloads, found #{preloads.size}" unless preloads.size == 2
%w[inter-var-latin.woff2 ibm-plex-mono-400-latin.woff2].each do |f|
  path = File.join(site, "assets", "fonts", f)
  errors << "missing font file #{f}" unless File.exist?(path)
end
font_bytes = Dir[File.join(site, "assets", "fonts", "*.woff2")].sum { |f| File.size(f) }
errors << "font payload #{font_bytes / 1024}KB exceeds 180KB budget" if font_bytes > 180 * 1024
```

- [ ] **Step 2: Run the test loop — expect FAIL**

Run: `cd docs && bundle install && bundle exec jekyll build && bundle exec ruby ../scripts/validate-docs.rb _site`
Expected: FAILED with "expected 2 font preloads, found 0" and "missing font file …"

- [ ] **Step 3: Download the latin-subset woff2 files from Google Fonts**

Google serves pre-subsetted OFL woff2 (Inter var latin ≈ 48 KB; Plex Mono ≈ 15 KB each). Run from the repo root:

```bash
mkdir -p docs/assets/fonts
UA="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

fetch_latin() { # $1=css2 query  $2=output file
  url=$(curl -s -A "$UA" "https://fonts.googleapis.com/css2?$1&display=swap" \
    | awk '/\/\* latin \*\//{f=1} f && match($0, /https:[^)]+\.woff2/) {print substr($0, RSTART, RLENGTH); exit}')
  [ -n "$url" ] && curl -s -o "docs/assets/fonts/$2" "$url" && ls -la "docs/assets/fonts/$2"
}

fetch_latin "family=Inter:wght@100..900"      "inter-var-latin.woff2"
fetch_latin "family=IBM+Plex+Mono:wght@400"   "ibm-plex-mono-400-latin.woff2"
fetch_latin "family=IBM+Plex+Mono:wght@700"   "ibm-plex-mono-700-latin.woff2"
file docs/assets/fonts/*.woff2   # each must report "Web Open Font Format (Version 2)"
```

Fallback if the scrape returns nothing (Google markup change): download `Inter-4.1.zip` from `https://github.com/rsms/inter/releases/tag/v4.1`, use `web/InterVariable.woff2` renamed to `inter-var-latin.woff2` (larger, ~340 KB — then relax the budget assertion to 420 KB and note it in the commit); Plex Mono from `https://github.com/IBM/plex/releases` (`IBM-Plex-Mono/fonts/complete/woff2/IBMPlexMono-Regular.woff2`, `-Bold.woff2`).

- [ ] **Step 4: Add `@font-face` and typography plumbing**

In `docs/_sass/custom/custom.scss`, add at the very top (before the `$mercury-*` `!default` block):

```scss
// Self-hosted fonts (OFL). Paths resolve relative to the compiled
// /assets/css/*.css, so ../fonts/ == /assets/fonts/.
@font-face {
  font-family: "Inter";
  src: url("../fonts/inter-var-latin.woff2") format("woff2");
  font-weight: 100 900;
  font-style: normal;
  font-display: swap;
}

@font-face {
  font-family: "IBM Plex Mono";
  src: url("../fonts/ibm-plex-mono-400-latin.woff2") format("woff2");
  font-weight: 400;
  font-style: normal;
  font-display: swap;
}

@font-face {
  font-family: "IBM Plex Mono";
  src: url("../fonts/ibm-plex-mono-700-latin.woff2") format("woff2");
  font-weight: 700;
  font-style: normal;
  font-display: swap;
}
```

In the SAME file, replace the existing `body { font-family: ... }` rule so it no longer hardcodes a stack (JTD's `$body-font-family` now carries it):

```scss
body {
  letter-spacing: -0.005em;
}
```

In `docs/_sass/color_schemes/mercury.scss`, add at the end (these are JTD support variables — setting them in the scheme file is JTD's documented override point):

```scss
// --- Typography (self-hosted; see custom.scss @font-face) ---
$body-font-family: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
$mono-font-family: "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
```

- [ ] **Step 5: Add preloads to `docs/_includes/head_custom.html`**

Append at the end of the file:

```html
<!-- Self-hosted fonts: preload the two that style above-the-fold text -->
<link rel="preload" href="{{ '/assets/fonts/inter-var-latin.woff2' | relative_url }}" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="{{ '/assets/fonts/ibm-plex-mono-400-latin.woff2' | relative_url }}" as="font" type="font/woff2" crossorigin>
```

- [ ] **Step 6: Run the test loop — expect PASS**

Run: `cd docs && bundle exec jekyll build && bundle exec ruby ../scripts/validate-docs.rb _site`
Expected: `docs smoke check OK`. Also confirm the compiled CSS picked up the font: `grep -c "Inter" docs/_site/assets/css/just-the-docs-default.css` ≥ 2.

- [ ] **Step 7: Commit**

```bash
git add docs/assets/fonts docs/_includes/head_custom.html docs/_sass scripts/validate-docs.rb
git commit -m "docs: self-host Inter and IBM Plex Mono for the docs site"
```

---

### Task 2: Light-scheme refresh (near-white sidebar, new tokens)

**Files:**
- Modify: `docs/_sass/color_schemes/mercury.scss`
- Modify: `docs/_sass/custom/custom.scss` (token defaults)

**Interfaces:**
- Produces: new tokens `$mercury-bg-sidebar` and `$mercury-text-muted` available to `custom.scss` (with `!default` fallbacks there). Tasks 3–8 consume `$mercury-*` tokens exactly as named here.

- [ ] **Step 1: Update `docs/_sass/color_schemes/mercury.scss`**

Add the two new tokens to the palette block (after `$mercury-code-bg`):

```scss
$mercury-bg-sidebar:   #fafaf8;  // near-white sidebar (replaces cream sidebar)
$mercury-text-muted:   #9a9aad;  // eyebrow labels, TOC headings, breadcrumbs
```

Change the sidebar mapping in the JTD overrides section:

```scss
$sidebar-color:                $mercury-bg-sidebar;
```

(was `$mercury-bg-alt`). Everything else in the overrides block stays.

- [ ] **Step 2: Add matching `!default` fallbacks in `docs/_sass/custom/custom.scss`**

The existing `!default` block (see the comment in that file explaining why it exists) gets two new lines:

```scss
$mercury-bg-sidebar:   #fafaf8 !default;
$mercury-text-muted:   #9a9aad !default;
$mercury-code-bg:      #f4f2ee !default;
```

(`$mercury-code-bg` is consumed by later tasks; add its fallback now.)

- [ ] **Step 3: Run the test loop — expect PASS, verify visually**

Run the test loop. Then `bundle exec jekyll serve` and confirm at `http://127.0.0.1:4000/sqkon/guides/querying/`: sidebar is near-white (`#fafaf8`), not cream; body text renders in Inter (check devtools computed `font-family`).

- [ ] **Step 4: Commit**

```bash
git add docs/_sass
git commit -m "docs: refresh light scheme with near-white sidebar and new tokens"
```

---

### Task 3: Dark scheme (`mercury-dark`) stylesheet emission

**Files:**
- Create: `docs/_sass/color_schemes/mercury-dark.scss`
- Create: `docs/assets/css/just-the-docs-mercury-dark.scss`

**Interfaces:**
- Produces: built site emits `/assets/css/just-the-docs-mercury-dark.css`. Task 4's toggle swaps the `<link>` href between `just-the-docs-default.css` (light) and `just-the-docs-mercury-dark.css`. The scheme redefines every `$mercury-*` token, so ALL component CSS in `custom.scss` adapts automatically — later tasks style components once, in tokens.

- [ ] **Step 1: Write the failing check**

```bash
cd docs && bundle exec jekyll build && test -f _site/assets/css/just-the-docs-mercury-dark.css && echo EXISTS || echo MISSING
```

Expected: `MISSING`

- [ ] **Step 2: Create `docs/_sass/color_schemes/mercury-dark.scss`**

```scss
// Mercury dark scheme — "D2 warm charcoal" from the design spec.
// The dark cousin of the cream: Mercury's charcoal family as surfaces,
// brand blue lightened to keep AA contrast on dark.

// --- Palette (mirrors every token mercury.scss defines) ---
$mercury-bg:           #232329;  // base surface
$mercury-bg-alt:       #28282f;  // panels
$mercury-bg-sidebar:   #28282f;  // sidebar panel
$mercury-text:         #f5f4f0;  // primary text
$mercury-text-mid:     #a3a3ab;  // secondary text
$mercury-text-muted:   #6e6e78;  // eyebrows, TOC headings, breadcrumbs
$mercury-accent:       #8a93f5;  // lightened brand blue (AA on #232329)
$mercury-accent-2:     #98a0f6;  // hover
$mercury-border:       rgba(255, 255, 255, 0.09);
$mercury-border-soft:  rgba(255, 255, 255, 0.08);
$mercury-code-bg:      #1b1b20;  // code blocks + inline code

// --- Just the Docs variable overrides ---
$color-scheme:                 dark; // JTD hint: affects e.g. search icon rendering
$body-background-color:        $mercury-bg;
$sidebar-color:                $mercury-bg-sidebar;
$body-text-color:              $mercury-text;
$body-heading-color:           $mercury-text;
$nav-child-link-color:         $mercury-text-mid;
$link-color:                   $mercury-accent;
$btn-primary-color:            #5266eb; // buttons keep the full-strength brand blue
$base-button-color:            $mercury-bg-alt;
$code-background-color:        $mercury-code-bg;
$feedback-color:               lighten($mercury-bg, 3%);
$table-background-color:       $mercury-bg-alt;
$search-background-color:      $mercury-bg-alt;
$search-result-preview-color:  $mercury-text-mid;
$border-color:                 $mercury-border;

// --- Typography (must match mercury.scss) ---
$body-font-family: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
$mono-font-family: "IBM Plex Mono", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
```

- [ ] **Step 3: Create `docs/assets/css/just-the-docs-mercury-dark.scss`**

This is JTD's documented pattern for emitting an alternate scheme stylesheet (mirrors the gem's `assets/css/just-the-docs-default.scss`):

```scss
---
---
{% include css/just-the-docs.scss.liquid color_scheme="mercury-dark" %}
```

(The two `---` lines are required Jekyll front matter — do not remove them.)

- [ ] **Step 4: Run the check — expect PASS**

```bash
cd docs && bundle exec jekyll build && test -f _site/assets/css/just-the-docs-mercury-dark.css && echo EXISTS
grep -c "#232329" _site/assets/css/just-the-docs-mercury-dark.css   # expect ≥ 1
bundle exec ruby ../scripts/validate-docs.rb _site                   # still OK
```

- [ ] **Step 5: Commit**

```bash
git add docs/_sass/color_schemes/mercury-dark.scss docs/assets/css/just-the-docs-mercury-dark.scss
git commit -m "docs: add mercury-dark color scheme stylesheet"
```

---

### Task 4: Theme toggle + no-FOUC persistence

**Files:**
- Create: `docs/_includes/components/sidebar.html` (shadow of the gem file, +1 button)
- Create: `docs/assets/js/sqkon-docs.js`
- Modify: `docs/_includes/head_custom.html` (no-FOUC script + defer script tag)
- Modify: `docs/_sass/custom/custom.scss` (toggle button styling)
- Test: `scripts/validate-docs.rb`

**Interfaces:**
- Consumes: `just-the-docs-mercury-dark.css` from Task 3.
- Produces: `<html data-sqkon-theme="light|dark">` attribute, set pre-paint — later CSS may key off it; `window.sqkonSetTheme(theme)` not exposed (toggle is self-contained). `docs/assets/js/sqkon-docs.js` exists — Tasks 5, 7, 8 append modules to this file.

- [ ] **Step 1: Write the failing validator assertion**

Append to `scripts/validate-docs.rb` before the `if errors.empty?` block:

```ruby
# --- Visual refresh: theme toggle ---
errors << "missing #sqkon-theme-toggle button" unless doc.at_css("button#sqkon-theme-toggle")
errors << "missing sqkon-docs.js script tag" unless doc.at_css('script[src*="sqkon-docs.js"]')
```

Run the test loop. Expected: FAILED with both messages.

- [ ] **Step 2: Shadow the sidebar component**

Copy the gem's sidebar include, then add ONE button. Run:

```bash
cp "$(cd docs && bundle show just-the-docs)/_includes/components/sidebar.html" docs/_includes/components/sidebar.html
```

Then edit `docs/_includes/components/sidebar.html`: at the very top add

```html
{%- comment -%}
  SHADOWED from just-the-docs 0.12.0 — sole divergence: the
  #sqkon-theme-toggle button added to .site-header. Diff against the gem
  copy when upgrading the theme.
{%- endcomment -%}
```

and inside `<div class="site-header">`, directly BEFORE the existing `<button id="menu-button" ...>` line, insert:

```html
    <button id="sqkon-theme-toggle" class="site-button btn-reset" aria-label="Switch between light and dark theme">
      <svg class="sqkon-icon-sun" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4m11.4-11.4 1.4-1.4"/></svg>
      <svg class="sqkon-icon-moon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>
    </button>
```

- [ ] **Step 3: Create `docs/assets/js/sqkon-docs.js` with the theme module**

```javascript
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
```

- [ ] **Step 4: Add the no-FOUC script and script tag to `docs/_includes/head_custom.html`**

Append at the end (order matters — `head_custom.html` renders AFTER the theme's stylesheet `<link>`, so this blocking script swaps the href before `<body>` paints):

```html
<!-- Theme restore: runs before first paint; no flash of wrong theme -->
<script>
  (function () {
    var theme;
    try { theme = localStorage.getItem("sqkon-theme"); } catch (e) {}
    if (!theme) {
      theme = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    }
    if (theme === "dark") {
      var link = document.querySelector('link[rel="stylesheet"][href*="just-the-docs-default"]');
      if (link) { link.href = link.href.replace("just-the-docs-default", "just-the-docs-mercury-dark"); }
    }
    document.documentElement.setAttribute("data-sqkon-theme", theme);
  })();
</script>

<script src="{{ '/assets/js/sqkon-docs.js' | relative_url }}" defer></script>
```

- [ ] **Step 5: Style the toggle in `docs/_sass/custom/custom.scss`**

Append:

```scss
// Theme toggle (sun shown in light mode, moon in dark)
#sqkon-theme-toggle {
  display: inline-flex;
  align-items: center;
  padding: 0.375rem;
  margin-right: 0.25rem;
  color: $mercury-text-mid;
  cursor: pointer;
  border-radius: 6px;

  &:hover { color: $mercury-accent; background: rgba($mercury-accent, 0.08); }
  &:focus-visible { outline: 2px solid $mercury-accent; outline-offset: 2px; }
}

html[data-sqkon-theme="dark"] #sqkon-theme-toggle .sqkon-icon-sun { display: none; }
html:not([data-sqkon-theme="dark"]) #sqkon-theme-toggle .sqkon-icon-moon { display: none; }
```

- [ ] **Step 6: Run the test loop — expect PASS, then manual browser check**

Test loop passes. Then `bundle exec jekyll serve`, open the site and verify: (a) toggle button visible in the sidebar header next to the menu button; (b) clicking flips the whole site to the dark scheme and back; (c) reload keeps the chosen theme with NO light-flash; (d) toggle works via keyboard (Tab to it, Enter); (e) in a private window with dark OS preference, the site loads dark.

- [ ] **Step 7: Commit**

```bash
git add docs/_includes docs/assets/js docs/_sass scripts/validate-docs.rb
git commit -m "docs: add light/dark theme toggle with no-FOUC persistence"
```

---

### Task 5: Code-block chrome (dark cards, language label, copy button)

**Files:**
- Modify: `docs/_sass/custom/custom.scss`
- Modify: `docs/assets/js/sqkon-docs.js` (append module)

**Interfaces:**
- Consumes: `$mercury-*` tokens; `sqkon-docs.js` from Task 4.
- Produces: DOM shape `div.highlighter-rouge > .sqkon-code-header (span.sqkon-code-lang + button.sqkon-code-copy) + div.highlight`. Task 8's hero opts OUT by wrapping its block in `.sqkon-no-chrome`.

- [ ] **Step 1: Add the code-card SCSS**

Append to `docs/_sass/custom/custom.scss`. The syntax palette is scheme-agnostic (code is dark in both schemes), so the ONLY scheme-dependent value is the card background:

```scss
// --- Code blocks: dark cards in both schemes ------------------------------
// Light scheme: charcoal card on white page. Dark scheme: near-black card.
$sqkon-code-block-bg: if(lightness($mercury-bg) > 50%, #272735, $mercury-code-bg);

div.highlighter-rouge {
  margin-bottom: 1rem;
  overflow: hidden;
  border: 1px solid $mercury-border-soft;
  border-radius: 8px;

  div.highlight, pre.highlight {
    margin: 0;
    background-color: $sqkon-code-block-bg;
    border: 0;
    border-radius: 0;
  }

  pre.highlight { padding: 0.75rem 1rem; }
  code { background: transparent; border: 0; }
}

.sqkon-code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.4rem 1rem;
  background: $sqkon-code-block-bg;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sqkon-code-lang {
  font-family: $mono-font-family;
  font-size: 0.6875rem;
  color: #7f849c;
  text-transform: none;
}

.sqkon-code-copy {
  padding: 0.1rem 0.5rem;
  font-size: 0.6875rem;
  color: #7f849c;
  cursor: pointer;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 5px;

  &:hover { color: #e7e6e0; border-color: rgba(255, 255, 255, 0.35); }
  &:focus-visible { outline: 2px solid #8a93f5; outline-offset: 2px; }
  &.sqkon-copied { color: #a7d78a; border-color: rgba(167, 215, 138, 0.4); }
}

// Rouge token palette — indigo/green on charcoal, both schemes.
.highlight {
  color: #e7e6e0; // base + unlisted tokens

  .c, .c1, .cm, .cp, .cs { color: #7f849c; font-style: italic; } // comments
  .k, .kd, .kn, .kp, .kr, .kt, .kc { color: #8a93f5; }           // keywords/types
  .s, .s1, .s2, .sb, .sc, .sd, .se, .sh, .si, .sx { color: #a7d78a; } // strings
  .m, .mi, .mf, .mh, .il { color: #f0c674; }                     // numbers
  .n, .nb, .nl, .nn, .nv, .p, .w { color: #e7e6e0; }             // names/punct
  .na, .nc, .nf, .nt { color: #a5c8f0; }                         // attrs/classes/functions
  .o, .ow { color: #c3c8d4; }                                    // operators
  .err { color: #e7e6e0; background: transparent; }              // don't flag pseudo-errors
  .gp { color: #7f849c; }                                        // shell prompt
}

// Mermaid diagrams render light; in dark mode float them on a light card
// rather than restyling mermaid itself (see spec: pragmatic compromise).
html[data-sqkon-theme="dark"] .language-mermaid,
html[data-sqkon-theme="dark"] pre > code.language-mermaid,
html[data-sqkon-theme="dark"] .mermaid {
  padding: 0.5rem;
  background: #f5f4f0;
  border-radius: 8px;
}
```

- [ ] **Step 2: Append the code-chrome JS module to `docs/assets/js/sqkon-docs.js`**

```javascript
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
```

- [ ] **Step 3: Build and verify manually**

Run the test loop (must stay green). Then serve and check `guides/querying/`: every Kotlin block is a dark card with a "Kotlin" label and working Copy button ("Copied" confirmation); bash blocks say "Bash"; the mermaid diagram on `concepts/architecture/` has NO copy header and still renders; toggle to dark — code cards go `#1b1b20`, mermaid diagram sits on a light card.

- [ ] **Step 4: Commit**

```bash
git add docs/_sass docs/assets/js
git commit -m "docs: dark code cards with language labels and copy buttons"
```

---

### Task 6: Page chrome (callouts, inline code, breadcrumbs, headings)

**Files:**
- Modify: `docs/_sass/custom/custom.scss`

**Interfaces:**
- Consumes: `$mercury-*` tokens. Callout class names (`.note`, `.highlight`*, `.important`, `.warning`, `.caution`, `.new`) come from `_config.yml` `callouts:` — unchanged.
  (*JTD generates the callout as a `blockquote`-scoped class, so these selectors don't collide with Rouge's `.highlight` div.)

- [ ] **Step 1: Append the page-chrome SCSS**

Append to `docs/_sass/custom/custom.scss`:

```scss
// --- Page chrome -----------------------------------------------------------

// Inline code: quiet cream/charcoal chip (blocks are handled above)
:not(pre, figure) > code {
  padding: 0.15em 0.35em;
  font-size: 0.85em;
  background-color: $mercury-code-bg;
  border: 1px solid $mercury-border-soft;
  border-radius: 5px;
}

// Breadcrumbs: quieter, smaller
.breadcrumb-nav-list-item {
  font-size: 0.75rem !important;
  color: $mercury-text-muted;

  a { color: $mercury-text-muted; &:hover { color: $mercury-accent; } }
}

// Section headers in the sidebar nav (JTD .nav-category)
.nav-category {
  font-size: 0.6875rem !important;
  color: $mercury-text-muted !important;
  letter-spacing: 0.08em;
}

// Callouts: left accent rail + tinted fill (restyles JTD's generated ones)
blockquote.note, blockquote.highlight, blockquote.important,
blockquote.warning, blockquote.caution, blockquote.new {
  padding: 0.75rem 1rem;
  margin-inline: 0;
  background: rgba($mercury-accent, 0.08);
  border-left: 3px solid $mercury-accent;
  border-radius: 0 8px 8px 0;

  > .highlight-title, > .note-title, > .important-title,
  > .warning-title, > .caution-title, > .new-title { font-weight: 600; }
}

blockquote.warning { background: rgba(240, 198, 116, 0.12); border-left-color: #d9a648; }
blockquote.caution { background: rgba(224, 108, 108, 0.10); border-left-color: #d06060; }
blockquote.important { background: rgba(122, 180, 120, 0.10); border-left-color: #5f9e5d; }

// Tables: soft borders, tinted header row
th { background: $mercury-bg-alt !important; }
td, th { border-color: $mercury-border-soft !important; }

// Motion discipline
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { transition: none !important; animation: none !important; }
}
```

- [ ] **Step 2: Build and verify**

Test loop stays green. Serve and check a page with callouts (`grep -rln "{: .note" docs --include="*.md" | head -3` to find one): callouts render as rail + tint in both schemes; inline code chips look right on light AND dark (toggle); table on `concepts/comparison/` has tinted header and soft borders; sidebar section labels are small caps-style.

If JTD 0.12 renders callouts as `div.note` rather than `blockquote.note` (verify with `grep -o 'class="[^"]*note[^"]*"' docs/_site/guides/expiry/index.html | head -3`), change the selectors to match the actual element — keep the rule bodies identical.

- [ ] **Step 3: Commit**

```bash
git add docs/_sass
git commit -m "docs: restyle callouts, inline code, breadcrumbs, and tables"
```

---

### Task 7: Right-hand "On this page" TOC

**Files:**
- Modify: `docs/assets/js/sqkon-docs.js` (append module)
- Modify: `docs/_sass/custom/custom.scss`

**Interfaces:**
- Consumes: JTD's `#main-content` structure (`<div id="main-content"> <main>…</main> <footer include> </div>`) and Kramdown's auto-generated heading `id`s.
- Produces: `<aside class="sqkon-toc">` appended after `<main>`; `html.sqkon-has-toc` class gates the grid layout.

- [ ] **Step 1: Append the TOC JS module to `docs/assets/js/sqkon-docs.js`**

```javascript
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
```

- [ ] **Step 2: Append the TOC SCSS**

```scss
// --- Right TOC ("On this page") --------------------------------------------
.sqkon-toc { display: none; }

// 75rem ≈ 1200px — below this the TOC stays hidden entirely
@media (min-width: 75rem) {
  html.sqkon-has-toc #main-content {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 12.5rem;
    column-gap: 2.5rem;
  }

  html.sqkon-has-toc #main-content > main { grid-column: 1; min-width: 0; }
  html.sqkon-has-toc #main-content > *:not(main):not(.sqkon-toc) { grid-column: 1 / -1; }

  .sqkon-toc {
    display: block;
    grid-column: 2;
    grid-row: 1;
    position: sticky;
    top: 2rem;
    align-self: start;
    max-height: calc(100vh - 4rem);
    padding-left: 1rem;
    overflow-y: auto;
    border-left: 1px solid $mercury-border-soft;

    &__title {
      margin: 0 0 0.5rem;
      font-size: 0.6875rem;
      font-weight: 600;
      color: $mercury-text-muted;
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }

    ul { padding: 0; margin: 0; list-style: none; }
    li { margin: 0; }
    &__h3 { padding-left: 0.75rem; }

    a {
      display: block;
      overflow: hidden;
      padding: 0.2rem 0;
      font-size: 0.8125rem;
      color: $mercury-text-mid;
      text-decoration: none;
      text-overflow: ellipsis;
      white-space: nowrap;

      &:hover { color: $mercury-accent; }
      &.sqkon-toc--active { font-weight: 600; color: $mercury-accent; }
    }
  }
}
```

- [ ] **Step 3: Build and verify**

Test loop green. Serve at desktop width (≥1200px): `guides/querying/` shows the TOC on the right, sticky while scrolling, active item tracks scroll position; narrow the window below 1200px — TOC disappears, content reflows to full width; `getting-started/` index (short page) — if it has <2 h2/h3 headings, no TOC and NO empty right gutter; landing page has no TOC. Check the in-page footer ("Edit this page on GitHub", last-edit line) still spans full width under the content.

- [ ] **Step 4: Commit**

```bash
git add docs/assets/js docs/_sass
git commit -m "docs: add scroll-tracking on-this-page TOC on wide viewports"
```

---

### Task 8: Landing page — split hero

**Files:**
- Modify: `docs/index.md`
- Modify: `docs/_sass/custom/custom.scss` (replace old hero styles, restyle feature cards)
- Modify: `docs/assets/js/sqkon-docs.js` (coordinate-chip copy)
- Test: `scripts/validate-docs.rb`

**Interfaces:**
- Consumes: `.sqkon-no-chrome` opt-out from Task 5; `site.sqkon_version` from `_config.yml`.
- Produces: hero DOM `.hero--split > .hero__copy + .hero__code`; `button.sqkon-coord[data-copy]` chip.

- [ ] **Step 1: Write the failing validator assertions**

In `scripts/validate-docs.rb`, append before the `if errors.empty?` block:

```ruby
# --- Visual refresh: split hero ---
errors << "missing .hero--split" unless doc.at_css(".hero--split")
errors << "missing hero code panel" unless doc.at_css(".hero__code .highlight")
coord = doc.at_css("button.sqkon-coord")
errors << "missing maven-coordinate chip" unless coord
if coord && coord["data-copy"] !~ /\Acom\.mercury\.sqkon:library:\d/
  errors << "coordinate chip data-copy looks wrong: #{coord && coord["data-copy"].inspect}"
end
```

Run the test loop. Expected: FAILED with the three new messages.

- [ ] **Step 2: Rewrite the hero block in `docs/index.md`**

Replace the current `<div class="hero">…</div>` block (lines 9–17) AND the badge paragraph after it with:

```html
<div class="hero hero--split">
  <div class="hero__copy">
    <p class="hero__eyebrow">Kotlin Multiplatform</p>
    <h1 class="hero__title">The KV store that speaks SQL.</h1>
    <p class="hero__tagline">Serialized Kotlin objects, queryable JSONB fields, reactive Flows — on Android and JVM.</p>
    <p class="hero__actions">
      <a href="{{ '/getting-started/quickstart/' | relative_url }}" class="btn btn-primary fs-5">Quickstart →</a>
      <button class="sqkon-coord" type="button"
              data-copy="com.mercury.sqkon:library:{{ site.sqkon_version }}"
              aria-label="Copy Maven coordinates">
        com.mercury.sqkon:library:{{ site.sqkon_version }}
      </button>
    </p>
  </div>
  <div class="hero__code sqkon-no-chrome">
    <div class="hero__code-bar" aria-hidden="true"><span></span><span></span><span></span></div>
{% highlight kotlin %}
// Query nested JSON, type-safely
val merchants = sqkon.keyValueStorage<Merchant>("merchants")

merchants.select(
    where = Merchant::category eq "Food",
    orderBy = listOf(OrderBy(Merchant::name)),
).collect { list -> render(list) }
{% endhighlight %}
  </div>
</div>

<p class="hero__badges">
<a href="https://central.sonatype.com/artifact/com.mercury.sqkon/library"><img src="https://img.shields.io/maven-central/v/com.mercury.sqkon/library" alt="Maven Central Version"></a>
<a href="https://github.com/MercuryTechnologies/sqkon/actions"><img src="https://img.shields.io/github/check-runs/MercuryTechnologies/sqkon/main" alt="CI status"></a>
<a href="https://github.com/MercuryTechnologies/sqkon/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License: Apache 2.0"></a>
</p>
```

Keep everything from `## Install` down unchanged, but DELETE the now-duplicated badge markdown lines (the `[![Maven Central Version]…` block) since the badges moved into `.hero__badges`. Keep the "GitHub" and "API Reference" links available via the nav (`nav_external_links` already covers both — verify, don't duplicate in the hero).

Check the DSL sample compiles conceptually against the querying guide (`docs/guides/ordering.md`) — if `orderBy` takes a single `OrderBy` rather than a list in current docs examples, match the docs' existing usage verbatim.

- [ ] **Step 3: Replace the hero SCSS in `docs/_sass/custom/custom.scss`**

DELETE the existing `.hero`, `.hero__title`, `.hero__tagline` rules and add:

```scss
// --- Split hero (landing) ---------------------------------------------------
.hero--split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr);
  gap: 2.5rem;
  align-items: center;
  padding: 3rem 0 2.5rem;
  margin: 0 0 1rem;
  text-align: left;
  background: none;
  border-bottom: 0;

  @media (max-width: 56.25rem) { // 900px: stack
    grid-template-columns: 1fr;
    padding-top: 1.5rem;
  }
}

.hero__eyebrow {
  margin: 0 0 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: $mercury-accent;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.hero__title {
  margin: 0 0 0.75rem;
  font-size: 2.5rem;
  line-height: 1.1;
  color: $mercury-text;
}

.hero__tagline {
  max-width: 34ch;
  margin: 0 0 1.5rem;
  font-size: 1.125rem;
  color: $mercury-text-mid;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
}

.sqkon-coord {
  padding: 0.5rem 0.875rem;
  font-family: $mono-font-family;
  font-size: 0.8125rem;
  color: $mercury-text-mid;
  cursor: pointer;
  background: $mercury-code-bg;
  border: 1px solid $mercury-border-soft;
  border-radius: 8px;

  &:hover { color: $mercury-accent; border-color: $mercury-accent; }
  &:focus-visible { outline: 2px solid $mercury-accent; outline-offset: 2px; }
  &.sqkon-copied { color: #5f9e5d; border-color: #5f9e5d; }
}

.hero__code {
  overflow: hidden;
  background: #272735;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(39, 39, 53, 0.18);

  div.highlighter-rouge, div.highlight, pre.highlight {
    margin: 0;
    background: transparent;
    border: 0;
    border-radius: 0;
  }

  pre.highlight { padding: 0.5rem 1.25rem 1.25rem; }
}

.hero__code-bar {
  display: flex;
  gap: 0.4rem;
  padding: 0.75rem 1rem 0.25rem;

  span {
    width: 0.6rem;
    height: 0.6rem;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.18);
  }
}

.hero__badges { margin: 0 0 2rem; }
```

Leave the existing `.feature-card` rules unchanged — they already consume `$mercury-*` tokens, so they adapt to both schemes automatically.

- [ ] **Step 4: Append the coordinate-chip copy module to `docs/assets/js/sqkon-docs.js`**

```javascript
// --- Maven-coordinate copy chip (landing hero) ------------------------------
(function () {
  "use strict";

  function init() {
    document.querySelectorAll("button.sqkon-coord[data-copy]").forEach(function (btn) {
      var original = btn.textContent;
      btn.addEventListener("click", function () {
        var text = btn.getAttribute("data-copy");
        var done = function () {
          btn.textContent = "Copied ✓";
          btn.classList.add("sqkon-copied");
          setTimeout(function () {
            btn.textContent = original;
            btn.classList.remove("sqkon-copied");
          }, 1500);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done);
        }
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
```

- [ ] **Step 5: Run the test loop — expect PASS, then manual check**

Test loop green (including the 6-feature-card and hero assertions). Serve and verify: split hero at desktop width with dark mac-window code panel (dots, shadow, NO copy-button header on it); coordinate chip copies `com.mercury.sqkon:library:1.3.2` (or current version) and confirms; below 900px the hero stacks; badges sit under the hero; dark mode — hero text tokens adapt, code panel unchanged.

- [ ] **Step 6: Commit**

```bash
git add docs/index.md docs/_sass docs/assets/js scripts/validate-docs.rb
git commit -m "docs: split hero landing with code panel and copy chip"
```

---

### Task 9: Full verification sweep

**Files:**
- None expected (fix-forward if issues found)

- [ ] **Step 1: Clean build + validator + link check**

```bash
cd docs
rm -rf _site .jekyll-cache
JEKYLL_ENV=production bundle exec jekyll build
bundle exec ruby ../scripts/validate-docs.rb _site
bundle exec htmlproofer ./_site --disable-external --allow-hash-href \
  --swap-urls "^/sqkon:" --ignore-urls "/\/api\//"
```

Expected: all three exit 0. (`--ignore-urls "/api/"` replaces CI's Dokka step — Dokka output doesn't exist locally.)

- [ ] **Step 2: Screenshot matrix**

Serve the site (`bundle exec jekyll serve`). Using the Playwright browser tools (or a manual browser pass if unavailable), capture and INSPECT:

| Page | Scheme | Viewport |
|---|---|---|
| `/sqkon/` (landing) | light + dark | 1440px + 390px |
| `/sqkon/guides/querying/` | light + dark | 1440px + 390px |

Check each against the spec: no horizontal page scroll at 390px; code blocks scroll internally; TOC present only at 1440px on the guide; hero stacks at 390px; no unstyled-font flash artifacts.

- [ ] **Step 3: Keyboard + no-JS pass**

- Tab order reaches: skip-link → theme toggle → nav → search → content links → copy buttons. Enter activates toggle and copy.
- Disable JS (Playwright: `javaScriptEnabled: false`, or browser devtools): site renders, readable in light theme, no TOC/copy buttons, no layout gaps.

- [ ] **Step 4: Contrast spot-check**

Verify these pairs meet WCAG AA (4.5:1 body, 3:1 large text) with any contrast checker:

| Foreground | Background | Context |
|---|---|---|
| `#535461` | `#ffffff` | light secondary text |
| `#5266eb` | `#ffffff` | light links |
| `#a3a3ab` | `#232329` | dark secondary text |
| `#8a93f5` | `#232329` | dark links |
| `#e7e6e0` | `#272735` | code base text |
| `#8a93f5` | `#272735` | code keywords |

If any pair fails, darken/lighten the FOREGROUND minimally and re-run the sweep; update the spec's token table in the same commit.

- [ ] **Step 5: Commit any fixes and close out**

```bash
git add -A && git commit -m "docs: verification fixes for visual refresh"  # only if fixes were needed
```

---

## Deferred / follow-ups (not in this plan)

- Regenerate `docs/assets/images/og-card.png` to match the new hero (spec: optional follow-up).
- Dokka `/api/` styling alignment.
