package org.son.haruharu.admin.domain.room.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.son.haruharu.common.entity.BaseEntity;

/**
 * 방 참여자의 본인 확인 여부만 읽는 user_identities 읽기 전용 매핑(#513).
 * <p>
 * core UserIdentityEntity 는 CI/DI 암호화 변환기(IdentityCrypto)를 끌고 오므로 어드민에 스캔하지 않고,
 * 필요한 칸(id·user_id, 기수 신청자 표의 PASS 이름 user_name #515)만 매핑한다. 행이 있으면 본인 확인 완료다(UserService 와 같은 기준).
 * soft-delete(탈퇴) 행은 BaseEntity 필터로 빠진다. 어드민은 이 테이블에 쓰지 않는다.
 */
@Entity
@Immutable
@Table(name = "user_identities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminUserIdentityView extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 100)
    private String userName;
}
