# HelpDesk Frontend — Foundation, Auth UI & App Shell (Phase 4, Milestones 1-3)

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
  dashboard.html                Authenticated app shell (sidebar+topbar) + placeholder dashboard content
  css/
    variables.css      Design tokens — the only file allowed to contain a hex color
    base.css            Reset, root typography, focus/accessibility defaults
    layout.css          Containers, flex/grid utilities, .app-shell grid (sidebar/topbar/main)
    components.css      Buttons, cards, forms, tables, badges, alerts, spinner, modal, toast,
                          empty state, password strength meter, requirements checklist, page
                          header, stat card, avatar, dropdown menu, activity list
    auth.css             Auth-screen-only layout (split brand panel + card), shared by every
                          auth page (login/forgot-password/reset-password/verification-pending)
    shell.css             Sidebar + topbar visual skin, shared by every authenticated page
                          (dashboard.html now; tickets/profile/admin pages later)
  js/
    core/
      config.js           API base URL and other environment constants
      api.js               fetch() wrapper: GET/POST/PUT/DELETE, credentials, JSON, ApiError
      utils.js              showToast, showLoading/hideLoading, setButtonLoading,
                              setFieldError, debounce, formatDate, escapeHtml, getCookie
      auth.js                login/logout/refreshToken/getCurrentUser — stubs, not implemented yet
      validation.js           isValidEmail, password requirement/strength rules, match check
      shell.js                Sidebar toggle, nav active-highlighting, user dropdown, search
                                focus animation, logout/notification placeholders — the one
                                module every authenticated page's script calls into
    pages/
      login.js                     Password toggle, client-side validation, simulated submit
      forgot-password.js            Email validation, simulated send, success-state swap
      reset-password.js              Token check, live requirements/strength, simulated submit
      verification-pending.js        Resend button placeholder with cooldown timer
      dashboard.js                    Just calls core/shell.js's initShell() — no
                                        dashboard-specific behavior in this milestone
  assets/
    images/
    icons/
```

## CSS architecture

Six layers. Every page loads `variables` → `base` → `layout` → `components`,
then whichever *family* stylesheet matches what kind of page it is (`auth.css`
for the sign-in/recovery flow, `shell.css` for anything behind the sidebar):

1. **variables.css** — every color, spacing value, radius, shadow, font size,
   transition, z-index, container width, and sidebar/topbar dimension lives
   here as a custom property. No other file hardcodes a value these tokens
   already cover.
2. **base.css** — the reset, global typography, and the single
   `:focus-visible` treatment every interactive element shares.
3. **layout.css** — structural, content-agnostic primitives: `.container`,
   flex/grid utility classes, and the `.app-shell` grid (the three-area
   sidebar/topbar/main layout, including its tablet-rail and mobile-overlay
   `@media` behavior). Visual styling of what sits inside that grid is
   deliberately not here — that's `shell.css`'s job, the same split
   `layout.css`/`auth.css` already use for the auth screens.
4. **components.css** — the reusable component library (buttons, cards,
   forms, tables, badges, alerts, spinner, modal, toast, empty state,
   password strength meter, password requirements checklist, page header,
   stat card, avatar, dropdown menu, activity list). Nothing here is
   page-specific or shell-specific — a stat card or dropdown menu is just as
   usable on a future ticket-details page as it is on the dashboard.
5. **auth.css** — shared by every auth-family screen (`login.html`,
   `forgot-password.html`, `reset-password.html`,
   `verification-pending.html`): the split brand-panel/card layout, the
   entrance animation, and the input-affix (icon + show/hide toggle)
   treatment.
6. **shell.css** — shared by every authenticated-family screen
   (`dashboard.html` now; tickets/profile/admin pages later): the sidebar's
   dark visual skin (brand row, nav links, active-state, tablet icon-rail
   label-hiding), and the topbar's contents (search box + its focus
   animation, notification badge, user menu trigger). A future
   non-auth/non-shell page family gets its own equivalent file rather than
   growing `components.css` with one-off rules.

**Visual identity** ("Signal & Slate"): a cool slate-teal neutral scale, a
deep teal brand color for primary actions, and an amber "signal" accent
reserved for anything that needs attention (priority, pending, warnings) —
literal for a system where tickets are signals waiting on a response.
Typeface is IBM Plex Sans/Mono, chosen because it was designed specifically
for enterprise software interfaces rather than marketing sites. Backgrounds
use layered gradients/a faint grid texture only on the login screen's brand
panel — every working screen after this stays a flat, calm surface so nothing
competes with ticket data. The one deliberate exception is the sidebar: a
solid dark slate (not a gradient) that echoes the auth screens' brand panel,
giving the authenticated app a consistent dark-chrome/light-content split
without repeating the gradient/texture treatment outside the auth flow.

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
- `shell.js` — imports `utils.js` (for `showToast`). `initShell()` is the
  single entry point every authenticated page's script calls: it wires the
  mobile sidebar toggle + overlay backdrop + Escape-to-close, marks the
  sidebar link whose `data-nav` matches the current page as active, the
  topbar user dropdown (click-outside/Escape/item-click all close it), the
  search box's focus animation, and the shared logout/notification
  placeholder toasts (anything with a `data-logout-trigger` attribute gets
  the same "not connected yet" message, whether it's the sidebar's logout
  link or the user menu's). Contains zero page-specific content.

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
- `dashboard.js` — imports and calls `core/shell.js`'s `initShell()` and
  nothing else. Every summary card, activity item, and table row on
  `dashboard.html` is static placeholder markup, so there's no
  dashboard-specific behavior to add yet.

Future pages follow the same pattern: `pages/tickets.js`,
`pages/ticket-details.js`, `pages/profile.js`, `pages/admin-users.js`,
`pages/admin-roles.js`, etc. — each one copies the same shell markup as
`dashboard.html` and calls `initShell()` too.

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
  (`#login-form`, `#toggle-password`, `#sidebar-toggle`).
- **`data-*` attributes**: used for JS hooks that aren't styling hooks and
  aren't unique-per-page, so a class/ID would be the wrong tool —
  `data-nav="dashboard.html"` (nav active-highlighting, value is the target
  page's filename) and `data-logout-trigger` (marks every element, however
  many there are on a page, that should show the same logout placeholder).

## Responsive strategy

Desktop-first: base rules target desktop/tablet layouts, with `max-width`
media queries scaling down. Three breakpoints, defined once and referenced
as literal values (custom properties can't be used inside `@media`
conditions, so `variables.css` documents them for reference):

- `1024px` — collapse multi-column layouts (`.grid-cols-4`, the login
  screen's brand panel) to single-column/no decorative panel. The app
  shell's sidebar collapses to a permanent icon-only rail here (labels
  hidden via CSS, every item stays reachable, no JS involved).
- `768px` — remaining grid columns collapse to one; container padding
  tightens. The app shell's sidebar goes further here: it leaves the grid
  entirely and becomes an off-canvas overlay (`position: fixed`,
  translated off-screen) that `shell.js`'s hamburger toggle slides in over
  a backdrop, restoring full labels since it's a full-width drawer again,
  not a rail. The topbar's search box and user name/role also hide at this
  width to make room for the hamburger button.
- `480px` — fine-tuning for dense components (password requirements
  checklist drops to one column; the page header stacks vertically).

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
- Milestone 3's sidebar links point to real future filenames
  (`tickets.html`, `my-tickets.html`, `create-ticket.html`, `profile.html`,
  `users.html`, `roles.html`) rather than `#` placeholders, since that's
  what each page will actually be named once built — until then, clicking
  them 404s on a static server, which is expected.
- Dashboard summary numbers, activity feed, and the recent-tickets table
  are hardcoded placeholder markup, not generated from any data structure
  — there is nothing here for a future data-loading pass to "hook into"
  beyond replacing the static rows/values directly.
- Logout (both the sidebar link and the user-menu item) and the
  notification bell are wired to the same placeholder toast pattern used
  throughout Milestones 1-2, via a shared `data-logout-trigger` attribute
  rather than one-off handlers.
