/**
 * Wires a password input to the requirements checklist + strength meter
 * markup pattern used on both reset-password.html and profile.html's
 * change-password section — one implementation instead of two near-copies.
 */

import { getPasswordRequirementResults, getPasswordStrength } from "./validation.js";

/**
 * @param {{
 *   passwordInput: HTMLInputElement,
 *   strengthWrapper: HTMLElement,
 *   strengthBar: HTMLElement,
 *   strengthFill: HTMLElement,
 *   strengthLabel: HTMLElement,
 *   requirementItems: Map<string, HTMLElement>
 * }} elements
 * @returns {{ update: Function }}
 */
export function initPasswordStrengthUI({
  passwordInput,
  strengthWrapper,
  strengthBar,
  strengthFill,
  strengthLabel,
  requirementItems,
}) {
  function update() {
    getPasswordRequirementResults(passwordInput.value).forEach((result) => {
      const item = requirementItems.get(result.id);
      if (!item) {
        return;
      }
      item.classList.toggle("is-met", result.met);
      item.querySelector("i").className = result.met ? "bi bi-check-circle-fill" : "bi bi-circle";
    });

    const strength = getPasswordStrength(passwordInput.value);
    strengthWrapper.className = `password-strength ${strength.className}`.trim();
    strengthFill.style.width = `${strength.percent}%`;
    strengthLabel.textContent = strength.label;
    strengthBar.setAttribute("aria-valuenow", String(Math.round(strength.percent)));
    strengthBar.setAttribute(
      "aria-label",
      strength.label ? `Password strength: ${strength.label}` : "Password strength"
    );
  }

  passwordInput.addEventListener("input", update);
  return { update };
}
