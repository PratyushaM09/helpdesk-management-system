/**
 * Page-specific script for reset-password.html. Client-side validation,
 * live strength/requirements feedback, and a simulated submit only — no
 * fetch, no backend call. The invalid-link copy matches the backend's
 * generic token-error message verbatim (AccountServiceImpl: shared by
 * expired/used/missing tokens by design, so the UI can't leak which case
 * it was either).
 */

import { setFieldError, setButtonLoading, setupPasswordToggle } from "../core/utils.js";
import { meetsAllPasswordRequirements, passwordsMatch } from "../core/validation.js";
import { initPasswordStrengthUI } from "../core/password-strength-ui.js";
import { redirectIfAuthenticated } from "../core/session.js";

// An already-authenticated visitor has Profile's real change-password
// instead — consistent with every other pre-login auth page.
redirectIfAuthenticated();

const resetView = document.getElementById("reset-view");
const invalidView = document.getElementById("invalid-view");
const successView = document.getElementById("success-view");

const token = new URLSearchParams(window.location.search).get("token");

if (!token) {
  resetView.hidden = true;
  invalidView.hidden = false;
} else {
  initResetForm();
}

function initResetForm() {
  const form = document.getElementById("reset-password-form");
  const newPasswordInput = document.getElementById("new-password");
  const newPasswordError = document.getElementById("new-password-error");
  const confirmPasswordInput = document.getElementById("confirm-password");
  const confirmPasswordError = document.getElementById("confirm-password-error");
  const submitButton = document.getElementById("reset-submit");

  const requirementItems = new Map(
    Array.from(document.querySelectorAll("[data-requirement]")).map((el) => [el.dataset.requirement, el])
  );

  setupPasswordToggle(document.getElementById("toggle-new-password"), newPasswordInput);
  setupPasswordToggle(document.getElementById("toggle-confirm-password"), confirmPasswordInput);

  initPasswordStrengthUI({
    passwordInput: newPasswordInput,
    strengthWrapper: document.getElementById("password-strength"),
    strengthBar: document.getElementById("password-strength-bar"),
    strengthFill: document.getElementById("password-strength-fill"),
    strengthLabel: document.getElementById("password-strength-label"),
    requirementItems,
  });

  function updateMatch() {
    if (!confirmPasswordInput.value) {
      setFieldError(confirmPasswordInput, confirmPasswordError);
      return;
    }
    setFieldError(
      confirmPasswordInput,
      confirmPasswordError,
      passwordsMatch(newPasswordInput.value, confirmPasswordInput.value) ? "" : "Passwords do not match."
    );
  }

  newPasswordInput.addEventListener("input", () => {
    if (confirmPasswordInput.value) {
      updateMatch();
    }
    if (newPasswordInput.getAttribute("aria-invalid") === "true" && meetsAllPasswordRequirements(newPasswordInput.value)) {
      setFieldError(newPasswordInput, newPasswordError);
    }
  });

  confirmPasswordInput.addEventListener("input", updateMatch);

  function validate() {
    let firstInvalid = null;

    if (!meetsAllPasswordRequirements(newPasswordInput.value)) {
      setFieldError(newPasswordInput, newPasswordError, "Password does not meet all requirements below.");
      firstInvalid ??= newPasswordInput;
    } else {
      setFieldError(newPasswordInput, newPasswordError);
    }

    if (!passwordsMatch(newPasswordInput.value, confirmPasswordInput.value)) {
      setFieldError(confirmPasswordInput, confirmPasswordError, "Passwords do not match.");
      firstInvalid ??= confirmPasswordInput;
    } else {
      setFieldError(confirmPasswordInput, confirmPasswordError);
    }

    firstInvalid?.focus();
    return firstInvalid === null;
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();

    if (!validate()) {
      return;
    }

    setButtonLoading(submitButton, true, "Resetting password…");
    setTimeout(() => {
      setButtonLoading(submitButton, false);
      resetView.hidden = true;
      successView.hidden = false;
      successView.querySelector(".empty-state__title")?.focus();
    }, 900);
  });
}
