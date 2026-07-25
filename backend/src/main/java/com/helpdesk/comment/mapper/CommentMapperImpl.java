package com.helpdesk.comment.mapper;

import com.helpdesk.comment.dto.request.CreateCommentRequest;
import com.helpdesk.comment.dto.request.UpdateCommentRequest;
import com.helpdesk.comment.dto.response.CommentResponse;
import com.helpdesk.comment.entity.Comment;
import com.helpdesk.comment.entity.CommentVisibility;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CommentMapperImpl implements CommentMapper {

    @Override
    public Comment toEntity(CreateCommentRequest request, Ticket ticket, User author, CommentVisibility resolvedVisibility) {
        return new Comment(ticket, author, request.content(), resolvedVisibility);
    }

    @Override
    public void updateEntity(Comment comment, UpdateCommentRequest request, Instant now) {
        comment.edit(request.content(), now);
    }

    @Override
    public CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getAuthor().getName(),
                comment.getContent(),
                comment.getVisibility(),
                comment.isEdited(),
                comment.getEditedAt(),
                comment.getCreatedAt()
        );
    }
}
