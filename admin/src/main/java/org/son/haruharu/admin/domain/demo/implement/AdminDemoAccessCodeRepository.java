package org.son.haruharu.admin.domain.demo.implement;

import java.util.Optional;

import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;

/** 어드민의 데모 접근 코드 관리 포트(#352). 도메인 객체만 다루며 storage 가 Entity 변환·단일행 upsert 를 구현한다. */
public interface AdminDemoAccessCodeRepository {

    Optional<DemoAccessCode> findCurrent();

    /** 단일 행 upsert — 기존 코드가 있으면 값만 교체하고, 없으면 최초 생성한다. */
    DemoAccessCode save(DemoAccessCode code);
}
