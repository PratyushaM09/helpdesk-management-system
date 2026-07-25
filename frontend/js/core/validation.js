/**
 * Client-side validation rules shared by every auth page. Deliberately
 * mirrors the backend's real constraints (StrongPasswordValidator: 10-128
 * chars, upper/lower/digit/symbol) so a password that passes here also
 * passes once these pages call the real API — but nothing here talks to
 * the network, it only judges strings already in the DOM.
 */

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * @param {string} value
 * @returns {boolean}
 */
export function isValidEmail(value) {
  return EMAIL_PATTERN.test(String(value ?? "").trim());
}

export const PASSWORD_RULES = [
  { id: "length", label: "10-128 characters", test: (p) => p.length >= 10 && p.length <= 128 },
  { id: "uppercase", label: "One uppercase letter (A-Z)", test: (p) => /[A-Z]/.test(p) },
  { id: "lowercase", label: "One lowercase letter (a-z)", test: (p) => /[a-z]/.test(p) },
  { id: "digit", label: "One number (0-9)", test: (p) => /[0-9]/.test(p) },
  { id: "special", label: "One special character (e.g. ! @ # $)", test: (p) => /[^A-Za-z0-9]/.test(p) },
];

/**
 * @param {string} password
 * @returns {{id: string, label: string, met: boolean}[]}
 */
export function getPasswordRequirementResults(password) {
  const value = password ?? "";
  return PASSWORD_RULES.map((rule) => ({ id: rule.id, label: rule.label, met: rule.test(value) }));
}

/**
 * @param {string} password
 * @returns {boolean} true only if every rule in {@link PASSWORD_RULES} passes
 */
export function meetsAllPasswordRequirements(password) {
  return getPasswordRequirementResults(password).every((result) => result.met);
}

const STRENGTH_LEVELS = [
  { minMet: 0, label: "Very weak", className: "password-strength--very-weak" },
  { minMet: 2, label: "Weak", className: "password-strength--weak" },
  { minMet: 3, label: "Fair", className: "password-strength--fair" },
  { minMet: 4, label: "Good", className: "password-strength--good" },
  { minMet: 5, label: "Strong", className: "password-strength--strong" },
];

/**
 * @param {string} password
 * @returns {{metCount: number, percent: number, label: string, className: string}}
 */
export function getPasswordStrength(password) {
  const value = password ?? "";
  if (!value) {
    return { metCount: 0, percent: 0, label: "", className: "" };
  }
  const metCount = PASSWORD_RULES.reduce((count, rule) => count + (rule.test(value) ? 1 : 0), 0);
  const level = [...STRENGTH_LEVELS].reverse().find((candidate) => metCount >= candidate.minMet);
  return {
    metCount,
    percent: (metCount / PASSWORD_RULES.length) * 100,
    label: level.label,
    className: level.className,
  };
}

/**
 * @param {string} password
 * @param {string} confirmPassword
 * @returns {boolean}
 */
export function passwordsMatch(password, confirmPassword) {
  return password.length > 0 && password === confirmPassword;
}
