package org.son.haruharu.domain.demoaccess.implement;

/**
 * 데모 로그인을 여는 접근 코드(#352). 운영진이 어드민에서 설정·교체하고, 데모 로그인이 대조한다.
 * <p>
 * core 는 이 값을 <b>보관만</b> 한다 — 읽거나 분기하는 도메인 로직이 없다(데모 무관 원칙). 실제 소비는 app 의
 * 데모 모듈(대조)과 admin(설정)뿐이다.
 */
public record DemoAccessCode(Long id, String code) {

    public static DemoAccessCode of(String code) {
        return new DemoAccessCode(null, code);
    }
}
