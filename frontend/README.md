# HelpDesk Frontend — Foundation, Auth UI, App Shell & Tickets (Phase 4, Milestones 1-4)

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
  tickets.html                   Ticket list: filters, sortable/filterable table, pagination chrome
  ticket-details.html             Full ticket view: description, timeline, comments, attachments, history
  create-ticket.html               New-ticket form: title/description/category/priority/attachments
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
      auth.js                login/logout/refreshToken/getCurrentUser — stubs, not implemented yet
      validation.js           isValidEmail, password requirement/strength rules, match check
      shell.js                Sidebar toggle, nav active-highlighting, user dropdown, search
                                focus animation — the one module every authenticated page's
                                script calls into
      dropdown.js              Generic trigger+menu open/close/outside-click/Escape controller
      filter-dropdown.js       Wires every [data-filter] dropdown into a single-select filter
      attachment-preview.js    Fake (never-uploaded) attachment preview, shared by 2 pages
    pages/
      login.js                     Password toggle, client-side validation, simulated submit
      forgot-password.js            Email validation, simulated send, success-state swap
      reset-password.js              Token check, live requirements/strength, simulated submit
      verification-pending.js        Resend button placeholder with cooldown timer
      dashboard.js                    Just calls core/shell.js's initShell() — no
                                        dashboard-specific behavior in this milestone
      tickets.js                      Skeleton→table swap, filter/sort of static rows,
                                        row-click navigation, empty-state toggling
      ticket-details.js                Comment collapse, visibility segmented control,
                                        attachment upload preview
      create-ticket.js                 Priority chips, character counter, validation,
                                        simulated submit/reset, attachment upload preview
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
   `create-ticket.html` now; profile/admin pages later): the sidebar's dark
   visual skin (brand row, nav links, active-state, tablet icon-rail
   label-hiding), and the topbar's contents (search box + its focus
   animation, notification badge, user menu trigger). A future
   non-auth/non-shell page family gets its own equivalent file rather than
   growing `components.css` with one-off rules.

Notably, **no `tickets.css`/`ticket-details.css` exists** — everything
those three pages needed turned out to be either a genuinely reusable
component (→ `components.css`) or a generic layout primitive (→
`layout.css`), never something specific to "being a ticket page." That's
evidence the split is working as intended rather than a gap.

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
  together), `bindPlaceholderActions` (wires every `[data-placeholder-action]`
  element to show its own message as a toast — the one mechanism behind
  every "not wired to the backend yet" button across the whole app), debounce,
  date formatting, HTML escaping, cookie reading.
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
- `shell.js` — imports `utils.js` (for `bindPlaceholderActions`) and
  `dropdown.js` (for the user menu). `initShell()` is the single entry
  point every authenticated page's script calls: it wires the mobile
  sidebar toggle + overlay backdrop + Escape-to-close, marks the sidebar
  link whose `data-nav` matches the current page (or `<body
  data-active-nav="...">`'s override, for a page like ticket-details.html
  that isn't itself a nav destination) as active, the topbar user dropdown,
  the search box's focus animation, and every placeholder action on the
  page. Contains zero page-specific content.
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
  a list, with a Remove button per row. Nothing is ever sent anywhere —
  shared verbatim by ticket-details.html (adding to an existing ticket)
  and create-ticket.html (attaching while filing a new one).

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
- `tickets.js` — after a simulated ~600ms load (the one place the loading
  skeleton is actually seen, since the table itself is static), listens for
  `filterchange` events to hide/show rows (Status/Priority/Category) or
  reorder them (Sort: newest/oldest/priority), updates the pagination
  summary and empty-state visibility, and turns a click anywhere on a row
  (that isn't already a link/button) into navigation to that row's "View"
  link destination.
- `ticket-details.js` — wires each comment's collapse/expand toggle, the
  comment editor's Public/Internal segmented control, and the attachment
  upload preview (via `core/attachment-preview.js`).
- `create-ticket.js` — the title character counter, the priority
  choice-chip single-select, full validation (title/description/category/
  priority all required, mirroring the backend's `CreateTicketRequest`),
  a simulated submit, and a `reset` handler that also clears the chips,
  attachment previews, and validation state the native form reset doesn't
  know about.

Future pages follow the same pattern: `pages/profile.js`,
`pages/admin-users.js`, `pages/admin-roles.js`, etc. — each one copies the
same shell markup as `dashboard.html` and calls `initShell()` too.

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
  sidebar item should stay highlighted), `data-placeholder-action="..."`
  (the element's own message, shown as a toast on click — logout,
  notifications, edit ticket, download/delete attachment, extra pagination
  pages, all use this one mechanism), `data-filter`/`data-label-prefix`/
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

The ticket table (`.table--stack`, opt-in per table) additionally
restructures itself below 768px: the header row is visually hidden (but
stays in the accessibility tree), and each row becomes a stacked card
where every cell grows a bold label — generated from its own
`data-label` attribute via CSS `content: attr(data-label)`, not
duplicated markup — instead of losing columns to horizontal scrolling.
`ticket-details.html`'s `.grid-detail` (content + sidebar summary) drops
its sidebar below the main content at 1024px, same breakpoint as the app
shell's sidebar-to-rail collapse.

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
  notification bell use the same placeholder toast pattern as everywhere
  else, via `data-placeholder-action` (see Milestone 4 note below — this
  replaced the earlier Milestone 3 `data-logout-trigger` attribute).
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
