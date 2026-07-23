package com.helpdesk.security;

import com.helpdesk.user.entity.User;
import com.helpdesk.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an email to a {@link UserPrincipal} — the one place a {@link User}
 * row is converted to the security layer's thin projection. Deliberately
 * contains no authentication logic of its own (no password check, no
 * lockout decision): it only answers "who is this," never "should this
 * succeed" — that judgment belongs to whatever calls it.
 * <p>
 * Throws Spring Security's own {@link UsernameNotFoundException}, not one
 * of this project's {@code ApplicationException} subtypes — this is what
 * lets {@code DaoAuthenticationProvider}'s built-in
 * {@code hideUserNotFoundExceptions} behavior translate "no such email" and
 * "wrong password" into the same generic failure automatically
 * (07-Security-Architecture.md §3.2 step 2's enumeration-resistance
 * requirement), with no bespoke code needed here to achieve it.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: '%s'".formatted(email)));
        return toPrincipal(user);
    }

    /**
     * Resolves a numeric user id to a {@link UserPrincipal} — used by
     * {@code JwtAuthenticationFilter} to re-check the current row
     * (tokenVersion, status) on every request, since a JWT's {@code sub}
     * claim is a user id, not an email. Not part of the
     * {@link UserDetailsService} contract (which is username/email-keyed,
     * used only by the login flow via {@code AuthenticationManager}) — an
     * additional, equally-thin lookup path through the same
     * "row → principal" conversion, so there remains exactly one place that
     * conversion happens.
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: '%d'".formatted(userId)));
        return toPrincipal(user);
    }

    private UserPrincipal toPrincipal(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().getName(),
                user.getStatus(),
                user.getTokenVersion()
        );
    }
}
