/**
 * Page-specific script for reset-password.html. Client-side validation,
 * live strength/requirements feedback, and a simulated submit only — no
 * fetch, no backend call. The invalid-link copy matches the backend's
 * generic token-error message verbatim (AccountServiceImpl: shared by
 * expired/used/missing tokens by design, so the UI can't leak which case
 * it was either).
 */

import { setFieldError, setButtonLoading } from "../core/utils.js";
import {
  getPasswordRequirementResults,
  getPasswordStrength,
  meetsAllPasswordRequirements,
  passwordsMatch,
} from "../core/validation.js";

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

function setupPasswordToggle(buttonId, input) {
  const button = document.getElementById(buttonId);
  button?.addEventListener("click", () => {
    const isCurrentlyHidden = input.type === "password";
    input.type = isCurrentlyHidden ? "text" : "password";
    const icon = button.querySelector("i");
    icon.className = isCurrentlyHidden ? "bi bi-eye-slash" : "bi bi-eye";
    button.setAttribute("aria-label", isCurrentlyHidden ? "Hide password" : "Show password");
  });
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
  const strengthWrapper = document.getElementById("password-strength");
  const strengthBar = document.getElementById("password-strength-bar");
  const strengthFill = document.getElementById("password-strength-fill");
  const strengthLabel = document.getElementById("password-strength-label");

  setupPasswordToggle("toggle-new-password", newPasswordInput);
  setupPasswordToggle("toggle-confirm-password", confirmPasswordInput);

  function updateRequirements() {
    getPasswordRequirementResults(newPasswordInput.value).forEach((result) => {
      const item = requirementItems.get(result.id);
      if (!item) {
        return;
      }
      item.classList.toggle("is-met", result.met);
      item.querySelector("i").className = result.met ? "bi bi-check-circle-fill" : "bi bi-circle";
    });
  }

  function updateStrength() {
    const strength = getPasswordStrength(newPasswordInput.value);
    strengthWrapper.className = `password-strength ${strength.className}`.trim();
    strengthFill.style.width = `${strength.percent}%`;
    strengthLabel.textContent = strength.label;
    strengthBar.setAttribute("aria-valuenow", String(Math.round(strength.percent)));
    strengthBar.setAttribute("aria-label", strength.label ? `Password strength: ${strength.label}` : "Password strength");
  }

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
    updateRequirements();
    updateStrength();
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
