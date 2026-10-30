package org.son.haruharu.domain.room.storage;

import org.son.haruharu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.room.implement.FinalSelection;

import java.time.LocalDateTime;

@org.hibernate.annotations.SQLRestriction("member_removed_at is null")
@Entity
@Table(name = "final_selections", uniqueConstraints = @UniqueConstraint(columnNames = "room_member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalSelectionEntity extends BaseEntity {

    /** 관리자 퇴장에 따른 비노출. 원본·일반 삭제 상태는 별도로 보존한다. */
    @Column(name = "member_removed_at", updatable=false)
    private java.time.LocalDateTime memberRemovedAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomMemberId;

    // null = 기권("아무도 선택 안 하기"). 최종선택 확정 행은 남기되 대상이 없음을 표현한다.
    // prod/dev(ddl-auto=validate/update)는 기존 NOT NULL을 자동 해제하지 않으므로 배포 시 수동 DDL 필요:
    //   ALTER TABLE final_selections ALTER COLUMN selected_member_id DROP NOT NULL;
    @Column
    private Long selectedMemberId;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    public static FinalSelectionEntity from(FinalSelection selection) {
        FinalSelectionEntity entity = new FinalSelectionEntity();
        entity.id = selection.id();
        entity.roomMemberId = selection.roomMemberId();
        entity.selectedMemberId = selection.selectedMemberId();
        entity.submittedAt = selection.submittedAt();
        return entity;
    }

    public FinalSelection toDomain() {
        return new FinalSelection(id, roomMemberId, selectedMemberId, submittedAt);
    }
}
