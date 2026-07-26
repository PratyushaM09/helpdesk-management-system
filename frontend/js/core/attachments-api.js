/**
 * Attachment API calls. List/get/delete reuse core/api.js exactly like
 * every other module; upload is the one deliberate exception in this
 * codebase to "always go through api.js" — fetch() has no way to report
 * upload progress, so uploadTicketAttachment uses XMLHttpRequest instead,
 * hand-mirroring api.js's credentials/CSRF handling since XHR's API shape
 * doesn't match fetch's. Nothing else in the app needs this; every other
 * request stays on api.js.
 *
 * Download: GET /attachments/{id} is metadata-only on this backend (no
 * FileStorageService exists yet — confirmed by reading AttachmentController/
 * AttachmentServiceImpl) — it returns the same AttachmentResponse shape as
 * everywhere else, never file bytes or a URL. getAttachmentMetadata() is
 * still a real call to the real endpoint; there is simply nothing further
 * to do with its response, which is why ticket-details.js's Download
 * action calls it and then shows an explanatory message rather than
 * triggering a browser download.
 */

import { api, ApiError } from "./api.js";
import { CONFIG } from "./config.js";
import { getCookie } from "./utils.js";

/** GET /tickets/{ticketId}/attachments — paginated; INTERNAL-comment attachments already excluded server-side for USER. */
export async function listAttachments(ticketId, { page = 0, size = 20 } = {}) {
  return api.get(`/tickets/${ticketId}/attachments?page=${page}&size=${size}`);
}

/** GET /attachments/{attachmentId} — metadata only, see module doc. */
export async function getAttachmentMetadata(attachmentId) {
  return api.get(`/attachments/${attachmentId}`);
}

/** DELETE /attachments/{attachmentId} — uploader-or-ADMIN, enforced server-side. */
export async function deleteAttachment(attachmentId) {
  return api.delete(`/attachments/${attachmentId}`);
}

/**
 * POST /tickets/{ticketId}/attachments, multipart/form-data, field name "file".
 * @param {number} ticketId
 * @param {File} file
 * @param {{ onProgress?: (percent: number) => void }} [options]
 */
export function uploadTicketAttachment(ticketId, file, options = {}) {
  return uploadFile(`/tickets/${ticketId}/attachments`, file, options);
}

function uploadFile(path, file, { onProgress } = {}) {
  return new Promise((resolve, reject) => {
    const formData = new FormData();
    formData.append("file", file);

    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${CONFIG.API_BASE_URL}${path}`);
    xhr.withCredentials = true;

    const csrfToken = getCookie(CONFIG.CSRF_COOKIE_NAME);
    if (csrfToken) {
      xhr.setRequestHeader(CONFIG.CSRF_HEADER_NAME, csrfToken);
    }

    xhr.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable) {
        onProgress?.(Math.round((event.loaded / event.total) * 100));
      }
    });

    xhr.addEventListener("load", () => {
      let payload = null;
      try {
        payload = xhr.responseText ? JSON.parse(xhr.responseText) : null;
      } catch {
        payload = null;
      }

      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(payload?.data ?? null);
        return;
      }

      const errorBody = payload ?? {};
      reject(
        new ApiError(errorBody.message || `Request failed with status ${xhr.status}`, {
          status: xhr.status,
          errorCode: errorBody.errorCode ?? null,
          validationErrors: errorBody.validationErrors ?? null,
          path,
        })
      );
    });

    xhr.addEventListener("error", () => {
      reject(
        new ApiError("Unable to reach the server. Check your connection and try again.", {
          errorCode: "NETWORK_ERROR",
        })
      );
    });

    xhr.send(formData);
  });
}
