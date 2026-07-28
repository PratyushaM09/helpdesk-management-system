/**
 * Page-specific script for forgot-password.html. The page is a static
 * "coming soon" notice (self-service password reset isn't built yet) -
 * this only guards against an already-authenticated visitor landing here,
 * same as every other pre-login auth page.
 */

import { redirectIfAuthenticated } from "../core/session.js";

redirectIfAuthenticated();
