/**
 * Page-specific script for ticket-details.html. Reads `?id=` from the URL
 * (the real numeric Ticket.id, not the ticketNumber — tickets.js links here
 * with `?id=`) and loads everything from the real backend: ticket detail,
 * comments, attachments, and history. Also wires every ticket action
 * (edit/assign/reassign/status-change/reopen/delete) and comment/attachment
 * CRUD, all permission-aware.
 *
 * Two backend limitations shape parts of this file (both confirmed by
 * reading the backend source, not assumed):
 * - CommentResponse/AttachmentResponse expose the author/uploader's name
 *   only, never an id, so "is this mine?" for showing Edit/Delete buttons
 *   is a best-effort name match — the server is the real enforcement
 *   backstop (a false-positive button just gets a real 403).
 * - GET /attachments/{id} is metadata-only (no FileStorageService exists),
 *   so "Download" calls the real endpoint and then explains there's no
 *   file content to download, rather than faking a browser download.
 */

import { initShell } from "../core/shell.js";
import { initDropdown } from "../core/dropdown.js";
import { initFileDropZone, iconFor, formatFileSize } from "../core/attachment-preview.js";
import {
  getTicket,
  updateTicket,
  assignTicket,
  reassignTicket,
  changeTicketStatus,
  reopenTicket,
  deleteTicket,
  listTicketHistory,
} from "../core/tickets-api.js";
import { listComments, addComment, updateComment, deleteComment } from "../core/comments-api.js";
import {
  listAttachments,
  uploadTicketAttachment,
  deleteAttachment,
  getAttachmentMetadata,
} from "../core/attachments-api.js";
import { formatStatus, formatPriority, formatHistoryAction } from "../core/ticket-format.js";
import { escapeHtml, formatDate, getInitials, showToast, setFieldError, setButtonLoading } from "../core/utils.js";
import { api, ApiError } from "../core/api.js";

/** Status transitions POST /tickets/{id}/status allows — mirrors TicketServiceImpl.LEGAL_STATUS_TRANSITIONS exactly. */
const LEGAL_TRANSITIONS = {
  ASSIGNED: ["IN_PROGRESS"],
  IN_PROGRESS: ["WAITING_FOR_CUSTOMER", "RESOLVED"],
  WAITING_FOR_CUSTOMER: ["IN_PROGRESS"],
  RESOLVED: ["CLOSED"],
};

const currentUser = await initShell();
if (currentUser) {
  init(currentUser);
}

async function init(currentUser) {
  const ticketId = new URLSearchParams(window.location.search).get("id");

  const loadingEl = document.getElementById("ticket-loading");
  const errorEl = document.getElementById("ticket-error");
  const errorMessageEl = document.getElementById("ticket-error-message");
  const contentEl = document.getElementById("ticket-content");

  const modalOverlay = document.getElementById("action-modal-overlay");
  const modalTitle = document.getElementById("action-modal-title");
  const modalBody = document.getElementById("action-modal-body");
  const modalForm = document.getElementById("action-modal-form");
  const modalConfirm = document.getElementById("action-modal-confirm");

  let ticket = null;
  let comments = [];
  let attachments = [];
  let historyEntries = [];
  let modalSubmitHandler = null;

  if (!ticketId) {
    showLoadError("No ticket was specified.");
    return;
  }

  const statusDropdown = initDropdown({
    trigger: document.querySelector("#action-status-dropdown .filter-dropdown__trigger"),
    menu: document.getElementById("status-action-menu"),
  });

  wireModal();
  wireCommentEditor();
  initFileDropZone({
    uploadArea: document.getElementById("attachment-upload"),
    fileInput: document.getElementById("attachment-input"),
    onFilesSelected: (files) => files.forEach(uploadAttachmentWithProgress),
  });

  await loadTicket();

  // ---------- Loading ----------

  async function loadTicket() {
    loadingEl.hidden = false;
    errorEl.hidden = true;
    contentEl.hidden = true;

    try {
      ticket = await getTicket(ticketId);
      renderTicket();
      loadingEl.hidden = true;
      contentEl.hidden = false;
      await Promise.all([loadComments(), loadAttachments(), loadHistory()]);
    } catch (error) {
      loadingEl.hidden = true;
      showLoadError(error instanceof ApiError ? error.message : "Something went wrong. Please try again.");
    }
  }

  function showLoadError(message) {
    errorMessageEl.textContent = message;
    errorEl.hidden = false;
    contentEl.hidden = true;
  }

  // ---------- Ticket header / summary / actions ----------

  function renderTicket() {
    document.title = `${ticket.ticketNumber} · Ticket Details · HelpDesk`;
    document.getElementById("ticket-title").textContent = ticket.title;

    const statusMeta = formatStatus(ticket.status);
    const priorityMeta = formatPriority(ticket.priority);

    document.getElementById("ticket-badges").innerHTML = `
      <span class="badge badge--neutral">${escapeHtml(ticket.ticketNumber)}</span>
      <span class="badge ${statusMeta.badgeClass}">${escapeHtml(statusMeta.label)}</span>
      <span class="badge ${priorityMeta.badgeClass}">${escapeHtml(priorityMeta.label)}</span>
    `;

    document.getElementById("ticket-description").textContent = ticket.description;

    document.getElementById("summary-requester").textContent = ticket.createdByName;
    document.getElementById("summary-assignee").textContent = ticket.assignedToName ?? "Unassigned";
    document.getElementById("summary-category").textContent = ticket.categoryName;
    document.getElementById(
      "summary-priority"
    ).innerHTML = `<span class="badge ${priorityMeta.badgeClass}">${escapeHtml(priorityMeta.label)}</span>`;
    document.getElementById(
      "summary-status"
    ).innerHTML = `<span class="badge ${statusMeta.badgeClass}">${escapeHtml(statusMeta.label)}</span>`;
    document.getElementById("summary-created").textContent = formatDate(ticket.createdAt);
    document.getElementById("summary-updated").textContent = formatDate(ticket.updatedAt);

    toggleRow("summary-resolved-row", "summary-resolved", ticket.resolvedAt);
    toggleRow("summary-closed-row", "summary-closed", ticket.closedAt);

    renderTimeline();
    renderActions();
  }

  function toggleRow(rowId, valueId, value) {
    const row = document.getElementById(rowId);
    row.hidden = !value;
    if (value) {
      document.getElementById(valueId).textContent = formatDate(value);
    }
  }

  function renderActions() {
    const role = currentUser.role;
    const status = ticket.status;

    const canEdit = role === "ADMIN" || (role === "USER" && (status === "OPEN" || status === "WAITING_FOR_CUSTOMER"));
    setHidden("action-edit", !canEdit);

    setHidden("action-assign", !(role === "ADMIN" && status === "OPEN"));
    setHidden(
      "action-reassign",
      !(role === "ADMIN" && ["ASSIGNED", "IN_PROGRESS", "WAITING_FOR_CUSTOMER"].includes(status))
    );

    const transitions = (LEGAL_TRANSITIONS[status] ?? []).filter((target) =>
      target === "CLOSED" ? role === "USER" || role === "ADMIN" : role === "SUPPORT_ENGINEER" || role === "ADMIN"
    );
    const statusMenu = document.getElementById("status-action-menu");
    if (transitions.length > 0) {
      setHidden("action-status-dropdown", false);
      statusMenu.innerHTML = transitions
        .map((target) => {
          const meta = formatStatus(target);
          return `<button type="button" class="dropdown-menu__item" role="menuitem" data-target-status="${target}">Mark as ${escapeHtml(meta.label)}</button>`;
        })
        .join("");
      statusMenu.querySelectorAll("[data-target-status]").forEach((button) => {
        button.addEventListener("click", () => {
          statusDropdown.close();
          openStatusChangeModal(button.dataset.targetStatus);
        });
      });
    } else {
      setHidden("action-status-dropdown", true);
    }

    setHidden("action-reopen", !(status === "RESOLVED" && (role === "USER" || role === "ADMIN")));
    setHidden("action-delete", role !== "ADMIN");
  }

  function setHidden(id, hidden) {
    const el = document.getElementById(id);
    if (el) {
      el.hidden = hidden;
    }
  }

  // ---------- Timeline (synthetic Created + real history, oldest-first) / History (real, newest-first) ----------

  function renderTimeline() {
    const items = [
      {
        label: "Created",
        meta: `${ticket.createdByName} opened this ticket · ${formatDate(ticket.createdAt)}`,
      },
      ...historyEntries.map(historyToTimelineItem),
    ];
    document.getElementById("ticket-timeline").innerHTML = items
      .map((item, index) => buildTimelineItemHtml(item, index === items.length - 1))
      .join("");
  }

  function renderHistory() {
    const container = document.getElementById("ticket-history");
    if (historyEntries.length === 0) {
      container.innerHTML = '<p class="form-hint">No history recorded yet.</p>';
      return;
    }
    const items = [...historyEntries].reverse().map(historyToTimelineItem);
    container.innerHTML = items.map((item, index) => buildTimelineItemHtml(item, index === items.length - 1)).join("");
  }

  function historyToTimelineItem(entry) {
    const meta = entry.note
      ? `${entry.actorName} · ${formatDate(entry.createdAt)} — ${entry.note}`
      : `${entry.actorName} · ${formatDate(entry.createdAt)}`;
    return { label: formatHistoryAction(entry.action), meta };
  }

  function buildTimelineItemHtml({ label, meta }, isLast) {
    return `
      <div class="timeline__item is-complete">
        <span class="timeline__rail">
          <span class="timeline__marker"></span>
          ${isLast ? "" : '<span class="timeline__line"></span>'}
        </span>
        <div class="timeline__content">
          <p class="timeline__title">${escapeHtml(label)}</p>
          <p class="timeline__meta">${escapeHtml(meta)}</p>
        </div>
      </div>
    `;
  }

  async function loadHistory() {
    try {
      const result = await listTicketHistory(ticket.id, { size: 100, sort: "id,asc" });
      historyEntries = result.content;
      renderTimeline();
      renderHistory();
    } catch {
      document.getElementById("ticket-history").innerHTML = '<p class="form-hint">Couldn\'t load history.</p>';
    }
  }

  // ---------- Generic action modal ----------

  function wireModal() {
    document.getElementById("action-modal-close").addEventListener("click", closeModal);
    document.getElementById("action-modal-cancel").addEventListener("click", closeModal);
    modalOverlay.addEventListener("click", (event) => {
      if (event.target === modalOverlay) {
        closeModal();
      }
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !modalOverlay.hidden) {
        closeModal();
      }
    });
    modalForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      await modalSubmitHandler?.();
    });
  }

  function openModal(title, bodyHtml, onSubmit) {
    modalTitle.textContent = title;
    modalBody.innerHTML = bodyHtml;
    modalSubmitHandler = onSubmit;
    modalOverlay.hidden = false;
    modalBody.querySelector("input, select, textarea")?.focus();
  }

  function closeModal() {
    modalOverlay.hidden = true;
    modalSubmitHandler = null;
    modalForm.reset();
  }

  // ---------- Actions: edit / assign / reassign / status / reopen / delete ----------

  document.getElementById("action-edit").addEventListener("click", () => {
    openModal(
      "Edit Ticket",
      `
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-title-input">Title</label>
        <input class="form-control" id="modal-title-input" maxlength="200" value="${escapeHtml(ticket.title)}" required />
      </div>
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-description-input">Description</label>
        <textarea class="form-control" id="modal-description-input" rows="6" required>${escapeHtml(ticket.description)}</textarea>
      </div>
    `,
      async () => {
        const title = document.getElementById("modal-title-input").value.trim();
        const description = document.getElementById("modal-description-input").value.trim();
        if (!title || !description) {
          return;
        }
        await runModalAction(async () => {
          ticket = await updateTicket(ticket.id, { title, description, version: ticket.version });
          renderTicket();
          showToast("Ticket updated.", "success");
        });
      }
    );
  });

  document.getElementById("action-assign").addEventListener("click", () => openAssignModal(false));
  document.getElementById("action-reassign").addEventListener("click", () => openAssignModal(true));

  async function openAssignModal(isReassign) {
    openModal(isReassign ? "Reassign Ticket" : "Assign Ticket", '<p class="form-hint">Loading support engineers…</p>', null);
    try {
      const engineers = await listSupportEngineers();
      if (engineers.length === 0) {
        modalBody.innerHTML = '<p class="form-hint">No support engineers found.</p>';
        return;
      }
      modalBody.innerHTML = `
        <div class="form-group">
          <label class="form-label form-label--required" for="modal-agent-select">Support engineer</label>
          <select class="form-control" id="modal-agent-select" required>
            <option value="" disabled selected>Select an engineer</option>
            ${engineers.map((engineer) => `<option value="${engineer.id}">${escapeHtml(engineer.name)}</option>`).join("")}
          </select>
        </div>
      `;
      modalBody.querySelector("select")?.focus();
      modalSubmitHandler = async () => {
        const agentId = Number(document.getElementById("modal-agent-select").value);
        if (!agentId) {
          return;
        }
        await runModalAction(async () => {
          ticket = await (isReassign ? reassignTicket : assignTicket)(ticket.id, { agentId, version: ticket.version });
          renderTicket();
          showToast(isReassign ? "Ticket reassigned." : "Ticket assigned.", "success");
        });
      };
    } catch {
      modalBody.innerHTML = '<p class="form-hint">Couldn\'t load support engineers.</p>';
    }
  }

  async function listSupportEngineers() {
    const result = await api.get("/users?page=0&size=100");
    return result.content.filter((candidate) => candidate.role === "SUPPORT_ENGINEER");
  }

  function openStatusChangeModal(targetStatus) {
    const meta = formatStatus(targetStatus);
    openModal(
      `Mark as ${meta.label}`,
      `
      <div class="form-group">
        <label class="form-label" for="modal-status-comment">Comment (optional)</label>
        <textarea class="form-control" id="modal-status-comment" rows="3" placeholder="Add context for this status change…"></textarea>
      </div>
    `,
      async () => {
        const comment = document.getElementById("modal-status-comment").value.trim();
        await runModalAction(async () => {
          ticket = await changeTicketStatus(ticket.id, {
            targetStatus,
            comment: comment || undefined,
            version: ticket.version,
          });
          renderTicket();
          await loadHistory();
          showToast("Status updated.", "success");
        });
      }
    );
  }

  document.getElementById("action-reopen").addEventListener("click", () => {
    openModal(
      "Reopen Ticket",
      `
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-reopen-reason">Reason</label>
        <textarea class="form-control" id="modal-reopen-reason" rows="3" required></textarea>
      </div>
    `,
      async () => {
        const reason = document.getElementById("modal-reopen-reason").value.trim();
        if (!reason) {
          return;
        }
        await runModalAction(async () => {
          ticket = await reopenTicket(ticket.id, { reason });
          renderTicket();
          await loadHistory();
          showToast("Ticket reopened.", "success");
        });
      }
    );
  });

  document.getElementById("action-delete").addEventListener("click", async () => {
    if (!window.confirm(`Delete ticket ${ticket.ticketNumber}? This cannot be undone.`)) {
      return;
    }
    try {
      await deleteTicket(ticket.id);
      showToast("Ticket deleted.", "success");
      window.location.href = "tickets.html";
    } catch (error) {
      showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
    }
  });

  async function runModalAction(action) {
    setButtonLoading(modalConfirm, true, "Saving…");
    try {
      await action();
      closeModal();
    } catch (error) {
      showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
    } finally {
      setButtonLoading(modalConfirm, false);
    }
  }

  // ---------- Comments ----------

  async function loadComments() {
    try {
      const result = await listComments(ticket.id, { size: 50 });
      comments = result.content;
      renderComments();
    } catch {
      document.getElementById("comments-list").innerHTML = '<p class="form-hint">Couldn\'t load comments.</p>';
    }
  }

  function renderComments() {
    const container = document.getElementById("comments-list");
    container.innerHTML =
      comments.length === 0
        ? '<p class="form-hint mb-4">No comments yet.</p>'
        : comments.map(buildCommentHtml).join("");
    wireCommentActions();
  }

  function buildCommentHtml(comment) {
    const isInternal = comment.visibility === "INTERNAL";
    const canModify = comment.authorName === currentUser.name || currentUser.role === "ADMIN";
    return `
      <div class="comment${isInternal ? " comment--internal" : ""}" data-comment-id="${comment.id}">
        <span class="avatar avatar--sm" aria-hidden="true">${escapeHtml(getInitials(comment.authorName))}</span>
        <div class="comment__content">
          <div class="comment__header">
            <span class="comment__author">${escapeHtml(comment.authorName)}</span>
            ${isInternal ? '<span class="badge badge--signal"><i class="bi bi-lock-fill" aria-hidden="true"></i> Internal</span>' : ""}
            <span class="comment__time">${formatDate(comment.createdAt)}${comment.edited ? " (edited)" : ""}</span>
            ${
              canModify
                ? `<button type="button" class="icon-btn" data-comment-edit aria-label="Edit comment"><i class="bi bi-pencil" aria-hidden="true"></i></button>
                   <button type="button" class="icon-btn icon-btn--danger" data-comment-delete aria-label="Delete comment"><i class="bi bi-trash" aria-hidden="true"></i></button>`
                : ""
            }
            <button type="button" class="comment__collapse" aria-expanded="true" aria-label="Collapse ${escapeHtml(comment.authorName)}'s comment">
              <i class="bi bi-chevron-down" aria-hidden="true"></i>
            </button>
          </div>
          <p class="comment__body">${escapeHtml(comment.content)}</p>
          <p class="comment__preview">${escapeHtml(truncate(comment.content, 120))}</p>
        </div>
      </div>
    `;
  }

  function truncate(text, maxLength) {
    return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text;
  }

  function wireCommentActions() {
    document.querySelectorAll(".comment__collapse").forEach((button) => {
      button.addEventListener("click", () => {
        const comment = button.closest(".comment");
        const isCollapsed = comment.classList.toggle("is-collapsed");
        button.setAttribute("aria-expanded", String(!isCollapsed));
      });
    });

    document.querySelectorAll("[data-comment-edit]").forEach((button) => {
      button.addEventListener("click", () => {
        const commentId = Number(button.closest(".comment").dataset.commentId);
        const comment = comments.find((candidate) => candidate.id === commentId);
        if (comment) {
          openEditCommentModal(comment);
        }
      });
    });

    document.querySelectorAll("[data-comment-delete]").forEach((button) => {
      button.addEventListener("click", async () => {
        const commentId = Number(button.closest(".comment").dataset.commentId);
        if (!window.confirm("Delete this comment?")) {
          return;
        }
        try {
          await deleteComment(commentId);
          await loadComments();
          showToast("Comment deleted.", "success");
        } catch (error) {
          showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
        }
      });
    });
  }

  function openEditCommentModal(comment) {
    openModal(
      "Edit Comment",
      `
      <div class="form-group">
        <label class="form-label form-label--required" for="modal-comment-content">Comment</label>
        <textarea class="form-control" id="modal-comment-content" rows="4" required>${escapeHtml(comment.content)}</textarea>
      </div>
    `,
      async () => {
        const content = document.getElementById("modal-comment-content").value.trim();
        if (!content) {
          return;
        }
        await runModalAction(async () => {
          await updateComment(comment.id, { content });
          await loadComments();
          showToast("Comment updated.", "success");
        });
      }
    );
  }

  function wireCommentEditor() {
    const commentInput = document.getElementById("comment-input");
    const commentError = document.getElementById("comment-error");
    const postCommentButton = document.getElementById("post-comment-button");
    const visibilityGroup = document.getElementById("comment-visibility");
    let selectedVisibility = "PUBLIC";

    if (currentUser.role === "USER") {
      document.getElementById("comment-visibility-internal")?.remove();
    }

    visibilityGroup.querySelectorAll(".segmented-control__option").forEach((option) => {
      option.addEventListener("click", () => {
        selectedVisibility = option.dataset.visibility;
        visibilityGroup.querySelectorAll(".segmented-control__option").forEach((candidate) => {
          const isSelected = candidate === option;
          candidate.classList.toggle("is-active", isSelected);
          candidate.setAttribute("aria-checked", String(isSelected));
        });
      });
    });

    postCommentButton.addEventListener("click", async () => {
      const content = commentInput.value.trim();
      if (!content) {
        setFieldError(commentInput, commentError, "Write a comment before posting.");
        commentInput.focus();
        return;
      }
      setFieldError(commentInput, commentError);
      setButtonLoading(postCommentButton, true, "Posting…");
      try {
        await addComment(ticket.id, { content, visibility: selectedVisibility });
        commentInput.value = "";
        await loadComments();
        showToast("Comment posted.", "success");
      } catch (error) {
        showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
      } finally {
        setButtonLoading(postCommentButton, false);
      }
    });
  }

  // ---------- Attachments ----------

  async function loadAttachments() {
    try {
      const result = await listAttachments(ticket.id, { size: 50 });
      attachments = result.content;
      renderAttachments();
    } catch {
      document.getElementById("attachments-list").innerHTML = '<p class="form-hint">Couldn\'t load attachments.</p>';
    }
  }

  function renderAttachments() {
    const container = document.getElementById("attachments-list");
    container.innerHTML =
      attachments.length === 0
        ? '<p class="form-hint mb-4">No attachments yet.</p>'
        : attachments.map(buildAttachmentHtml).join("");
    wireAttachmentActions();
  }

  function buildAttachmentHtml(attachment) {
    const canModify = attachment.uploadedByName === currentUser.name || currentUser.role === "ADMIN";
    return `
      <div class="attachment-row" data-attachment-id="${attachment.id}">
        <span class="attachment-row__icon"><i class="bi ${iconFor(attachment.mimeType)}" aria-hidden="true"></i></span>
        <span class="attachment-row__body">
          <span class="attachment-row__name">${escapeHtml(attachment.originalFilename)}</span>
          <span class="attachment-row__meta">${formatFileSize(attachment.sizeBytes)} &middot; Uploaded by ${escapeHtml(attachment.uploadedByName)} &middot; ${formatDate(attachment.createdAt)}</span>
        </span>
        <span class="attachment-row__actions">
          <button type="button" class="icon-btn" data-attachment-download aria-label="Download ${escapeHtml(attachment.originalFilename)}">
            <i class="bi bi-download" aria-hidden="true"></i>
          </button>
          ${
            canModify
              ? `<button type="button" class="icon-btn icon-btn--danger" data-attachment-delete aria-label="Delete ${escapeHtml(attachment.originalFilename)}"><i class="bi bi-trash" aria-hidden="true"></i></button>`
              : ""
          }
        </span>
      </div>
    `;
  }

  function wireAttachmentActions() {
    document.querySelectorAll("[data-attachment-download]").forEach((button) => {
      button.addEventListener("click", async () => {
        const id = Number(button.closest(".attachment-row").dataset.attachmentId);
        try {
          await getAttachmentMetadata(id);
          showToast(
            "This backend doesn't store file content yet (metadata only) — there's nothing to download.",
            "info"
          );
        } catch (error) {
          showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
        }
      });
    });

    document.querySelectorAll("[data-attachment-delete]").forEach((button) => {
      button.addEventListener("click", async () => {
        const id = Number(button.closest(".attachment-row").dataset.attachmentId);
        if (!window.confirm("Delete this attachment?")) {
          return;
        }
        try {
          await deleteAttachment(id);
          await loadAttachments();
          showToast("Attachment deleted.", "success");
        } catch (error) {
          showToast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "danger");
        }
      });
    });
  }

  function uploadAttachmentWithProgress(file) {
    const container = document.getElementById("attachments-list");
    const row = document.createElement("div");
    row.className = "attachment-row attachment-row--pending";
    row.innerHTML = `
      <span class="attachment-row__icon"><i class="bi ${iconFor(file.type)}" aria-hidden="true"></i></span>
      <span class="attachment-row__body">
        <span class="attachment-row__name">${escapeHtml(file.name)}</span>
        <span class="attachment-row__meta" data-progress>Uploading… 0%</span>
      </span>
    `;
    container.prepend(row);
    const progressEl = row.querySelector("[data-progress]");

    uploadTicketAttachment(ticket.id, file, {
      onProgress: (percent) => {
        progressEl.textContent = `Uploading… ${percent}%`;
      },
    })
      .then(() => {
        showToast(`"${file.name}" uploaded.`, "success");
        loadAttachments();
      })
      .catch((error) => {
        row.remove();
        showToast(
          error instanceof ApiError ? error.message : `"${file.name}" failed to upload.`,
          "danger"
        );
      });
  }
}
