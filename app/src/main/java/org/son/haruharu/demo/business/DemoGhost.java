package org.son.haruharu.demo.business;

/** 데모 방을 채우는 유령 멤버 한 명(#352 PR2). userId 와 방 구성용 성별. */
public record DemoGhost(Long userId, String gender) {
}
