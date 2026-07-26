/**
 * Page-specific script for profile.html. Two independent forms, both now
 * wired to the real backend. Password rules/UI reuse the exact same core
 * modules as reset-password.html.
 *
 * A successful password change revokes every refresh token for this user —
 * including the current session's own (confirmed by reading
 * AccountServiceImpl.changePassword) — so that form redirects to login on
 * success instead of staying on the page like the profile-name form does.
 */

import { initShell } from "../core/shell.js";
import { showToast, setFieldError, setButtonLoading, setupPasswordToggle, formatDate } from "../core/utils.js";
import { meetsAllPasswordRequirements, passwordsMatch } from "../core/validation.js";
import { initPasswordStrengthUI } from "../core/password-strength-ui.js";
import { updateProfile, changePassword } from "../core/account-api.js";
import { ApiError } from "../core/api.js";

const ROLE_LABELS = {
  ADMIN: { text: "Admin", badgeClass: "badge--brand" },
  SUPPORT_ENGINEER: { text: "Support Engineer", badgeClass: "badge--info" },
  USER: { text: "User", badgeClass: "badge--neutral" },
};

const STATUS_LABELS = {
  ACTIVE: { text: "Active", badgeClass: "badge--success" },
  LOCKED: { text: "Locked", badgeClass: "badge--danger" },
  DEACTIVATED: { text: "Deactivated", badgeClass: "badge--neutral" },
};

const currentUser = await initShell();
if (currentUser) {
  init(currentUser);
}

function init(user) {
  applyProfileToPage(user);

  // ---------- Personal information ----------
  const profileForm = document.getElementById("profile-form");
  const nameInput = document.getElementById("name-input");
  const nameError = document.getElementById("name-error");
  const profileSubmit = document.getElementById("profile-submit");
  let isSavingProfile = false;

  nameInput.addEventListener("input", () => {
    if (nameInput.getAttribute("aria-invalid") === "true" && nameInput.value.trim()) {
      setFieldError(nameInput, nameError);
    }
  });

  profileForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (isSavingProfile) {
      return;
    }

    const name = nameInput.value.trim();
    if (!name) {
      setFieldError(nameInput, nameError, "Enter your full name.");
      nameInput.focus();
      return;
    }
    setFieldError(nameInput, nameError);

    isSavingProfile = true;
    setButtonLoading(profileSubmit, true, "Saving…");
    try {
      const updated = await updateProfile({ name });
      applyProfileToPage(updated);
      showToast("Profile updated.", "success");
    } catch (error) {
      if (error instanceof ApiError && error.validationErrors?.length) {
        error.validationErrors.forEach(({ field, message }) => {
          if (field === "name") {
            setFieldError(nameInput, nameError, message);
          }
        });
      } else {
        showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
      }
    } finally {
      isSavingProfile = false;
      setButtonLoading(profileSubmit, false);
    }
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
  let isChangingPassword = false;

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

  passwordForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (isChangingPassword || !validatePasswordForm()) {
      return;
    }

    isChangingPassword = true;
    setButtonLoading(passwordSubmit, true, "Updating…");
    try {
      await changePassword({
        currentPassword: currentPasswordInput.value,
        newPassword: newPasswordInput.value,
        confirmPassword: confirmPasswordInput.value,
      });
      showToast("Password updated. Please sign in again with your new password.", "success");
      window.location.href = "login.html";
    } catch (error) {
      isChangingPassword = false;
      setButtonLoading(passwordSubmit, false);

      if (error instanceof ApiError && error.validationErrors?.length) {
        error.validationErrors.forEach(({ field, message }) => {
          if (field === "currentPassword") {
            setFieldError(currentPasswordInput, currentPasswordError, message);
          } else if (field === "newPassword") {
            setFieldError(newPasswordInput, newPasswordError, message);
          } else if (field === "confirmPassword") {
            setFieldError(confirmPasswordInput, confirmPasswordError, message);
          }
        });
      } else if (error instanceof ApiError && error.status === 400) {
        // Wrong current password / mismatch / same-as-current all arrive as
        // a plain 400 with a human-readable message, not per-field errors —
        // AccountServiceImpl.changePassword checks them in that order.
        setFieldError(currentPasswordInput, currentPasswordError, error.message);
      } else {
        showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
      }
    }
  });
}

function applyProfileToPage(profile) {
  document.getElementById("name-input").value = profile.name;
  document.getElementById("email-input").value = profile.email;

  const roleMeta = ROLE_LABELS[profile.role] ?? { text: profile.role, badgeClass: "badge--neutral" };
  document.getElementById("profile-role-value").innerHTML = `<span class="badge ${roleMeta.badgeClass}">${roleMeta.text}</span>`;

  const statusMeta = STATUS_LABELS[profile.status] ?? { text: profile.status, badgeClass: "badge--neutral" };
  document.getElementById("profile-status-value").innerHTML = `<span class="badge ${statusMeta.badgeClass}">${statusMeta.text}</span>`;

  document.getElementById("profile-verified-value").innerHTML = profile.emailVerified
    ? '<span class="badge badge--success"><i class="bi bi-patch-check-fill" aria-hidden="true"></i> Verified</span>'
    : '<span class="badge badge--neutral"><i class="bi bi-patch-exclamation" aria-hidden="true"></i> Unverified</span>';

  document.getElementById("profile-created-value").textContent = formatDate(profile.createdAt);
}
