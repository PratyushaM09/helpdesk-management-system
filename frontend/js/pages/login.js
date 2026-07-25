/**
 * Page-specific script for login.html. Real authentication: POST
 * /auth/login via core/auth.js, backend ErrorResponse messages surfaced
 * verbatim, and a post-login redirect based on the account's real
 * verification status (GET /account/me) — an unverified-but-active account
 * logs in successfully on the backend, so "handling" that case means
 * routing to verification-pending.html instead of the dashboard, not
 * blocking sign-in.
 */

import { setFieldError, setButtonLoading, setupPasswordToggle } from "../core/utils.js";
import { isValidEmail } from "../core/validation.js";
import { login, getCurrentUser } from "../core/auth.js";
import { redirectIfAuthenticated } from "../core/session.js";
import { ApiError } from "../core/api.js";

redirectIfAuthenticated();

const loginForm = document.getElementById("login-form");
const emailInput = document.getElementById("email");
const emailError = document.getElementById("email-error");
const passwordInput = document.getElementById("password");
const passwordError = document.getElementById("password-error");
const toggleButton = document.getElementById("toggle-password");
const submitButton = document.getElementById("login-submit");
const errorAlert = document.getElementById("login-error-alert");
const errorMessage = document.getElementById("login-error-message");

setupPasswordToggle(toggleButton, passwordInput);

function showLoginError(message) {
  errorMessage.textContent = message;
  errorAlert.hidden = false;
}

function hideLoginError() {
  errorAlert.hidden = true;
}

const FIELD_ERROR_TARGETS = {
  email: [emailInput, emailError],
  password: [passwordInput, passwordError],
};

/** @returns {boolean} true if any backend validation errors were field-mappable */
function applyBackendValidationErrors(validationErrors) {
  let firstInvalid = null;
  validationErrors.forEach(({ field, message }) => {
    const target = FIELD_ERROR_TARGETS[field];
    if (target) {
      setFieldError(target[0], target[1], message);
      firstInvalid ??= target[0];
    }
  });
  firstInvalid?.focus();
  return firstInvalid !== null;
}

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
    hideLoginError();
    if (input.getAttribute("aria-invalid") === "true") {
      validate();
    }
  });
});

loginForm?.addEventListener("submit", async (event) => {
  event.preventDefault();
  hideLoginError();

  if (!validate()) {
    return;
  }

  setButtonLoading(submitButton, true, "Signing in…");

  try {
    await login({ email: emailInput.value.trim(), password: passwordInput.value });
    const profile = await getCurrentUser({ forceRefetch: true });
    window.location.href = profile.emailVerified ? "dashboard.html" : "verification-pending.html";
  } catch (error) {
    setButtonLoading(submitButton, false);
    passwordInput.value = "";

    if (error instanceof ApiError && error.validationErrors?.length) {
      applyBackendValidationErrors(error.validationErrors);
      return;
    }

    showLoginError(error instanceof ApiError ? error.message : "Something went wrong. Please try again.");
    passwordInput.focus();
  }
});
