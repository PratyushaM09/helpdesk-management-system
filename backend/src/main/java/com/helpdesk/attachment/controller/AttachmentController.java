package com.helpdesk.attachment.controller;

import com.helpdesk.attachment.dto.response.AttachmentResponse;
import com.helpdesk.attachment.service.AttachmentService;
import com.helpdesk.common.ApiResponse;
import com.helpdesk.common.PageResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.constant.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

/**
 * Attachment endpoints (Phase 3, Controller Milestone 2) — bind, delegate
 * exactly once to {@link AttachmentService}, shape the response; MIME/size/
 * ownership validation stays entirely in the Service. Not mapped under a
 * single resource root: upload is nested under either a ticket or a comment,
 * while download/delete address the attachment directly
 * ({@code /attachments/{attachmentId}}) — the same shape the brief
 * specifies, so each method declares its own full sub-path.
 * <p>
 * Multipart handling: {@code @RequestParam("file") MultipartFile file} — no
 * existing upload endpoint was found anywhere in this codebase to match, so
 * this is the plain, idiomatic Spring form for "one file, no accompanying
 * JSON part" (the alternative, {@code @RequestPart}, exists specifically for
 * content-negotiated deserialization of a second, structured part — not
 * needed here since ticket/comment id are path variables, not part of the
 * multipart body).
 * <p>
 * {@code storageKey} generation: {@code AttachmentService} takes an
 * already-produced {@code storageKey} as given (Service Milestone 3 - "no
 * storage implementation, assume storageKey already exists"). With no
 * {@code FileStorageService} (ADR-0008) built yet, this controller generates
 * a random UUID placeholder in its place - a stand-in for whatever a real
 * storage backend would eventually return, not a storage implementation
 * itself. Replace this with a real upload call once that service exists.
 */
@Tag(name = "Attachment")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH)
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Operation(summary = "Upload a ticket-level attachment")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Attachment uploaded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported file type or size")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found or not visible to the caller")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File (or overall multipart request) exceeds the server's configured upload size limit")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @PostMapping(value = "/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentResponse>> uploadTicketAttachment(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file) {
        AttachmentResponse created = attachmentService.uploadAttachment(
                ticketId, null, generateStorageKey(), file.getOriginalFilename(), file.getContentType(), file.getSize());
        URI location = URI.create(ApiConstants.API_BASE_PATH + "/attachments/" + created.id());
        return ResponseEntity.created(location).body(ApiResponse.success(ResponseMessages.CREATED, created));
    }

    @Operation(summary = "Upload a comment-level attachment")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Attachment uploaded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported file type or size")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Comment not found, or its ticket isn't visible to the caller")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File (or overall multipart request) exceeds the server's configured upload size limit")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @PostMapping(value = "/comments/{commentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentResponse>> uploadCommentAttachment(
            @Parameter(description = "Comment identifier", example = "1") @PathVariable Long commentId,
            @RequestParam("file") MultipartFile file) {
        AttachmentResponse created = attachmentService.uploadAttachment(
                null, commentId, generateStorageKey(), file.getOriginalFilename(), file.getContentType(), file.getSize());
        URI location = URI.create(ApiConstants.API_BASE_PATH + "/attachments/" + created.id());
        return ResponseEntity.created(location).body(ApiResponse.success(ResponseMessages.CREATED, created));
    }

    @Operation(summary = "List a ticket's attachments, paginated",
            description = "USER never receives attachments belonging to an INTERNAL comment.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paged attachment list")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found or not visible to the caller")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @GetMapping("/tickets/{ticketId}/attachments")
    public ResponseEntity<ApiResponse<PageResponse<AttachmentResponse>>> listAttachments(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long ticketId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<AttachmentResponse> attachments = attachmentService.listAttachments(ticketId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(attachments)));
    }

    @Operation(summary = "Get an attachment's metadata",
            description = "Metadata only - no file bytes are streamed (no FileStorageService exists yet). "
                    + "Re-checks the parent ticket's visibility at request time.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Attachment metadata")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Attachment not found, or its ticket isn't visible to the caller")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<ApiResponse<AttachmentResponse>> downloadAttachment(
            @Parameter(description = "Attachment identifier", example = "1") @PathVariable Long attachmentId) {
        return ResponseEntity.ok(ApiResponse.success(attachmentService.downloadAttachment(attachmentId)));
    }

    @Operation(summary = "Delete an attachment", description = "Uploader or administrator only. Physical delete - no soft delete.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Attachment deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is neither the uploader nor an administrator")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Attachment not found")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @Parameter(description = "Attachment identifier", example = "1") @PathVariable Long attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.DELETED));
    }

    private String generateStorageKey() {
        return UUID.randomUUID().toString();
    }
}
