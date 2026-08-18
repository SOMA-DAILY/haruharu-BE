package org.son.haruharu.domain.demoaccess.implement;

import java.util.Optional;

public interface DemoAccessCodeRepository {

    /** 현재 설정된 데모 접근 코드(단일 값). 미설정이면 empty → 데모 로그인은 닫힌 상태다. */
    Optional<DemoAccessCode> findCurrent();
}
