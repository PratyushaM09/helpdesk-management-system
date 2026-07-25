/**
 * Fake attachment upload preview — shared by ticket-details.html (adding an
 * attachment to an existing ticket) and create-ticket.html (attaching files
 * while filing a new one). Reads real File objects the user picked/dropped
 * so the preview (name, size, icon) is genuine, but nothing is ever sent
 * anywhere: there is no fetch, no XHR, no storage — "Remove" just deletes
 * the preview row.
 */

import { escapeHtml } from "./utils.js";

const FILE_ICONS = {
  "application/pdf": "bi-file-earmark-pdf",
  "application/msword": "bi-file-earmark-word",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "bi-file-earmark-word",
  "application/vnd.ms-excel": "bi-file-earmark-excel",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": "bi-file-earmark-excel",
  "application/zip": "bi-file-earmark-zip",
  "image/png": "bi-file-earmark-image",
  "image/jpeg": "bi-file-earmark-image",
};

function iconFor(mimeType) {
  return FILE_ICONS[mimeType] ?? "bi-file-earmark";
}

function formatFileSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(0)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function buildPreviewRow(file) {
  const row = document.createElement("div");
  row.className = "attachment-row attachment-row--pending";
  row.innerHTML = `
    <span class="attachment-row__icon"><i class="bi ${iconFor(file.type)}" aria-hidden="true"></i></span>
    <span class="attachment-row__body">
      <span class="attachment-row__name">${escapeHtml(file.name)}</span>
      <span class="attachment-row__meta">${formatFileSize(file.size)} &middot; Pending upload</span>
    </span>
    <span class="attachment-row__actions">
      <button type="button" class="icon-btn icon-btn--danger" aria-label="Remove ${escapeHtml(file.name)}">
        <i class="bi bi-x-lg" aria-hidden="true"></i>
      </button>
    </span>
  `;
  row.querySelector(".icon-btn").addEventListener("click", () => row.remove());
  return row;
}

/**
 * @param {{ uploadArea: HTMLElement, fileInput: HTMLInputElement, list: HTMLElement }} elements
 */
export function initAttachmentUpload({ uploadArea, fileInput, list }) {
  if (!uploadArea || !fileInput || !list) {
    return;
  }

  const addFiles = (fileList) => {
    Array.from(fileList ?? []).forEach((file) => list.appendChild(buildPreviewRow(file)));
  };

  uploadArea.addEventListener("click", () => fileInput.click());

  uploadArea.addEventListener("keydown", (event) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      fileInput.click();
    }
  });

  ["dragover", "dragenter"].forEach((eventName) => {
    uploadArea.addEventListener(eventName, (event) => {
      event.preventDefault();
      uploadArea.classList.add("is-dragover");
    });
  });

  ["dragleave", "dragend"].forEach((eventName) => {
    uploadArea.addEventListener(eventName, () => uploadArea.classList.remove("is-dragover"));
  });

  uploadArea.addEventListener("drop", (event) => {
    event.preventDefault();
    uploadArea.classList.remove("is-dragover");
    addFiles(event.dataTransfer?.files);
  });

  fileInput.addEventListener("change", () => {
    addFiles(fileInput.files);
    fileInput.value = "";
  });
}
