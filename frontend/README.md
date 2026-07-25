# HelpDesk Frontend — Foundation & Auth UI (Phase 4, Milestones 1-2)

Vanilla HTML5/CSS3/ES6 frontend for the HelpDesk Management System. No
framework, no build step — every file is served as-is.

## Folder structure

```
frontend/
  index.html               Entry point; redirects to login.html (no dashboard yet)
  login.html                Sign-in screen (UI only, not wired to the backend)
  forgot-password.html       Request a reset link (email only, simulated send)
  reset-password.html         New password + confirm, reads ?token= from the URL
  verification-pending.html    "Check your email" holding page after account creation
  css/
    variables.css      Design tokens — the only file allowed to contain a hex color
    base.css            Reset, root typography, focus/accessibility defaults
    layout.css          Containers, flex/grid utilities, app-shell skeleton
    components.css      Buttons, cards, forms, tables, badges, alerts, spinner, modal,
                          toast, empty state, password strength meter, requirements checklist
    auth.css             Auth-screen-only layout (split brand panel + card), shared by
                          every page above
  js/
    core/
      config.js           API base URL and other environment constants
      api.js               fetch() wrapper: GET/POST/PUT/DELETE, credentials, JSON, ApiError
      utils.js              showToast, showLoading/hideLoading, setButtonLoading,
                              setFieldError, debounce, formatDate, escapeHtml, getCookie
      auth.js                login/logout/refreshToken/getCurrentUser — stubs, not implemented yet
      validation.js           isValidEmail, password requirement/strength rules, match check
    pages/
      login.js                     Password toggle, client-side validation, simulated submit
      forgot-password.js            Email validation, simulated send, success-state swap
      reset-password.js              Token check, live requirements/strength, simulated submit
      verification-pending.js        Resend button placeholder with cooldown timer
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
   forms, tables, badges, alerts, spinner, modal, toast, empty state,
   password strength meter, password requirements checklist). Nothing here
   is page-specific — the strength meter and requirements checklist live
   here rather than in `auth.css` because a future change-password page
   will reuse them outside the auth flow.
5. **auth.css** — shared by every auth-family screen (`login.html`,
   `forgot-password.html`, `reset-password.html`,
   `verification-pending.html`): the split brand-panel/card layout, the
   entrance animation, and the input-affix (icon + show/hide toggle)
   treatment. A future non-auth page gets its own equivalent file rather
   than growing `components.css` or `auth.css` with one-off rules.

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
  with no domain knowledge: toasts, loading indicators, `setButtonLoading`
  (spinner + disable a button during an in-flight or simulated request),
  `setFieldError` (toggle a field's `aria-invalid` + its error text
  together), debounce, date formatting, HTML escaping, cookie reading.
- `auth.js` — stubs only so far. Documents the exact backend contract
  (`/auth/login`, `/auth/refresh`, `/auth/logout`, cookie-based sessions,
  no client-stored tokens) each function will fulfil once implemented, so
  wiring it up later is filling in a body, not designing an API.
- `validation.js` — no imports. Pure functions only, no DOM access:
  `isValidEmail`, the five `PASSWORD_RULES` (mirrors the backend's real
  `StrongPasswordValidator` — 10-128 chars, upper/lower/digit/symbol) plus
  `getPasswordRequirementResults`/`meetsAllPasswordRequirements`,
  `getPasswordStrength` (a 5-level score derived from how many rules
  pass), and `passwordsMatch`. Every page that touches a password imports
  this rather than re-deriving the rules.

`pages/` — one script per HTML page, imported by that page only, and
importing only from `core/` (never from another file in `pages/`):

- `login.js` — password visibility toggle, required/format validation,
  simulated submit with a loading state.
- `forgot-password.js` — email validation, simulated send, then swaps the
  card's content to the success state (focus moves to its heading).
- `reset-password.js` — reads `?token=` from the URL and shows an
  invalid-link state if it's missing; otherwise wires live password
  requirement/strength feedback, a live confirm-password match check,
  submit validation, and a simulated success swap.
- `verification-pending.js` — resend button: simulated send +
  success toast + a 30-second disabled cooldown with a live countdown
  label.

Future pages follow the same pattern: `pages/dashboard.js`,
`pages/tickets.js`, `pages/ticket-details.js`, `pages/profile.js`,
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
- Milestone 2's copy and rules mirror real backend behavior read from
  source (no backend files were changed): password policy is
  `StrongPasswordValidator` (10-128 chars, upper/lower/digit/symbol);
  `forgot-password.html`'s success text is the backend's exact
  anti-enumeration string; `reset-password.html`'s invalid-link text
  matches the single generic message the backend uses for
  expired/used/missing tokens alike (by design, so neither side can leak
  which case it was); the reset token's 30-minute TTL is the code's actual
  constant, not the docs' stale "1 hour" claim.
- `verification-pending.html`'s resend button is a placeholder only. The
  real backend endpoint (`POST /account/resend-verification`) requires an
  authenticated session and always resends to the caller's own address —
  it never accepts an email in the request body. Whichever milestone wires
  this up needs the user to already be logged in (unverified) to reach
  this page for real, not a pre-login "check your email after signup"
  screen — there is no public self-registration flow either.
