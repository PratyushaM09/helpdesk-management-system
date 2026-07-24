package com.helpdesk.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Exposes the one {@link AuthenticationManager} bean {@code AuthenticationServiceImpl}
 * needs to verify credentials — carved out narrowly from
 * {@code SecurityConfig}'s own bean set. No {@code SecurityFilterChain}, no
 * route rules, no filters, no entry point/access-denied handler live here;
 * those are {@code SecurityConfig}'s.
 * <p>
 * Necessary, not optional: {@code SecurityConfig} defines the application's
 * one {@code SecurityFilterChain} bean, which makes Spring Boot's
 * {@code @ConditionalOnDefaultWebSecurity} auto-configuration (the thing
 * that would otherwise wire an {@code AuthenticationManager} for free) back
 * off entirely. Without this class, {@code AuthenticationServiceImpl}
 * cannot be constructed and the application context fails to start.
 */
@Configuration
public class AuthenticationManagerConfig {

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
