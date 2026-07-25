/**
 * Page-specific script for create-ticket.html. Client-side validation and a
 * simulated submit only — no fetch, no backend call. Field rules mirror the
 * backend's real CreateTicketRequest (title required, max 200; description
 * required; category and priority required) so the UI is already accurate
 * whenever this gets wired up.
 */

import { initShell } from "../core/shell.js";
import { initAttachmentUpload } from "../core/attachment-preview.js";
import { showToast, setFieldError, setButtonLoading } from "../core/utils.js";

initShell();

const form = document.getElementById("create-ticket-form");
const titleInput = document.getElementById("title-input");
const titleError = document.getElementById("title-error");
const titleCount = document.getElementById("title-count");
const descriptionInput = document.getElementById("description-input");
const descriptionError = document.getElementById("description-error");
const categorySelect = document.getElementById("category-select");
const categoryError = document.getElementById("category-error");
const priorityError = document.getElementById("priority-error");
const priorityChips = Array.from(document.querySelectorAll(".choice-chip[data-priority]"));
const attachmentList = document.getElementById("attachment-preview-list");
const submitButton = document.getElementById("create-ticket-submit");

let selectedPriority = "";

initAttachmentUpload({
  uploadArea: document.getElementById("attachment-upload"),
  fileInput: document.getElementById("attachment-input"),
  list: attachmentList,
});

function updateTitleCount() {
  titleCount.textContent = `${titleInput.value.length} / 200`;
}

titleInput.addEventListener("input", () => {
  updateTitleCount();
  if (titleInput.getAttribute("aria-invalid") === "true" && titleInput.value.trim()) {
    setFieldError(titleInput, titleError);
  }
});

priorityChips.forEach((chip) => {
  chip.addEventListener("click", () => {
    selectedPriority = chip.dataset.priority;
    priorityChips.forEach((candidate) => {
      const isSelected = candidate === chip;
      candidate.classList.toggle("is-selected", isSelected);
      candidate.setAttribute("aria-checked", String(isSelected));
    });
    setFieldError(undefined, priorityError);
  });
});

function validate() {
  let firstInvalid = null;

  if (!titleInput.value.trim()) {
    setFieldError(titleInput, titleError, "Enter a title for this ticket.");
    firstInvalid ??= titleInput;
  } else {
    setFieldError(titleInput, titleError);
  }

  if (!descriptionInput.value.trim()) {
    setFieldError(descriptionInput, descriptionError, "Describe the issue.");
    firstInvalid ??= descriptionInput;
  } else {
    setFieldError(descriptionInput, descriptionError);
  }

  if (!categorySelect.value) {
    setFieldError(categorySelect, categoryError, "Select a category.");
    firstInvalid ??= categorySelect;
  } else {
    setFieldError(categorySelect, categoryError);
  }

  if (!selectedPriority) {
    setFieldError(undefined, priorityError, "Select a priority.");
    firstInvalid ??= priorityChips[0];
  } else {
    setFieldError(undefined, priorityError);
  }

  firstInvalid?.focus();
  return firstInvalid === null;
}

function resetCustomState() {
  selectedPriority = "";
  priorityChips.forEach((chip) => {
    chip.classList.remove("is-selected");
    chip.setAttribute("aria-checked", "false");
  });
  attachmentList.replaceChildren();
  updateTitleCount();
  [titleError, descriptionError, categoryError, priorityError].forEach((errorEl) => errorEl && (errorEl.hidden = true));
  [titleInput, descriptionInput, categorySelect].forEach((input) => input.removeAttribute("aria-invalid"));
}

form.addEventListener("reset", () => {
  // Fires after the browser has already reset standard field values —
  // this clears the custom bits it doesn't know about (chips, attachment
  // previews, errors). Also fires from the successful-submit path below,
  // since form.reset() dispatches the same event.
  resetCustomState();
});

form.addEventListener("submit", (event) => {
  event.preventDefault();

  if (!validate()) {
    return;
  }

  setButtonLoading(submitButton, true, "Creating ticket…");
  setTimeout(() => {
    setButtonLoading(submitButton, false);
    showToast("Ticket created (placeholder) — this isn't wired up to the backend yet.", "success");
    form.reset();
  }, 900);
});
