/**
 * Generic action-modal controller — one implementation shared by
 * ticket-details.js and users.js instead of two hand-rolled copies of the
 * same open/close/focus-management logic, each of which swaps the modal's
 * title/body/submit-handler per action (edit, assign, change status, ...).
 *
 * Accessibility: moves focus into the modal on open and back to whatever
 * triggered it on close, and marks the rest of the page `inert` while the
 * modal is open so Tab/keyboard users can't reach background content
 * without a full custom focus-trap loop.
 */

/**
 * @param {{
 *   overlay: HTMLElement,
 *   title: HTMLElement,
 *   body: HTMLElement,
 *   form: HTMLFormElement,
 *   cancelButton: HTMLElement,
 *   closeButton: HTMLElement,
 *   inertTarget?: HTMLElement
 * }} elements
 */
export function initModal({ overlay, title, body, form, cancelButton, closeButton, inertTarget }) {
  let onSubmit = null;
  let previouslyFocused = null;

  function open(titleText, bodyHtml, submitHandler) {
    previouslyFocused = document.activeElement;
    title.textContent = titleText;
    body.innerHTML = bodyHtml;
    onSubmit = submitHandler;
    overlay.hidden = false;
    if (inertTarget) {
      inertTarget.inert = true;
    }
    body.querySelector("input, select, textarea")?.focus();
  }

  function close() {
    overlay.hidden = true;
    onSubmit = null;
    form.reset();
    if (inertTarget) {
      inertTarget.inert = false;
    }
    previouslyFocused?.focus();
  }

  closeButton.addEventListener("click", close);
  cancelButton.addEventListener("click", close);

  overlay.addEventListener("click", (event) => {
    if (event.target === overlay) {
      close();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !overlay.hidden) {
      close();
    }
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    await onSubmit?.();
  });

  return {
    open,
    close,
    /** Lets the caller swap in a new submit handler after `open()` (e.g. once an async select's options finish loading). */
    setSubmitHandler: (handler) => {
      onSubmit = handler;
    },
  };
}
