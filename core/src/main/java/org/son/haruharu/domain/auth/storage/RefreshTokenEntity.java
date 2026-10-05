package org.son.haruharu.domain.auth.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.auth.implement.RefreshToken;

import java.time.LocalDateTime;

// 주의: ERD의 refresh_tokens 테이블에는 updated_at 컬럼이 없으나,
// BaseEntity 상속 규칙에 따라 updated_at 컬럼이 추가됩니다.
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public static RefreshTokenEntity from(RefreshToken refreshToken) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.userId = refreshToken.userId();
        entity.tokenHash = refreshToken.tokenHash();
        entity.expiresAt = refreshToken.expiresAt();
        entity.revokedAt = refreshToken.revokedAt();
        return entity;
    }

    public RefreshToken toDomain() {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revokedAt);
    }
}
