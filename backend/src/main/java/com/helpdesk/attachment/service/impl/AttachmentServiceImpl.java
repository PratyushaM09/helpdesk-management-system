package com.helpdesk.attachment.service.impl;

import com.helpdesk.attachment.dto.response.AttachmentResponse;
import com.helpdesk.attachment.entity.Attachment;
import com.helpdesk.attachment.mapper.AttachmentMapper;
import com.helpdesk.attachment.repository.AttachmentRepository;
import com.helpdesk.attachment.service.AttachmentService;
import com.helpdesk.comment.entity.Comment;
import com.helpdesk.comment.entity.CommentVisibility;
import com.helpdesk.comment.repository.CommentRepository;
import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.ForbiddenException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.role.entity.RoleName;
import com.helpdesk.security.UserPrincipal;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AttachmentServiceImpl implements AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentServiceImpl.class);

    /** FR-ATT-2's allow-list: images (JPG/PNG), PDF, Word, Excel, ZIP. Not yet externalized to a {@code FileUploadProperties} configuration class. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png",
            "application/pdf",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip"
    );

    /** 10 MB - not yet externalized to configuration, same as {@link #ALLOWED_MIME_TYPES}. */
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TicketService ticketService;
    private final AttachmentMapper attachmentMapper;

    public AttachmentServiceImpl(AttachmentRepository attachmentRepository, TicketRepository ticketRepository,
                                  CommentRepository commentRepository, UserRepository userRepository,
                                  TicketService ticketService, AttachmentMapper attachmentMapper) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.ticketService = ticketService;
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    @Transactional
    public AttachmentResponse uploadAttachment(Long ticketId, Long commentId, String storageKey,
                                                String originalFilename, String mimeType, long sizeBytes) {
        if (ticketId == null && commentId == null) {
            throw new BadRequestException("Either a ticket or a comment must be specified for this attachment.");
        }
        Comment comment = commentId != null
                ? commentRepository.findById(commentId).orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId))
                : null;
        // The comment-scoped upload route (Controller Milestone 2) has no
        // ticketId in its path - only commentId. Rather than have the
        // Controller reach into CommentRepository itself to resolve it (data
        // access that isn't "delegate exactly once to the appropriate
        // service"), this method derives it here when the caller doesn't
        // already know it.
        Long resolvedTicketId = ticketId != null ? ticketId : comment.getTicket().getId();

        ticketService.validateTicketAccess(resolvedTicketId);
        Ticket ticket = ticketRepository.findById(resolvedTicketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", resolvedTicketId));

        validateMimeType(mimeType);
        validateSize(sizeBytes);

        User uploadedBy = loadAuthenticatedUser();
        // No AttachmentMapper.toEntity exists (Mapper Layer milestone: "no
        // request mapping, uploads are multipart") - constructed directly
        // here, the same "Service builds the entity when no mapper method
        // covers this direction" pattern AccountServiceImpl already follows.
        Attachment attachment = new Attachment(ticket, comment, storageKey, originalFilename, mimeType, sizeBytes, uploadedBy);
        Attachment saved = attachmentRepository.save(attachment);

        log.info("Attachment uploaded: attachmentId={}, ticketId={}, commentId={}", saved.getId(), ticketId, commentId);
        return attachmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentResponse downloadAttachment(Long attachmentId) {
        Attachment attachment = findAttachmentOrThrow(attachmentId);
        ticketService.validateTicketAccess(attachment.getTicket().getId());
        return attachmentMapper.toResponse(attachment);
    }

    @Override
    @Transactional
    public void deleteAttachment(Long attachmentId) {
        Attachment attachment = findAttachmentOrThrow(attachmentId);
        // Same "visibility gates ownership" order downloadAttachment/
        // uploadAttachment/listAttachments already follow - without this, a
        // caller with no legitimate access to the parent ticket could still
        // learn an attachment id exists and who owns it from the 403 below,
        // the exact anti-enumeration leak the 404-not-403 convention exists
        // to prevent (02-Architecture.md's visibility rule).
        ticketService.validateTicketAccess(attachment.getTicket().getId());
        validateAttachmentOwner(attachment, currentPrincipal());

        attachmentRepository.delete(attachment);
        log.info("Attachment deleted: attachmentId={}", attachmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AttachmentResponse> listAttachments(Long ticketId, Pageable pageable) {
        ticketService.validateTicketAccess(ticketId);

        UserPrincipal principal = currentPrincipal();
        Page<Attachment> attachments = principal.role() == RoleName.USER
                ? attachmentRepository.findByTicketIdExcludingCommentVisibility(ticketId, CommentVisibility.INTERNAL, pageable)
                : attachmentRepository.findByTicketId(ticketId, pageable);

        return attachments.map(attachmentMapper::toResponse);
    }

    private void validateMimeType(String mimeType) {
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new BadRequestException("Unsupported file type: '%s'.".formatted(mimeType));
        }
    }

    private void validateSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new BadRequestException("Uploaded file is empty.");
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new BadRequestException(
                    "File exceeds the maximum allowed size of %d MB.".formatted(MAX_SIZE_BYTES / (1024 * 1024)));
        }
    }

    private void validateAttachmentOwner(Attachment attachment, UserPrincipal principal) {
        if (principal.role() == RoleName.ADMIN) {
            return;
        }
        if (attachment.getUploadedBy().getId().equals(principal.userId())) {
            return;
        }
        throw new ForbiddenException("Only the uploader or an administrator may delete this attachment.");
    }

    private Attachment findAttachmentOrThrow(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", attachmentId));
    }

    private User loadAuthenticatedUser() {
        UserPrincipal principal = currentPrincipal();
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.userId()));
    }

    private UserPrincipal currentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
