package com.helpdesk.account.mapper;

import com.helpdesk.account.dto.response.AccountProfileResponse;
import com.helpdesk.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AccountMapperImpl implements AccountMapper {

    @Override
    public AccountProfileResponse toAccountProfile(User user) {
        return new AccountProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().getName(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
