/**
 * Page-specific script for login.html. UI wiring only — password-visibility
 * toggle and a placeholder submit handler. Real authentication is wired in
 * the milestone that implements auth.js.
 */

import { showToast } from "../core/utils.js";

const passwordInput = document.getElementById("password");
const toggleButton = document.getElementById("toggle-password");
const loginForm = document.getElementById("login-form");

toggleButton?.addEventListener("click", () => {
  const isCurrentlyHidden = passwordInput.type === "password";
  passwordInput.type = isCurrentlyHidden ? "text" : "password";

  const icon = toggleButton.querySelector("i");
  icon.className = isCurrentlyHidden ? "bi bi-eye-slash" : "bi bi-eye";
  toggleButton.setAttribute("aria-label", isCurrentlyHidden ? "Hide password" : "Show password");
});

loginForm?.addEventListener("submit", (event) => {
  event.preventDefault();
  showToast("Sign-in isn't connected yet — this is the Milestone 1 UI foundation.", "info");
});
