/**
 * File-selection UI shared by every page with an upload drop-zone.
 * `initFileDropZone` only detects "the user picked/dropped these files" (via
 * click-to-browse or drag-and-drop) — what happens next is the caller's
 * concern, because the two current callers need genuinely different
 * behavior: create-ticket.html has no ticket id yet, so it stages files as
 * preview rows and uploads them only after the ticket is created
 * (`initStagedAttachmentUpload`); ticket-details.html's ticket already
 * exists, so it uploads each file immediately with real progress (built
 * directly in ticket-details.js using the exported `iconFor`/`formatFileSize`
 * helpers, not this module's staging list).
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

export function iconFor(mimeType) {
  return FILE_ICONS[mimeType] ?? "bi-file-earmark";
}

export function formatFileSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(0)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Wires a drop-zone `.upload-area` + hidden file input to call
 * `onFilesSelected(File[])` whenever files are picked or dropped —
 * click-to-browse, keyboard (Enter/Space), and drag-and-drop all funnel
 * through the same callback.
 * @param {{ uploadArea: HTMLElement, fileInput: HTMLInputElement, onFilesSelected: (files: File[]) => void }} options
 */
export function initFileDropZone({ uploadArea, fileInput, onFilesSelected }) {
  if (!uploadArea || !fileInput || !onFilesSelected) {
    return;
  }

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
    onFilesSelected(Array.from(event.dataTransfer?.files ?? []));
  });

  fileInput.addEventListener("change", () => {
    onFilesSelected(Array.from(fileInput.files ?? []));
    fileInput.value = "";
  });
}

function buildStagedRow(file, onRemove) {
  const row = document.createElement("div");
  row.className = "attachment-row attachment-row--pending";
  row.innerHTML = `
    <span class="attachment-row__icon"><i class="bi ${iconFor(file.type)}" aria-hidden="true"></i></span>
    <span class="attachment-row__body">
      <span class="attachment-row__name">${escapeHtml(file.name)}</span>
      <span class="attachment-row__meta">${formatFileSize(file.size)} &middot; Will upload after the ticket is created</span>
    </span>
    <span class="attachment-row__actions">
      <button type="button" class="icon-btn icon-btn--danger" aria-label="Remove ${escapeHtml(file.name)}">
        <i class="bi bi-x-lg" aria-hidden="true"></i>
      </button>
    </span>
  `;
  row.querySelector(".icon-btn").addEventListener("click", onRemove);
  return row;
}

/**
 * Stages files as preview rows (no upload — there's no ticket id yet on
 * create-ticket.html). The caller reads back the staged `File` objects via
 * `getFiles()` once the ticket is actually created, to upload them for real.
 * @param {{ uploadArea: HTMLElement, fileInput: HTMLInputElement, list: HTMLElement }} elements
 * @returns {{ getFiles: () => File[], clear: () => void }}
 */
export function initStagedAttachmentUpload({ uploadArea, fileInput, list }) {
  let stagedFiles = [];

  initFileDropZone({
    uploadArea,
    fileInput,
    onFilesSelected: (files) => {
      files.forEach((file) => {
        stagedFiles.push(file);
        const row = buildStagedRow(file, () => {
          stagedFiles = stagedFiles.filter((staged) => staged !== file);
          row.remove();
        });
        list.appendChild(row);
      });
    },
  });

  return {
    getFiles: () => stagedFiles,
    clear: () => {
      stagedFiles = [];
      list.replaceChildren();
    },
  };
}
