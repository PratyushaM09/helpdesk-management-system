/**
 * Generic open/close controller for a trigger button + a popup menu: the
 * topbar user menu, the tickets page's filter dropdowns, and any future
 * row/context menu all share this instead of re-implementing
 * outside-click/Escape/aria-expanded handling three separate times.
 *
 * Owns no visual state beyond `hidden` and `aria-expanded` — everything
 * else (selection, labels) is the caller's concern.
 */

/**
 * @param {{ trigger: HTMLElement, menu: HTMLElement, onOpen?: Function, onClose?: Function }} options
 * @returns {{ open: Function, close: Function, toggle: Function, isOpen: () => boolean }}
 */
export function initDropdown({ trigger, menu, onOpen, onClose } = {}) {
  if (!trigger || !menu) {
    return { open() {}, close() {}, toggle() {}, isOpen: () => false };
  }

  const open = () => {
    menu.hidden = false;
    trigger.setAttribute("aria-expanded", "true");
    onOpen?.();
  };

  const close = () => {
    menu.hidden = true;
    trigger.setAttribute("aria-expanded", "false");
    onClose?.();
  };

  const toggle = () => (menu.hidden ? open() : close());

  trigger.addEventListener("click", (event) => {
    event.stopPropagation();
    toggle();
  });

  document.addEventListener("click", (event) => {
    if (!menu.hidden && !menu.contains(event.target) && !trigger.contains(event.target)) {
      close();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !menu.hidden) {
      close();
      trigger.focus();
    }
  });

  return { open, close, toggle, isOpen: () => !menu.hidden };
}
