package org.son.haruharu.domain.identity.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.identity.implement.UserIdentity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_identities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String ci;

    @Column(nullable = false)
    private String di;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDate birthday;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(nullable = false, length = 100)
    private String userName;

    @Column(nullable = false, length = 10)
    private String operator;

    @Column(nullable = false)
    private boolean isForeigner;

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    public static UserIdentityEntity from(UserIdentity userIdentity) {
        throw new UnsupportedOperationException("TODO");
    }

    public UserIdentity toDomain() {
        throw new UnsupportedOperationException("TODO");
    }
}
