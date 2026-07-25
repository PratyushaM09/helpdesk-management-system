# HelpDesk Frontend — Foundation, Auth UI, App Shell, Tickets, Admin & Backend Integration (Phase 4, Milestones 1-6)

Vanilla HTML5/CSS3/ES6 frontend for the HelpDesk Management System. No
framework, no build step — every file is served as-is.

## Folder structure

```
frontend/
  index.html               Entry point; redirects to login.html (no dashboard yet)
  login.html                Sign-in screen — real POST /auth/login (Milestone 6)
  forgot-password.html       Request a reset link (email only, simulated send)
  reset-password.html         New password + confirm, reads ?token= from the URL
  verification-pending.html    "Check your email" holding page after account creation
  dashboard.html                Authenticated app shell (sidebar+topbar) + placeholder dashboard content
  tickets.html                   Ticket list: filters, sortable/filterable table, pagination chrome
  ticket-details.html             Full ticket view: description, timeline, comments, attachments, history
  create-ticket.html               New-ticket form: title/description/category/priority/attachments
  profile.html                      Personal info, password change, account summary, recent activity
  users.html                         Admin user list: search/role/status filters, responsive table
  roles.html                         Read-only role cards: description, example permissions, user count
  css/
    variables.css      Design tokens — the only file allowed to contain a hex color
    base.css            Reset, root typography, focus/accessibility defaults
    layout.css          Containers, flex/grid utilities, .app-shell grid (sidebar/topbar/main),
                          .grid-detail (content + sidebar-summary layout)
    components.css      The full reusable component library — buttons, cards, forms, tables
                          (incl. responsive stacking + clickable rows), badges, alerts, spinner,
                          modal, toast, empty state, password strength meter, requirements
                          checklist, page header, stat card, avatar, dropdown menu, activity
                          list, filter bar/dropdown, pagination, loading skeleton, timeline,
                          comment (+ internal-note styling), comment editor, segmented control,
                          choice chips, attachment row, upload area, meta list
    auth.css             Auth-screen-only layout (split brand panel + card), shared by every
                          auth page (login/forgot-password/reset-password/verification-pending)
    shell.css             Sidebar + topbar visual skin, shared by every authenticated page
                          (dashboard/tickets/ticket-details/create-ticket now; profile/admin later)
  js/
    core/
      config.js           API base URL and other environment constants
      api.js               fetch() wrapper: GET/POST/PUT/DELETE, credentials, JSON, ApiError
      utils.js              showToast, showLoading/hideLoading, setButtonLoading,
                              setFieldError, bindPlaceholderActions, debounce, formatDate,
                              escapeHtml, getCookie
      auth.js                Real login/logout/refreshToken/getCurrentUser — POST /auth/login,
                               /auth/refresh, /auth/logout, GET /account/me — plus the in-memory
                               (never persisted) current-user cache
      session.js               bootstrapSession/requireAuth/redirectIfAuthenticated — session
                                 restoration and page-guarding built on top of auth.js
      validation.js           isValidEmail, password requirement/strength rules, match check
      shell.js                Session guard (redirects guests to login.html) + real topbar user
                                display + real logout, then sidebar toggle, nav active-highlighting,
                                user dropdown, search focus animation — the one module every
                                authenticated page's script calls into
      dropdown.js              Generic trigger+menu open/close/outside-click/Escape controller
      filter-dropdown.js       Wires every [data-filter] dropdown into a single-select filter
      table-filter.js          Filters a static table's rows against [data-filter] dropdowns,
                                 shared by tickets.js and users.js
      attachment-preview.js    Fake (never-uploaded) attachment preview, shared by 2 pages
      password-strength-ui.js  Wires a password input to the requirements checklist + strength
                                 meter markup, shared by reset-password.js and profile.js
    pages/
      login.js                     Real POST /auth/login, backend ErrorResponse messages
                                     surfaced verbatim, redirect based on email-verification status
      forgot-password.js            Email validation, simulated send, success-state swap
      reset-password.js              Token check, live requirements/strength, simulated submit
      verification-pending.js        Resend button placeholder with cooldown timer
      dashboard.js                    Real current-user greeting + email-verification banner on
                                        top of core/shell.js's initShell(); summary/activity/table
                                        stay static (no backend endpoint exists for them — see
                                        Backend Integration below)
      tickets.js                      Skeleton→table swap, filter/sort of static rows,
                                        row-click navigation, empty-state toggling
      ticket-details.js                Comment collapse, visibility segmented control,
                                        attachment upload preview
      create-ticket.js                 Priority chips, character counter, validation,
                                        simulated submit/reset, attachment upload preview
      profile.js                        Password toggles, live strength/requirements, two
                                          independent validated forms, both simulated submits
      users.js                          Role/status filtering of static rows (via
                                          core/table-filter.js) — no row navigation, no sort
      roles.js                          Just calls core/shell.js's initShell() — the page is
                                          fully static/read-only
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
   flex/grid utility classes, the `.app-shell` grid (the three-area
   sidebar/topbar/main layout, including its tablet-rail and mobile-overlay
   `@media` behavior), and `.grid-detail` (a 2fr/1fr content+sidebar-summary
   layout — ticket-details.html now, a future profile/user-details page
   later; deliberately not a 50/50 `.grid-cols-2`, since the sidebar is a
   summary, not a peer column). Visual styling of what sits inside the
   `.app-shell` grid is deliberately not here — that's `shell.css`'s job.
4. **components.css** — the reusable component library. Nothing here is
   page-specific or shell-specific: a stat card, dropdown menu, timeline, or
   comment block is exactly as usable on a future profile or admin page as
   it is here. This is by far the largest file, and deliberately so — the
   milestone that added tickets/comments/attachments/pagination/skeletons
   grew it a lot, but every addition is a component, not page glue.
5. **auth.css** — shared by every auth-family screen (`login.html`,
   `forgot-password.html`, `reset-password.html`,
   `verification-pending.html`): the split brand-panel/card layout, the
   entrance animation, and the input-affix (icon + show/hide toggle)
   treatment.
6. **shell.css** — shared by every authenticated-family screen
   (`dashboard.html`, `tickets.html`, `ticket-details.html`,
   `create-ticket.html`, `profile.html`, `users.html`, `roles.html` — every
   page behind the sidebar now): the sidebar's dark visual skin (brand row,
   nav links, active-state, tablet icon-rail label-hiding), and the
   topbar's contents (search box + its focus animation, notification
   badge, user menu trigger). A future non-auth/non-shell page family gets
   its own equivalent file rather than growing `components.css` with
   one-off rules.

Notably, **no `tickets.css`, `profile.css`, or `users.css` exists** —
every page built across Milestones 4-5 needed only genuinely reusable
components (→ `components.css`) or generic layout primitives (→
`layout.css`), never something specific to "being a ticket page" or
"being the users page." `roles.html` in particular needed *zero* new
CSS at all — its three role cards are 100% existing `.card`/`.badge`/
`.flex` utilities. That's evidence the split is working as intended
rather than a gap.

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
  together), `setupPasswordToggle` (show/hide a password field — used by
  login.js, reset-password.js, and profile.js instead of three copies),
  `bindPlaceholderActions` (wires every `[data-placeholder-action]`
  element to show its own message as a toast — the one mechanism behind
  every "not wired to the backend yet" button across the whole app), debounce,
  date formatting, HTML escaping, cookie reading.
- `auth.js` — imports `api.js`. Real implementations of `login`,
  `logout`, `refreshToken`, `getCurrentUser` (`GET /account/me`, the only
  endpoint carrying `status`/`emailVerified` — login/refresh responses omit
  both), plus `getCachedUser`/`getCurrentRole` for synchronous access. The
  *only* client-side session state is an in-memory `cachedUser` variable —
  deliberately never `localStorage`/`sessionStorage`, since the backend's
  actual session lives entirely in httpOnly cookies the frontend never
  touches directly. A hard refresh always re-derives it via
  `getCurrentUser({ forceRefetch: true })`.
- `session.js` — imports `api.js` (for `ApiError`) and `auth.js`.
  `bootstrapSession()` resolves the current session, transparently
  recovering an expired access token with exactly one silent
  `POST /auth/refresh` before giving up; `requireAuth()` wraps that for
  page-guarding (redirects to `login.html` if there's no recoverable
  session); `redirectIfAuthenticated()` is `login.html`'s mirror image
  (redirects to `dashboard.html` if a session already exists). Network/
  unexpected errors during bootstrap propagate rather than being treated
  as "not logged in," so a connectivity blip doesn't bounce an
  already-signed-in user to the login screen.
- `validation.js` — no imports. Pure functions only, no DOM access:
  `isValidEmail`, the five `PASSWORD_RULES` (mirrors the backend's real
  `StrongPasswordValidator` — 10-128 chars, upper/lower/digit/symbol) plus
  `getPasswordRequirementResults`/`meetsAllPasswordRequirements`,
  `getPasswordStrength` (a 5-level score derived from how many rules
  pass), and `passwordsMatch`. Every page that touches a password imports
  this rather than re-deriving the rules.
- `shell.js` — imports `utils.js`, `dropdown.js`, `session.js` (for
  `requireAuth`), and `auth.js` (for `logout`). `initShell()` is the single
  entry point every authenticated page's script calls, and is now
  `async`: it first awaits `requireAuth()` — redirecting to `login.html`
  if there's no session — then populates the topbar (`.topbar__user-name`,
  the avatar's initials, the role badge) with the *real* signed-in user,
  wires every `[data-logout-action]` element to a real `POST /auth/logout`
  + redirect, and only then wires the mobile sidebar toggle, nav
  active-highlighting, the user dropdown, the search focus animation, and
  every remaining `[data-placeholder-action]`. Returns the resolved user
  (or `null` after a redirect) so a page script that needs it — currently
  just `dashboard.js` — doesn't have to re-fetch it. A network failure
  while checking the session leaves the static shell markup alone instead
  of redirecting (see `session.js`'s `requireAuth` doc). This makes
  session-awareness a shell-level concern applied uniformly to every
  authenticated page, rather than seven separate page scripts each
  repeating the same guard.
- `dropdown.js` — no imports. `initDropdown({ trigger, menu })` is the
  single open/close/outside-click/Escape controller behind three different
  UIs: the topbar user menu (`shell.js`), the tickets page's filter
  dropdowns (`filter-dropdown.js`), and any future row/context menu — one
  implementation instead of three near-identical ones.
- `filter-dropdown.js` — imports `dropdown.js`. `initFilterDropdowns()`
  turns every `[data-filter]` element on the page into a working
  single-select menu (Status/Priority/Category/Sort on tickets.html) and
  dispatches a `filterchange` `CustomEvent` (`{ filter, value }`) whenever
  a selection changes, so the page script decides what a selection
  actually does rather than this module knowing about tables at all.
- `attachment-preview.js` — imports `utils.js` (for `escapeHtml`).
  `initAttachmentUpload({ uploadArea, fileInput, list })` wires a
  drag-and-drop + click-to-browse upload zone that appends a preview row
  per selected `File` (real name/size/icon, via the browser's File API) to
  a list, with a Remove button per row (`.icon-btn`/`.icon-btn--danger` —
  a generic small icon-button class also used by the Users table's row
  actions, not an attachment-specific one despite the module's name).
  Nothing is ever sent anywhere — shared verbatim by ticket-details.html
  and create-ticket.html.
- `table-filter.js` — no imports. `initTableFilter({ tableBody,
  tableWrapper, emptyState, paginationSummary, skeleton, filterKeys, noun })`
  owns exactly "which static rows are currently visible": it listens for
  `filterchange` events matching `filterKeys`, toggles row `hidden`,
  and keeps the empty-state/pagination-summary/skeleton-reveal in sync.
  Sorting and row-click navigation are page-specific and stay in
  `tickets.js`, built on top of this module's returned `getRows()`.
- `password-strength-ui.js` — imports `validation.js`.
  `initPasswordStrengthUI({ passwordInput, strengthWrapper, strengthBar,
  strengthFill, strengthLabel, requirementItems })` wires a password
  input's `input` event to update both the requirements checklist and the
  strength meter, returning an `update()` the caller can also invoke
  manually (`profile.js` calls it after a simulated successful password
  change to reset the meter). Used by `reset-password.js` and `profile.js`
  instead of two copies of the same DOM-wiring logic.

`pages/` — one script per HTML page, imported by that page only, and
importing only from `core/` (never from another file in `pages/`):

- `login.js` — calls `redirectIfAuthenticated()` on load. Password
  visibility toggle, required/format validation, then a real
  `POST /auth/login`: on success, fetches the full profile (`GET
  /account/me`, forced fresh) and redirects to `dashboard.html` or
  `verification-pending.html` depending on `emailVerified` (the backend
  lets an unverified-but-active account log in fine — "handling" that
  case means routing it somewhere sensible after success, not blocking
  sign-in). On failure, backend `ErrorResponse.message` is shown verbatim
  in a form-level alert (covers wrong credentials, a locked account, and
  network/timeout errors alike — the message text is the only thing that
  differs) or mapped onto specific fields when the backend responds with
  `validationErrors`.
- `forgot-password.js` — email validation, simulated send, then swaps the
  card's content to the success state (focus moves to its heading).
- `reset-password.js` — reads `?token=` from the URL and shows an
  invalid-link state if it's missing; otherwise wires password show/hide
  (`setupPasswordToggle`) and live requirement/strength feedback
  (`core/password-strength-ui.js`), a live confirm-password match check,
  submit validation, and a simulated success swap.
- `verification-pending.js` — resend button: simulated send +
  success toast + a 30-second disabled cooldown with a live countdown
  label.
- `dashboard.js` — awaits `core/shell.js`'s `initShell()` for the real
  user, then personalizes the subtitle ("Welcome back, {first name}...")
  and reveals an email-verification reminder banner if
  `user.emailVerified === false`. Every summary card, activity item, and
  table row stays static placeholder markup — no backend endpoint exists
  for dashboard summary or activity data (see Backend Integration below).
- `tickets.js` — delegates filtering/skeleton/empty-state/pagination-summary
  to `core/table-filter.js` (Status/Priority/Category), and separately
  handles Sort (newest/oldest/priority — reorders rows directly, since
  sorting isn't part of what `table-filter.js` owns) and turns a click
  anywhere on a row (that isn't already a link/button) into navigation to
  that row's "View" link destination.
- `ticket-details.js` — wires each comment's collapse/expand toggle, the
  comment editor's Public/Internal segmented control, and the attachment
  upload preview (via `core/attachment-preview.js`).
- `create-ticket.js` — the title character counter, the priority
  choice-chip single-select, full validation (title/description/category/
  priority all required, mirroring the backend's `CreateTicketRequest`),
  a simulated submit, and a `reset` handler that also clears the chips,
  attachment previews, and validation state the native form reset doesn't
  know about.
- `profile.js` — two independent forms, each with its own validation and
  simulated submit: personal info (name required; email is `disabled` in
  the markup, so there's nothing to validate there) and change-password
  (current password required, new password via the same
  `password-strength-ui.js` + `validation.js` combo as reset-password.html,
  confirm-match check). All three password fields get `setupPasswordToggle`.
- `users.js` — delegates entirely to `core/table-filter.js` (Role/Status);
  no sort, no row-click navigation — there's no user-details page yet, so
  every row action is a `data-placeholder-action` toast.
- `roles.js` — the page is fully static/read-only (no CRUD, no filtering),
  so this only calls `initShell()`.

Future pages follow the same pattern: `pages/my-tickets.js`, etc. — each
one copies the same shell markup as `dashboard.html` and calls
`initShell()` too.

## Backend integration (Milestone 6)

The first milestone where the frontend actually calls the backend. Scope
was deliberately narrow — auth + session + the parts of the dashboard a
session can drive — confirmed with the user before writing code once it
became clear the backend has no dashboard-summary or activity-feed
endpoint at all (verified by reading every controller in the backend
tree, not assumed).

**Endpoints connected:**

| Endpoint | Used by | Notes |
|---|---|---|
| `POST /auth/login` | `auth.js`'s `login()` | Body `{email,password}`; response is `{id,name,email,role}` only — no `status`/`emailVerified` |
| `GET /account/me` | `auth.js`'s `getCurrentUser()` | The only endpoint with `status`/`emailVerified`; used by session bootstrap and login's post-success redirect decision |
| `POST /auth/refresh` | `auth.js`'s `refreshToken()` | No body — reads the `refresh_token` cookie automatically (its `Path` is scoped to exactly this endpoint) |
| `POST /auth/logout` | `auth.js`'s `logout()`, wired to every `[data-logout-action]` element by `shell.js` | Requires a currently-valid access token; the in-memory user cache is cleared regardless of whether the call itself succeeds |

**Session architecture:** `core/auth.js` (raw API calls + an in-memory-only
`cachedUser` — never `localStorage`, matching the backend's httpOnly-cookie
design exactly) → `core/session.js` (`bootstrapSession`/`requireAuth`/
`redirectIfAuthenticated`, the recovery/guard logic) → `core/shell.js`
(`initShell()` calls `requireAuth()` first, so every authenticated page is
guarded and topbar-personalized for free, with zero changes needed in six
of the seven page scripts) → `login.js` (calls `redirectIfAuthenticated()`
directly, since it has no shell).

**Error handling:** `api.js` (unchanged from Milestone 1 — reused exactly
as instructed) already turns every non-2xx response into an `ApiError`
carrying the backend's real `status`/`errorCode`/`message`/
`validationErrors`. `login.js` shows `error.message` verbatim in every
failure case (401 invalid credentials, 423 locked account, a network/
timeout error from `api.js` itself) since the backend's message text is
already the right thing to show a user, and maps `validationErrors` onto
specific fields when present. `session.js`'s `bootstrapSession` treats a
401 from `GET /account/me` as "try a silent refresh," and anything else
(network failure) as "can't tell if there's a session" — propagated to
the caller instead of being swallowed as "logged out," so a connectivity
blip never incorrectly bounces a signed-in user to `login.html`.

**What's still static, and why:** dashboard summary cards, recent
activity, and the recent-tickets table. The backend has no
`dashboard`/`summary`/`stats` controller or global activity-feed endpoint
of any kind (confirmed by reading the full backend controller tree) — the
only way to populate them would be calling the ticket-list endpoint and
computing counts client-side, which this milestone explicitly excludes
("Do NOT implement Tickets"). Confirmed with the user before proceeding;
revisit once either a real summary endpoint exists or ticket integration
is in scope.

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
  `data-nav="dashboard.html"` (nav active-highlighting; value is the
  target page's filename), `data-active-nav` on `<body>` (a page that isn't
  itself a nav destination, like ticket-details.html, declares which
  sidebar item should stay highlighted), `data-logout-action` (marks the
  real logout triggers `shell.js` wires to `POST /auth/logout` — as of
  Milestone 6, the one action that's no longer a placeholder),
  `data-placeholder-action="..."` (the element's own message, shown as a
  toast on click — notifications, edit ticket, download/delete attachment,
  extra pagination pages, all still placeholders), `data-filter`/`data-label-prefix`/
  `data-value` (the tickets page's filter dropdowns), and
  `data-status`/`data-priority`/`data-category`/`data-updated`/`data-href`
  (per-row values `tickets.js` filters, sorts, and navigates by).

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

The ticket and users tables (`.table--stack`, opt-in per table) additionally
restructure themselves below 768px: the header row is visually hidden (but
stays in the accessibility tree), and each row becomes a stacked card
where every cell grows a bold label — generated from its own
`data-label` attribute via CSS `content: attr(data-label)`, not
duplicated markup — instead of losing columns to horizontal scrolling.
`ticket-details.html` and `profile.html` both use `.grid-detail` (content
+ sidebar summary), dropping the sidebar below the main content at
1024px, same breakpoint as the app shell's sidebar-to-rail collapse.
`roles.html`'s `.grid-cols-3` role cards follow the existing utility's own
collapse (2 columns at ≤1024px, 1 at ≤768px) with no page-specific work.

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
- The notification bell (and every other non-auth action across the app)
  still uses the placeholder toast pattern via `data-placeholder-action`.
  Logout (both the sidebar link and the user-menu item) is real as of
  Milestone 6 — see Backend Integration above — via the new
  `data-logout-action` attribute, which replaced Milestone 4's
  `data-placeholder-action="Sign-out isn't connected..."` on those same
  two elements across all seven shell pages.
- **Milestone 4 refactor of Milestone 3's `shell.js`**: consolidated the
  bespoke logout/notification toast wiring into the new generic
  `bindPlaceholderActions()` (now living in `utils.js`), and the hand-rolled
  user-menu open/close logic into the new shared `core/dropdown.js`. Done
  specifically because this milestone's brief said "avoid duplication" and
  the alternative was a second, near-identical dropdown/placeholder
  mechanism for the new filter dropdowns and ticket-detail placeholder
  buttons. `dashboard.html`'s markup was updated to match
  (`data-logout-trigger` → `data-placeholder-action="..."`); no other
  Milestone 1-3 behavior changed.
- Ticket domain facts were read from backend source (no backend files
  changed) so placeholder data/copy/validation match reality: ticket
  number format `HD-{year}-{6-digit sequence}` (`TicketServiceImpl`);
  `TicketStatus` = OPEN/ASSIGNED/IN_PROGRESS/WAITING_FOR_CUSTOMER/RESOLVED/
  CLOSED; `TicketPriority` = LOW/MEDIUM/HIGH/URGENT; Category is a real
  seeded entity (Software, Hardware, Network, Email, Accounts, Security,
  Infrastructure, Other), not an enum; `CreateTicketRequest` requires
  title (≤200 chars), description, category, and priority — exactly what
  `create-ticket.html` validates. Comment `visibility` is `PUBLIC`/
  `INTERNAL` (staff-only); attachments cap at 10MB and a fixed MIME
  allow-list (PDF/Word/Excel/ZIP/JPG/PNG), reflected in the upload area's
  hint text and the `<input accept>` attribute.
- Every row on `tickets.html` links to `ticket-details.html`, but since
  there's no data layer yet, all of them land on the same illustrative
  example ticket (HD-2026-000042) rather than a badge/number that doesn't
  match the fixed example content around it — documented here so it isn't
  mistaken for a routing bug.
- Pagination beyond page 1 and the Sort/Filter dropdowns' underlying data
  are chrome, not a real multi-page dataset: filtering/sorting act on the
  8 static rows actually in the DOM, and the pagination summary reflects
  post-filter row counts, but pages 2/3 and "Next" are wired to the same
  placeholder-toast pattern as everything else not yet connected to a
  backend.
- **Milestone 5 refactor**: extracted `setupPasswordToggle` (from three
  near-identical copies in `login.js`/`reset-password.js`/the new
  `profile.js`) and `password-strength-ui.js` (from `reset-password.js`,
  now shared with `profile.js`) into `core/`; extracted `table-filter.js`
  from `tickets.js`'s filter/skeleton/empty-state logic once `users.js`
  needed the identical shape. Also renamed the attachment row's icon
  button class from `.attachment-row__action` to the generic `.icon-btn`
  once the Users table needed the same small icon-button style for
  Edit/Activate/Deactivate — it was never really attachment-specific.
  `ticket-details.html`/`create-ticket.html`'s markup and
  `attachment-preview.js`'s generated markup were updated to match; no
  visual or behavioral change to Milestone 4's pages.
- User/Role domain facts were read from backend source (no backend files
  changed): `User` has a single `name` field (not first/last), `email`,
  `role`, `status` (`UserStatus`: `ACTIVE`/`LOCKED`/`DEACTIVATED`), and
  `emailVerified` — no avatar/phone/department fields exist, so
  `profile.html` doesn't invent any. Self-service profile update
  (`UpdateProfileRequest`) only carries `name` — email is *not*
  self-service-editable, hence `email-input` is `disabled` with an
  explanatory hint rather than a real editable field. `RoleName` is
  `USER`/`SUPPORT_ENGINEER`/`ADMIN`; `Role` is a real entity (not just the
  enum) with a seeded `description` per role (`RoleSeeder`) — the exact
  text `roles.html` uses. All three seeded roles have `system: true`
  (undeletable), reflected in each card's "System role" badge.
- **No formal Permission entity/API exists on the backend** (confirmed:
  the fixed-role RBAC model has no ACL framework) — `roles.html`'s
  "example permissions" and permission badges are illustrative capability
  groupings I derived from the actual `@PreAuthorize` rules across
  `UserController`/`RoleController`/`TicketController`/`CommentController`/
  `AttachmentController`, not a real permissions list a future API call
  would return verbatim. Assigned user counts (1 Admin / 3 Support
  Engineers / 4 Users) match `users.html`'s 8 placeholder rows exactly, for
  narrative consistency between the two pages.
- `users.html` has no user-details page to link to yet, so unlike
  `tickets.html` its rows aren't clickable/navigable — every row action
  (Edit, Activate/Deactivate) is a `data-placeholder-action` toast, mapped
  to the backend's real distinct actions (`UserController`'s update,
  `AccountController`'s activate/deactivate) even though none of them do
  anything yet.
- **Milestone 6**: the login form's "Remember me" checkbox is still
  visual-only — `LoginRequest` has no such field on the backend, so
  there's nothing to send even now that login is real. `dashboard.js` and
  `login.js` use top-level `await` (native to ES modules in every current
  browser) rather than an async-IIFE wrapper, consistent with this
  project's "modern vanilla JS" stack. Verified end-to-end against a real
  running instance of the backend (Spring Boot + in-memory H2, started
  locally for this session only) rather than static assertions alone —
  see the milestone report for the exact requests/responses checked
  (login success/failure/lockout, `/account/me`, refresh with and without
  the CSRF header, logout clearing the session). No backend file was
  modified to make this possible — H2 was substituted for MySQL purely
  via Spring profile/property overrides at the command line.
