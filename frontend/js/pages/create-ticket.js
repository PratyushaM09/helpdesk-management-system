/**
 * Page-specific script for create-ticket.html. Real POST /tickets, real
 * GET /categories (cached), and a real staged-attachment upload sequence:
 * since the backend has no "create ticket with attachments in one request"
 * endpoint, files are staged as preview rows and uploaded one by one to the
 * new ticket's id only after creation succeeds, before redirecting.
 *
 * Ticket creation is USER-only on the backend (`@PreAuthorize("hasRole('USER')")`)
 * — a Support Engineer or Admin account gets a real 403 attempting this, so
 * the form is disabled with an explanatory notice for those roles rather
 * than letting them hit a confusing dead end.
 */

import { initShell } from "../core/shell.js";
import { initStagedAttachmentUpload } from "../core/attachment-preview.js";
import { showToast, setFieldError, setButtonLoading } from "../core/utils.js";
import { createTicket, getCategories } from "../core/tickets-api.js";
import { uploadTicketAttachment } from "../core/attachments-api.js";
import { ApiError } from "../core/api.js";

const user = await initShell();
if (user) {
  init(user);
}

async function init(user) {
  const form = document.getElementById("create-ticket-form");
  const roleNotice = document.getElementById("wrong-role-notice");
  const titleInput = document.getElementById("title-input");
  const titleError = document.getElementById("title-error");
  const titleCount = document.getElementById("title-count");
  const descriptionInput = document.getElementById("description-input");
  const descriptionError = document.getElementById("description-error");
  const categorySelect = document.getElementById("category-select");
  const categoryError = document.getElementById("category-error");
  const priorityError = document.getElementById("priority-error");
  const priorityChips = Array.from(document.querySelectorAll(".choice-chip[data-priority]"));
  const submitButton = document.getElementById("create-ticket-submit");

  if (user.role !== "USER") {
    if (roleNotice) {
      roleNotice.hidden = false;
    }
    form.querySelectorAll("input, textarea, select, button").forEach((el) => (el.disabled = true));
    return;
  }

  let selectedPriority = "";
  let isSubmitting = false;

  const attachmentStaging = initStagedAttachmentUpload({
    uploadArea: document.getElementById("attachment-upload"),
    fileInput: document.getElementById("attachment-input"),
    list: document.getElementById("attachment-preview-list"),
  });

  // Categories: fetched once, cached in core/tickets-api.js.
  try {
    const categories = await getCategories();
    categorySelect.innerHTML = '<option value="" disabled selected>Select a category</option>';
    categories.forEach((category) => {
      const option = document.createElement("option");
      option.value = String(category.id);
      option.textContent = category.name;
      categorySelect.appendChild(option);
    });
    categorySelect.disabled = false;
  } catch {
    categorySelect.innerHTML = '<option value="" disabled selected>Couldn\'t load categories</option>';
    showToast("Couldn't load categories. Refresh the page to try again.", "danger");
  }

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
    attachmentStaging.clear();
    updateTitleCount();
    [titleError, descriptionError, categoryError, priorityError].forEach((errorEl) => errorEl && (errorEl.hidden = true));
    [titleInput, descriptionInput, categorySelect].forEach((input) => input.removeAttribute("aria-invalid"));
  }

  form.addEventListener("reset", () => {
    resetCustomState();
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (isSubmitting || !validate()) {
      return;
    }

    isSubmitting = true;
    setButtonLoading(submitButton, true, "Creating ticket…");

    try {
      const ticket = await createTicket({
        title: titleInput.value.trim(),
        description: descriptionInput.value.trim(),
        categoryId: Number(categorySelect.value),
        priority: selectedPriority,
      });

      const stagedFiles = attachmentStaging.getFiles();
      for (const file of stagedFiles) {
        try {
          await uploadTicketAttachment(ticket.id, file);
        } catch {
          showToast(`Ticket created, but "${file.name}" failed to attach.`, "warning");
        }
      }

      showToast("Ticket created.", "success");
      window.location.href = `ticket-details.html?id=${ticket.id}`;
    } catch (error) {
      isSubmitting = false;
      setButtonLoading(submitButton, false);

      if (error instanceof ApiError && error.validationErrors?.length) {
        error.validationErrors.forEach(({ field, message }) => {
          if (field === "title") {
            setFieldError(titleInput, titleError, message);
          } else if (field === "description") {
            setFieldError(descriptionInput, descriptionError, message);
          } else if (field === "categoryId") {
            setFieldError(categorySelect, categoryError, message);
          } else if (field === "priority") {
            setFieldError(undefined, priorityError, message);
          }
        });
        return;
      }

      showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
    }
  });
}
