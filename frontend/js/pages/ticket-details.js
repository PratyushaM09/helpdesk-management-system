/**
 * Page-specific script for ticket-details.html. Every ticket row on
 * tickets.html currently links here — since there's no data layer yet,
 * every one lands on the same illustrative example (HD-2026-000042)
 * rather than a mismatched badge next to fixed example content.
 */

import { initShell } from "../core/shell.js";
import { initAttachmentUpload } from "../core/attachment-preview.js";

initShell();

initAttachmentUpload({
  uploadArea: document.getElementById("attachment-upload"),
  fileInput: document.getElementById("attachment-input"),
  list: document.getElementById("attachment-preview-list"),
});

// Comment collapse — toggles the full body vs. a one-line italic preview,
// with proper ARIA for the expandable region.
document.querySelectorAll(".comment__collapse").forEach((button) => {
  button.addEventListener("click", () => {
    const comment = button.closest(".comment");
    const isCollapsed = comment.classList.toggle("is-collapsed");
    button.setAttribute("aria-expanded", String(!isCollapsed));
  });
});

// Comment visibility segmented control (Public/Internal) — a plain
// radiogroup toggle; nothing is submitted, see the editor's Post Comment
// button's data-placeholder-action.
document.querySelectorAll('.segmented-control[role="radiogroup"]').forEach((group) => {
  const options = Array.from(group.querySelectorAll(".segmented-control__option"));
  options.forEach((option) => {
    option.addEventListener("click", () => {
      options.forEach((candidate) => {
        const isSelected = candidate === option;
        candidate.classList.toggle("is-active", isSelected);
        candidate.setAttribute("aria-checked", String(isSelected));
      });
    });
  });
});
