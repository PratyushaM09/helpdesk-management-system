package com.helpdesk.attachment.mapper;

import com.helpdesk.attachment.dto.response.AttachmentResponse;
import com.helpdesk.attachment.entity.Attachment;
import org.springframework.stereotype.Component;

@Component
public class AttachmentMapperImpl implements AttachmentMapper {

    @Override
    public AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getMimeType(),
                attachment.getSizeBytes(),
                attachment.getUploadedBy().getName(),
                attachment.getCreatedAt()
        );
    }
}
