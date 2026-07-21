package com.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The single {@link PasswordEncoder} bean the whole application shares
 * (11-Development-Rules.md §12 — "always via the injected PasswordEncoder
 * bean"; SDR-001 — BCrypt). Registered independently of authentication
 * itself: hashing is a security utility needed the moment any password is
 * first stored (this milestone's user creation), well before login/JWT
 * exist — nothing here wires a login flow or a {@code SecurityFilterChain}.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
