package com.helpdesk.attachment.mapper;

import com.helpdesk.attachment.dto.response.AttachmentResponse;
import com.helpdesk.attachment.entity.Attachment;

/**
 * Converts {@link Attachment} entities into API response DTOs. No request
 * mapping - uploads are multipart requests, handled directly by the Service
 * layer, never via a JSON request DTO. Deliberately never maps
 * {@code storageKey} - it is internal infrastructure ({@code AttachmentResponse}'s
 * own Javadoc), never client-facing.
 */
public interface AttachmentMapper {

    /** Projects an {@link Attachment} to its API-safe response shape. */
    AttachmentResponse toResponse(Attachment attachment);
}
