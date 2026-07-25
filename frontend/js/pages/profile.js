/**
 * Page-specific script for profile.html. Two independent forms, both
 * client-side validation + simulated submit only — no fetch, no backend
 * call. Password rules/UI reuse the exact same core modules as
 * reset-password.html.
 */

import { initShell } from "../core/shell.js";
import { showToast, setFieldError, setButtonLoading, setupPasswordToggle } from "../core/utils.js";
import { meetsAllPasswordRequirements, passwordsMatch } from "../core/validation.js";
import { initPasswordStrengthUI } from "../core/password-strength-ui.js";

initShell();

// ---------- Personal information ----------
const profileForm = document.getElementById("profile-form");
const nameInput = document.getElementById("name-input");
const nameError = document.getElementById("name-error");
const profileSubmit = document.getElementById("profile-submit");

nameInput.addEventListener("input", () => {
  if (nameInput.getAttribute("aria-invalid") === "true" && nameInput.value.trim()) {
    setFieldError(nameInput, nameError);
  }
});

profileForm.addEventListener("submit", (event) => {
  event.preventDefault();

  if (!nameInput.value.trim()) {
    setFieldError(nameInput, nameError, "Enter your full name.");
    nameInput.focus();
    return;
  }
  setFieldError(nameInput, nameError);

  setButtonLoading(profileSubmit, true, "Saving…");
  setTimeout(() => {
    setButtonLoading(profileSubmit, false);
    showToast("Profile updated (placeholder) — this isn't wired up to the backend yet.", "success");
  }, 700);
});

// ---------- Change password ----------
const passwordForm = document.getElementById("password-form");
const currentPasswordInput = document.getElementById("current-password-input");
const currentPasswordError = document.getElementById("current-password-error");
const newPasswordInput = document.getElementById("new-password-input");
const newPasswordError = document.getElementById("new-password-error");
const confirmPasswordInput = document.getElementById("confirm-password-input");
const confirmPasswordError = document.getElementById("confirm-password-error");
const passwordSubmit = document.getElementById("password-submit");

setupPasswordToggle(document.getElementById("toggle-current-password"), currentPasswordInput);
setupPasswordToggle(document.getElementById("toggle-new-password"), newPasswordInput);
setupPasswordToggle(document.getElementById("toggle-confirm-password"), confirmPasswordInput);

const requirementItems = new Map(
  Array.from(document.querySelectorAll("[data-requirement]")).map((el) => [el.dataset.requirement, el])
);

const strengthUI = initPasswordStrengthUI({
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

function validatePasswordForm() {
  let firstInvalid = null;

  if (!currentPasswordInput.value) {
    setFieldError(currentPasswordInput, currentPasswordError, "Enter your current password.");
    firstInvalid ??= currentPasswordInput;
  } else {
    setFieldError(currentPasswordInput, currentPasswordError);
  }

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

passwordForm.addEventListener("submit", (event) => {
  event.preventDefault();

  if (!validatePasswordForm()) {
    return;
  }

  setButtonLoading(passwordSubmit, true, "Updating…");
  setTimeout(() => {
    setButtonLoading(passwordSubmit, false);
    showToast("Password updated (placeholder) — this isn't wired up to the backend yet.", "success");
    passwordForm.reset();
    strengthUI.update();
  }, 900);
});
