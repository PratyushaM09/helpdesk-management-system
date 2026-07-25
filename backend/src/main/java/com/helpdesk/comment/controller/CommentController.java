package com.helpdesk.comment.controller;

import com.helpdesk.comment.dto.request.CreateCommentRequest;
import com.helpdesk.comment.dto.request.UpdateCommentRequest;
import com.helpdesk.comment.dto.response.CommentResponse;
import com.helpdesk.comment.service.CommentService;
import com.helpdesk.common.ApiResponse;
import com.helpdesk.common.PageResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.constant.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Comment endpoints (Phase 3, Controller Milestone 2) — bind, validate via
 * {@code @Valid}, delegate exactly once to {@link CommentService}, shape the
 * response; no ownership/visibility decision is made in this class. Not
 * mapped under a single resource root: creation/listing are nested under
 * their parent ticket ({@code /tickets/{ticketId}/comments}), while
 * edit/delete address the comment directly ({@code /comments/{commentId}}) —
 * the same shape the brief specifies, so each method declares its own full
 * sub-path rather than forcing one artificial common prefix.
 */
@Tag(name = "Comment")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH)
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "Add a comment to a ticket",
            description = "Visibility defaults to PUBLIC if omitted; only SUPPORT_ENGINEER/ADMIN may post INTERNAL.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Comment added")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "A USER requested INTERNAL visibility")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found or not visible to the caller")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @PostMapping("/tickets/{ticketId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse created = commentService.addComment(ticketId, request);
        URI location = URI.create(ApiConstants.API_BASE_PATH + "/comments/" + created.id());
        return ResponseEntity.created(location).body(ApiResponse.success(ResponseMessages.CREATED, created));
    }

    @Operation(summary = "Update a comment's content", description = "Author or administrator only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comment updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is neither the author nor an administrator")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Comment not found")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @Parameter(description = "Comment identifier", example = "1") @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        CommentResponse updated = commentService.updateComment(commentId, request);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.UPDATED, updated));
    }

    @Operation(summary = "Delete a comment", description = "Author or administrator only. Physical delete - comments are never soft-deleted.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comment deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is neither the author nor an administrator")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Comment not found")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @Parameter(description = "Comment identifier", example = "1") @PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.DELETED));
    }

    @Operation(summary = "List a ticket's comments, paginated",
            description = "USER never receives INTERNAL comments; SUPPORT_ENGINEER/ADMIN receive every visibility.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paged comment list")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ticket not found or not visible to the caller")
    @PreAuthorize("hasAnyRole('USER','SUPPORT_ENGINEER','ADMIN')")
    @GetMapping("/tickets/{ticketId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> getComments(
            @Parameter(description = "Ticket identifier", example = "1") @PathVariable Long ticketId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<CommentResponse> comments = commentService.getComments(ticketId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(comments)));
    }
}
