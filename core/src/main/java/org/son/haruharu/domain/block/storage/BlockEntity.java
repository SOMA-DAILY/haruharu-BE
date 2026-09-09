package org.son.haruharu.domain.block.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.block.implement.Block;

@Entity
@Table(name = "user_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_blocks_blocker_blocked",
                columnNames = {"blockerUserId", "blockedUserId"}),
        indexes = {
                @Index(name = "idx_user_blocks_blocker", columnList = "blockerUserId"),
                @Index(name = "idx_user_blocks_blocked", columnList = "blockedUserId")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long blockerUserId;

    @Column(nullable = false)
    private Long blockedUserId;

    public static BlockEntity from(Block block) {
        var entity = new BlockEntity();
        entity.id = block.id();
        entity.blockerUserId = block.blockerUserId();
        entity.blockedUserId = block.blockedUserId();
        entity.createdAt = block.createdAt();
        return entity;
    }

    public Block toDomain() {
        return new Block(id, blockerUserId, blockedUserId, createdAt);
    }
}
