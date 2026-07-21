package com.helpdesk.util;

import com.helpdesk.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Guards every paginated list endpoint's client-supplied sort fields against
 * an explicit allow-list (11-Development-Rules.md §7.5) — a raw
 * client-supplied sort property is never passed into a repository query
 * unchecked, since Spring Data would otherwise happily order by any real
 * entity column a client names, including a sensitive one (e.g. a User's
 * {@code passwordHash}).
 */
public final class SortValidator {

    private SortValidator() {
    }

    /**
     * @throws BadRequestException if any requested sort property is not in
     *                             {@code allowedProperties}
     */
    public static void validate(Sort sort, Set<String> allowedProperties) {
        for (Sort.Order order : sort) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BadRequestException("Unsupported sort field: '%s'".formatted(order.getProperty()));
            }
        }
    }
}
