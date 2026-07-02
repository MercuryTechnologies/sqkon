# Sqkon Docs Visual Refresh — Design

**Date:** 2026-07-01
**Status:** Approved
**Scope:** Visual/brand redesign of the Jekyll docs site (`docs/`). Chrome and skin only — no content rewrites, no IA changes.

## Goal

Refresh the docs site's look to combine Mercury brand identity with modern dev-docs craft (Stripe/Linear-style). Keep the platform (Jekyll + Just-the-Docs theme gem), keep all existing markdown content, keep search/nav/a11y from the theme.

Decisions below were validated with mockups during brainstorming:

| Decision | Choice |
|---|---|
| Landing page | **B — crisp split hero** (left-aligned pitch + floating dark code panel) |
| Inner pages | **V1 — dark code blocks, right-hand "On this page" TOC** |
| Dark mode | **Light + dark toggle** (persisted, defaults to system preference) |
| Dark base tone | **D2 — warm charcoal** (`#232329` family) |
| Typography | **Self-hosted Inter** (UI/body) + **IBM Plex Mono** (code) |
| Approach | **Deep JTD customization** — no theme fork |

## 1. Visual system

### Light scheme (default) — updates to `mercury.scss`

- Canvas: white `#ffffff`; sidebar: near-white `#fafaf8` with `#e7e6e0` border (replaces the cream sidebar).
- Text: charcoal `#272735`; secondary `#535461`; accent: brand blue `#5266eb`, hover `#4354c8`.
- Cream (`#efeee9` family) survives only as small touches: inline-code background, subtle fills, the maven-coordinate chip. No large cream surfaces.
- Code blocks flip to dark-on-light pages: charcoal `#272735` background with light syntax colors (indigo `#8a93f5` keywords, green `#a7d78a` strings — matching the mockups).

### Dark scheme (new) — `mercury-dark.scss`

- Base `#232329`; panels/sidebar `#28282f`; borders `rgba(255,255,255,0.08–0.09)`.
- Text `#f5f4f0`; secondary `#a3a3ab`; muted `#9494a0` (AA 4.5:1 on the dark base; light-scheme muted is `#70707d`).
- Accent: brand blue lightened to `#8a93f5` for links/active states (AA on the dark base); callout fill `rgba(138,147,245,0.10–0.16)`.
- Code blocks `#1b1b20` with a hairline border.

### Typography

- **Inter variable** (woff2, self-hosted in `docs/assets/fonts/`, preloaded) for UI and body. System-stack fallback preserved.
- **IBM Plex Mono** (OFL-licensed — Mercury's actual mono font) for code blocks and inline code.
- Mercury's brand fonts (Arcadia, Tiempos) are proprietary/commercially licensed and MUST NOT be bundled in this public Apache-2.0 repo or hotlinked from Mercury's CDN.
- Keep tight heading tracking (`-0.02em`) and weight 600–650 headings.
- Total font payload budget: ~180KB woff2.

### Theme toggle

- Sun/moon button in the sidebar header area.
- Implemented as a second JTD color scheme (`mercury-dark`) switched with `jtd.setTheme()`.
- A tiny inline script in `head_custom.html` runs before first paint: reads `localStorage` (`sqkon-theme`); if unset, falls back to `prefers-color-scheme`. No flash of wrong theme.
- Toggle is keyboard-operable with a visible focus ring and an `aria-label`.

## 2. Page treatments

### Landing page (`docs/index.md`)

- Split hero: left column — eyebrow label "Kotlin Multiplatform" (uppercase, blue, tracked), headline, one-line pitch, primary "Quickstart →" button, and a click-to-copy maven-coordinate chip (`com.mercury.sqkon:library:<version>` from `site.sqkon_version`).
- Right column — floating mac-window-style dark code panel (traffic-light dots, soft shadow) showing the query DSL example.
- Hero stacks vertically below ~900px; code panel goes full-width.
- Feature-card grid retained, restyled to the new system. Badges (Maven Central, CI, license) move below the hero.

### Inner pages (all guides/concepts/examples/reference)

- Header: breadcrumb (section / page) → H1 → lede paragraph style.
- **Code blocks:** charcoal card with header row — language label (derived from Rouge's `language-*` class) left, copy button right. Copy button shows a brief "Copied" confirmation.
- **Callouts:** existing `{: .note }` / `{: .highlight }` etc. restyled as left-rail blocks (3px accent rail, tinted fill, rounded right corners). Callout config names in `_config.yml` are unchanged.
- **Right TOC ("On this page"):** built client-side from `h2`/`h3` heading IDs; IntersectionObserver drives the active state; hidden below ~1200px viewport width. Pages with fewer than 2 headings render no TOC.
- All existing markdown pages (33) render unmodified — no front-matter or content edits required beyond `index.md`'s hero markup.

## 3. Implementation shape

| Piece | File |
|---|---|
| Dark scheme tokens | `docs/_sass/color_schemes/mercury-dark.scss` (new) |
| Light palette updates | `docs/_sass/color_schemes/mercury.scss` |
| All component styling (hero, cards, code chrome, callouts, TOC) | `docs/_sass/custom/custom.scss` |
| Fonts, preloads, no-FOUC theme script | `docs/_includes/head_custom.html` + `docs/assets/fonts/` |
| Dark stylesheet emission | `docs/assets/css/just-the-docs-mercury-dark.scss` (JTD's documented alternate-scheme pattern) |
| Toggle button placement | shadowed `docs/_includes/components/sidebar.html` (copied from the JTD gem, one button added; no `_layouts` shadow needed — the right TOC is injected client-side) |
| Copy buttons, language labels, TOC build + scroll-spy, toggle handler | `docs/assets/js/sqkon-docs.js` (new, vanilla JS, no dependencies) |
| Hero markup | `docs/index.md` |

Notes:

- `!default` fallbacks in `custom.scss` must be kept/extended — JTD always compiles its stock light/dark schemes alongside custom ones (see existing comment in that file).
- The shadowed `components/sidebar.html` must be diffed against the gem's version at upgrade time; keep the edit surface minimal (one added button) and comment the divergence point.
- JS is progressive enhancement: with JS disabled the site renders fully (no TOC, no copy buttons, theme fixed to light/system).

## 4. Error handling / edge cases

- **No-JS:** content fully readable; enhancement-only features absent.
- **Long headings / deep pages:** TOC truncates with ellipsis; TOC scrolls independently if taller than viewport.
- **Small screens:** right TOC hidden < 1200px; hero stacks < 900px; code blocks scroll horizontally inside their card.
- **`localStorage` unavailable** (private mode): toggle still works for the session; preference just isn't persisted.
- **Reduced motion:** transitions gated behind `prefers-reduced-motion`.

## 5. Verification

- `bundle exec jekyll build` completes clean (run in `docs/` with the pinned Gemfile).
- Playwright screenshots: landing + one guide page (e.g. Querying) × {light, dark} × {desktop, ~390px mobile}.
- Keyboard pass: theme toggle and copy buttons reachable and operable; visible focus states.
- Contrast: AA for text/link colors on both schemes (notably `#8a93f5` on `#232329`, `#535461` on `#ffffff`).
- Library unaffected — no changes outside `docs/`; `./gradlew jvmTest` untouched by this work.

## Out of scope

- Content rewrites, page additions, IA/nav reorganization.
- Dokka-generated API reference styling (`/api/` is a separate pipeline).
- The og-card image (`docs/assets/images/og-card.png`) — **optional follow-up:** regenerate to match the new hero so link previews aren't dated.
- iOS docs coverage changes.
