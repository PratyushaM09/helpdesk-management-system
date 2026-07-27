package com.helpdesk.role.entity;

import com.helpdesk.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fixed, seeded reference data (three rows: USER, SUPPORT_ENGINEER, ADMIN) —
 * kept as a table rather than a hardcoded enum column on {@code User} so a
 * future role/permission expansion is a data change, not a schema migration
 * touching every foreign key (05-Database.md §3).
 * <p>
 * {@code system} is set only by {@code RoleSeeder}, never by a request DTO —
 * it is what {@code RoleServiceImpl.deleteRole} checks before allowing a
 * delete (Phase 2, Milestone 2). Every row is {@code true} today since there
 * is no create-role endpoint, but the guard reads this flag rather than
 * hardcoding "reject every delete," so it reflects the real invariant, not a
 * stand-in for it.
 */
@Entity
@Table(name = "roles")
public class Role extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 30, unique = true)
    private RoleName name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_system")
private boolean system = true;

    protected Role() {
    }

    public Role(RoleName name, String description) {
        this(name, description, true);
    }

    /** {@code system} escape hatch for infrastructure/tests; every ordinary caller should use the two-arg constructor. */
    public Role(RoleName name, String description, boolean system) {
        this.name = name;
        this.description = description;
        this.system = system;
    }

    public Long getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSystem() {
        return system;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Role other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Role{id=%d, name=%s}".formatted(id, name);
    }
}
