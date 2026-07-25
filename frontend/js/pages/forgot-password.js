/**
 * Page-specific script for forgot-password.html. Client-side validation and
 * a simulated request/response only — no fetch, no backend call. The
 * success copy intentionally matches the backend's anti-enumeration message
 * verbatim (AccountServiceImpl.forgotPassword) so the UI is already
 * accurate once this is wired to the real endpoint.
 */

import { setFieldError, setButtonLoading } from "../core/utils.js";
import { isValidEmail } from "../core/validation.js";

const form = document.getElementById("forgot-password-form");
const emailInput = document.getElementById("email");
const emailError = document.getElementById("email-error");
const submitButton = document.getElementById("forgot-submit");
const requestView = document.getElementById("request-view");
const successView = document.getElementById("success-view");

function validate() {
  if (!emailInput.value.trim()) {
    setFieldError(emailInput, emailError, "Enter your work email.");
    emailInput.focus();
    return false;
  }
  if (!isValidEmail(emailInput.value)) {
    setFieldError(emailInput, emailError, "Enter a valid email address.");
    emailInput.focus();
    return false;
  }
  setFieldError(emailInput, emailError);
  return true;
}

emailInput?.addEventListener("input", () => {
  if (emailInput.getAttribute("aria-invalid") === "true") {
    validate();
  }
});

form?.addEventListener("submit", (event) => {
  event.preventDefault();

  if (!validate()) {
    return;
  }

  setButtonLoading(submitButton, true, "Sending…");
  setTimeout(() => {
    setButtonLoading(submitButton, false);
    requestView.hidden = true;
    successView.hidden = false;
    successView.querySelector(".empty-state__title")?.focus();
  }, 800);
});
