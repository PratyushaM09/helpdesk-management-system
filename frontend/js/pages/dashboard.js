/**
 * Page-specific script for dashboard.html. core/shell.js's initShell()
 * already guards the page (redirects guests to login.html) and populates
 * the topbar with the real signed-in user; this only adds the
 * dashboard-specific personalization on top: a "Welcome back" greeting and
 * an email-verification reminder banner (an unverified-but-active account
 * can still reach the dashboard on this backend — see AccountProfileResponse
 * .emailVerified — so this is a nudge, not a redirect).
 *
 * The summary cards, recent-activity feed, and recent-tickets table stay
 * static placeholder markup: the backend has no dashboard-summary or
 * activity-feed endpoint (confirmed by reading every controller), and
 * deriving one from the ticket-list endpoint is out of scope for this
 * milestone (Tickets integration is explicitly excluded).
 */

import { initShell } from "../core/shell.js";

const user = await initShell();

if (user) {
  const subtitle = document.getElementById("dashboard-subtitle");
  if (subtitle) {
    const firstName = user.name.trim().split(/\s+/)[0];
    subtitle.textContent = `Welcome back, ${firstName}. Here's what's happening across your HelpDesk today.`;
  }

  const unverifiedBanner = document.getElementById("unverified-banner");
  if (unverifiedBanner) {
    unverifiedBanner.hidden = user.emailVerified !== false;
  }
}
