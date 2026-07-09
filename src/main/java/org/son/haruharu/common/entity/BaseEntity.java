package org.son.haruharu.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// deleted_at 기반 전역 soft-delete. delete 호출은 물리삭제 대신 deleted_at 세팅으로 전환되고,
// 모든 조회는 deleted_at is null 행만 자동 반환한다. Hibernate가 deleted_at 컬럼을 관리하므로
// 일반 필드로 매핑하지 않는다. 삭제 포함 조회가 필요하면 native query로 우회한다.
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    protected LocalDateTime updatedAt;
}
