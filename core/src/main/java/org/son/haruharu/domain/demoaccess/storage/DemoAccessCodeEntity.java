package org.son.haruharu.domain.demoaccess.storage;

import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 데모 접근 코드 저장(#352). 단일 행으로 운영하며 어드민이 교체(update)한다. */
@Entity
@Table(name = "demo_access_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemoAccessCodeEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String code;

    public static DemoAccessCodeEntity from(DemoAccessCode demoAccessCode) {
        DemoAccessCodeEntity entity = new DemoAccessCodeEntity();
        entity.id = demoAccessCode.id();
        entity.code = demoAccessCode.code();
        return entity;
    }

    public DemoAccessCode toDomain() {
        return new DemoAccessCode(id, code);
    }

    /** 어드민 코드 교체용 — 단일 행을 유지하며 값만 갱신한다. */
    public void updateCode(String code) {
        this.code = code;
    }
}
