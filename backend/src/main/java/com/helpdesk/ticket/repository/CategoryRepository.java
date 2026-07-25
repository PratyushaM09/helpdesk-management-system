package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@code findByNameIgnoreCase} backs {@code CategorySeeder}'s idempotent seed
 * check (05-Database.md §4: case-insensitive uniqueness) — the same shape as
 * {@code RoleRepository.findByName}.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);
}
