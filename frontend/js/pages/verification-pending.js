/**
 * Page-specific script for verification-pending.html. Resend calls the real
 * POST /account/resend-verification, which requires an authenticated
 * session and always targets the caller's own address (no email in the
 * request body). This page has no shell/requireAuth() of its own (it's
 * reached mid-login, before the "is this account verified" branch even
 * matters for routing elsewhere), so primeCsrfToken() runs here directly
 * rather than via session.js's bootstrapSession.
 */

import { showToast, setButtonLoading } from "../core/utils.js";
import { resendVerification } from "../core/account-api.js";
import { primeCsrfToken } from "../core/auth.js";
import { ApiError } from "../core/api.js";

const resendButton = document.getElementById("resend-button");
const COOLDOWN_SECONDS = 30;

resendButton?.addEventListener("click", async () => {
  setButtonLoading(resendButton, true, "Sending…");
  try {
    await primeCsrfToken();
    await resendVerification();
    showToast("Verification email sent. Check your inbox.", "success");
    startCooldown();
  } catch (error) {
    setButtonLoading(resendButton, false);
    if (error instanceof ApiError && error.status === 401) {
      showToast("Your session has expired. Please sign in again.", "danger");
    } else {
      showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
    }
  }
});

function startCooldown() {
  const originalHtml = resendButton.innerHTML;
  resendButton.disabled = true;
  let remainingSeconds = COOLDOWN_SECONDS;

  const render = () => {
    const minutes = Math.floor(remainingSeconds / 60);
    const seconds = String(remainingSeconds % 60).padStart(2, "0");
    resendButton.textContent = `Resend available in ${minutes}:${seconds}`;
  };

  render();
  const intervalId = setInterval(() => {
    remainingSeconds -= 1;
    if (remainingSeconds <= 0) {
      clearInterval(intervalId);
      resendButton.innerHTML = originalHtml;
      resendButton.disabled = false;
      return;
    }
    render();
  }, 1000);
}
