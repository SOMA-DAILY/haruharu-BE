package org.son.haruharu.admin.domain.account.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminAccountStatus;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.son.haruharu.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccountEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminAccountStatus status;

    @Column
    private LocalDateTime lastLoginAt;

    public static AdminAccountEntity from(AdminAccount account) {
        var entity = new AdminAccountEntity();
        entity.id = account.id();
        entity.email = account.email();
        entity.passwordHash = account.passwordHash();
        entity.name = account.name();
        entity.role = account.role();
        entity.status = account.status();
        entity.lastLoginAt = account.lastLoginAt();
        return entity;
    }

    public AdminAccount toDomain() {
        return new AdminAccount(id, email, passwordHash, name, role, status, lastLoginAt);
    }
}
