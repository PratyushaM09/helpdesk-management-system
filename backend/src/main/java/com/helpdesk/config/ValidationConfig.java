package com.helpdesk.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Wires Jakarta Bean Validation's message interpolation to Spring's own
 * {@link MessageSource} (backed by {@code messages.properties}) instead of
 * the Bean Validation spec's default {@code ValidationMessages.properties}
 * bundle — so every validation message, custom or standard, lives in the
 * one message file the rest of the application already uses, per task 5.
 * <p>
 * Defining this bean deliberately overrides Spring Boot's own
 * auto-configured {@code Validator} bean ({@code ValidationAutoConfiguration}
 * backs off via {@code @ConditionalOnMissingBean} the moment one is
 * present) — this is the standard, documented way to customize it, not a
 * workaround. {@code MethodValidationPostProcessor} (which is what makes
 * {@code @Validated} + constrained method parameters throw
 * {@code ConstraintViolationException}) is still auto-configured by Spring
 * Boot and automatically picks up this same validator bean — no separate
 * wiring needed for that.
 */
@Configuration
public class ValidationConfig {

    @Bean
    public LocalValidatorFactoryBean localValidatorFactoryBean(@NonNull MessageSource messageSource) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        return factoryBean;
    }
}
