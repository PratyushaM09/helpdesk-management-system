/**
 * Page-specific script for login.html. UI wiring only — password-visibility
 * toggle, client-side validation, and a simulated submit. Real
 * authentication is wired in the milestone that implements auth.js.
 */

import { showToast, setFieldError, setButtonLoading } from "../core/utils.js";
import { isValidEmail } from "../core/validation.js";

const loginForm = document.getElementById("login-form");
const emailInput = document.getElementById("email");
const emailError = document.getElementById("email-error");
const passwordInput = document.getElementById("password");
const passwordError = document.getElementById("password-error");
const toggleButton = document.getElementById("toggle-password");
const submitButton = document.getElementById("login-submit");

toggleButton?.addEventListener("click", () => {
  const isCurrentlyHidden = passwordInput.type === "password";
  passwordInput.type = isCurrentlyHidden ? "text" : "password";

  const icon = toggleButton.querySelector("i");
  icon.className = isCurrentlyHidden ? "bi bi-eye-slash" : "bi bi-eye";
  toggleButton.setAttribute("aria-label", isCurrentlyHidden ? "Hide password" : "Show password");
});

function validate() {
  let firstInvalid = null;

  if (!emailInput.value.trim()) {
    setFieldError(emailInput, emailError, "Enter your work email.");
    firstInvalid ??= emailInput;
  } else if (!isValidEmail(emailInput.value)) {
    setFieldError(emailInput, emailError, "Enter a valid email address.");
    firstInvalid ??= emailInput;
  } else {
    setFieldError(emailInput, emailError);
  }

  if (!passwordInput.value) {
    setFieldError(passwordInput, passwordError, "Enter your password.");
    firstInvalid ??= passwordInput;
  } else {
    setFieldError(passwordInput, passwordError);
  }

  firstInvalid?.focus();
  return firstInvalid === null;
}

[emailInput, passwordInput].forEach((input) => {
  input?.addEventListener("input", () => {
    if (input.getAttribute("aria-invalid") === "true") {
      validate();
    }
  });
});

loginForm?.addEventListener("submit", (event) => {
  event.preventDefault();

  if (!validate()) {
    return;
  }

  setButtonLoading(submitButton, true, "Signing in…");
  setTimeout(() => {
    setButtonLoading(submitButton, false);
    showToast("Sign-in isn't connected yet — this is the Milestone 2 UI.", "info");
  }, 900);
});
