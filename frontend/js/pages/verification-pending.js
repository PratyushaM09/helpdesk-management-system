/**
 * Page-specific script for verification-pending.html. The resend button is
 * a placeholder — it simulates a request and a 30-second cooldown, but the
 * backend's actual /account/resend-verification endpoint requires an
 * authenticated session and takes no email in its body (it always resends
 * to the caller's own address), which the real implementation must respect.
 */

import { showToast, setButtonLoading } from "../core/utils.js";

const resendButton = document.getElementById("resend-button");
const COOLDOWN_SECONDS = 30;

resendButton?.addEventListener("click", () => {
  setButtonLoading(resendButton, true, "Sending…");
  setTimeout(() => {
    setButtonLoading(resendButton, false);
    showToast("Verification email sent. Check your inbox.", "success");
    startCooldown();
  }, 800);
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
