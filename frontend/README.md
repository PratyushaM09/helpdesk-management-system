# HelpDesk Frontend — Foundation (Phase 4, Milestone 1)

Vanilla HTML5/CSS3/ES6 frontend for the HelpDesk Management System. No
framework, no build step — every file is served as-is.

## Folder structure

```
frontend/
  index.html          Entry point; redirects to login.html (no dashboard yet)
  login.html           Sign-in screen (UI only, not wired to the backend)
  css/
    variables.css      Design tokens — the only file allowed to contain a hex color
    base.css            Reset, root typography, focus/accessibility defaults
    layout.css          Containers, flex/grid utilities, app-shell skeleton
    components.css      Buttons, cards, forms, tables, badges, alerts, spinner, modal, toast, empty state
    auth.css             Login-screen-only layout and visual treatment
  js/
    core/
      config.js           API base URL and other environment constants
      api.js               fetch() wrapper: GET/POST/PUT/DELETE, credentials, JSON, ApiError
      utils.js              showToast, showLoading/hideLoading, debounce, formatDate, escapeHtml, getCookie
      auth.js                login/logout/refreshToken/getCurrentUser — stubs, not implemented yet
    pages/
      login.js               Page script for login.html (password toggle, placeholder submit)
  assets/
    images/
    icons/
```

## CSS architecture

Five layers, always loaded in this order, each with one job:

1. **variables.css** — every color, spacing value, radius, shadow, font size,
   transition, z-index, and container width lives here as a custom
   property. No other file hardcodes a value these tokens already cover.
2. **base.css** — the reset, global typography, and the single
   `:focus-visible` treatment every interactive element shares.
3. **layout.css** — structural, content-agnostic primitives: `.container`,
   flex/grid utility classes, the `.app-shell` skeleton reserved for
   Milestone 2's sidebar/topbar.
4. **components.css** — the reusable component library (buttons, cards,
   forms, tables, badges, alerts, spinner, modal, toast, empty state).
   Nothing here is page-specific.
5. **auth.css** — the one page-specific stylesheet in this milestone,
   loaded only by `login.html`. Future pages get their own equivalent file
   rather than growing `components.css` with one-off rules.

**Visual identity** ("Signal & Slate"): a cool slate-teal neutral scale, a
deep teal brand color for primary actions, and an amber "signal" accent
reserved for anything that needs attention (priority, pending, warnings) —
literal for a system where tickets are signals waiting on a response.
Typeface is IBM Plex Sans/Mono, chosen because it was designed specifically
for enterprise software interfaces rather than marketing sites. Backgrounds
use layered gradients/a faint grid texture only on the login screen's brand
panel — every working screen after this stays a flat, calm surface so nothing
competes with ticket data.

## JavaScript architecture

Native ES modules (`<script type="module">`), no bundler, no globals.
Split into two folders on purpose: `core/` (shared, framework-ish modules —
should stay a short, stable list) and `pages/` (one file per HTML page —
expected to grow to 15-20 files as dashboard/tickets/admin pages are added,
without ever crowding `core/`). A page's script only ever imports from
`core/`, never from another file in `pages/`.

`core/` — each file has exactly one responsibility:

- `config.js` — no imports. The single source of truth for the API base URL
  and any other environment value. Nothing else in the codebase should
  contain a literal URL, header name, or storage key.
- `api.js` — imports `config.js` and `utils.js` (for the CSRF cookie
  reader). Owns all `fetch()` transport concerns: credentials, JSON
  encoding/parsing, timeouts, and translating the backend's
  `ApiResponse`/`ErrorResponse` envelopes into either a plain return value
  or a thrown `ApiError`. Contains zero auth logic and zero page logic.
- `utils.js` — no imports besides `config.js`. Generic, reusable helpers
  with no domain knowledge: toasts, loading indicators, debounce, date
  formatting, HTML escaping, cookie reading.
- `auth.js` — stubs only in this milestone. Documents the exact backend
  contract (`/auth/login`, `/auth/refresh`, `/auth/logout`, cookie-based
  sessions, no client-stored tokens) each function will fulfil once
  implemented, so wiring it up later is filling in a body, not designing
  an API.

`pages/` — one script per HTML page, imported by that page only:

- `login.js` — UI wiring for `login.html` alone (password visibility
  toggle, placeholder submit handler). Future pages follow the same
  pattern: `pages/dashboard.js`, `pages/tickets.js`,
  `pages/ticket-details.js`, `pages/profile.js`,
  `pages/admin-users.js`, `pages/admin-roles.js`, etc.

## Naming conventions

- **Files**: kebab-case (`login.js`, `auth.css`).
- **CSS classes**: BEM-lite — `.component`, `.component__part`,
  `.component--variant` (e.g. `.card`, `.card__header`, `.btn--primary`).
  Utility classes are short and literal (`.flex`, `.gap-4`, `.mt-6`).
- **CSS custom properties**: `--category-name-step`, e.g.
  `--color-brand-600`, `--space-4`, `--shadow-md`.
- **JavaScript**: camelCase for functions/variables, PascalCase for classes
  (`ApiError`), `UPPER_SNAKE_CASE` only inside the frozen `CONFIG` object.
- **IDs**: kebab-case, used only where CSS/JS needs a unique hook
  (`#login-form`, `#toggle-password`).

## Responsive strategy

Desktop-first: base rules target desktop/tablet layouts, with `max-width`
media queries scaling down. Three breakpoints, defined once and referenced
as literal values (custom properties can't be used inside `@media`
conditions, so `variables.css` documents them for reference):

- `1024px` — collapse multi-column layouts (`.grid-cols-4`, the login
  screen's brand panel, the future `.app-shell` sidebar) to single-column/no
  decorative panel.
- `768px` — remaining grid columns collapse to one; container padding
  tightens.
- `480px` — reserved for fine-tuning dense components once real pages
  (tables, ticket lists) exist.

All interactive elements meet WCAG AA contrast and expose a visible
`:focus-visible` ring; `prefers-reduced-motion: reduce` disables every
animation/transition globally from `base.css`.

## Assumptions

- Backend runs at `http://localhost:8080`, base path `/api/v1` (from
  `ApiConstants.API_BASE_PATH`) — set in `config.js`.
- The backend's CORS allow-list (`application.yml`) only permits
  `http://localhost:3000` and `http://localhost:5173` in dev. Since this
  project uses no build tooling, the frontend must be served from a static
  server bound to one of those two origins/ports (e.g. `npx serve -l 3000`)
  for cookie-based auth to work later — opening `login.html` via
  `file://` or a different port will fail CORS once auth.js is implemented.
- CSRF double-submit cookie name (`csrf_token`) and header
  (`X-CSRF-Token`) are taken from `SecurityConstants` on the backend and
  hardcoded nowhere except `config.js`.
- No self-registration UI exists because the backend has no public
  registration endpoint — accounts are provisioned by an admin, hence
  login.html's footer pointing users to "contact your IT administrator."
- Fonts (IBM Plex Sans/Mono) and Bootstrap Icons are loaded via CDN
  `<link>` tags rather than vendored locally, consistent with "no build
  tools" — this needs internet access in dev; swap for self-hosted files
  before an offline/air-gapped deployment.
