package org.son.haruharu.domain.room.implement;

import java.security.SecureRandom;

import org.son.haruharu.domain.room.implement.exception.InviteCodeGenerationFailedException;
import org.springframework.stereotype.Component;

/**
 * 초대코드 문자열 생성기(#318). 운영진이 구두·문자로 전달하는 코드라 <b>혼동문자(O·0·I·1)를 뺀</b>
 * 32글자 알파벳에서 6자리를 뽑는다(32^6 ≈ 10.7억).
 * <p>
 * 중복은 생성 시점 조회로 걸러 재시도하고, 최종 방어선은 {@code room_invites.code} UNIQUE 제약이다
 * (조회~저장 사이 경쟁은 DB가 막는다). 코드 형식은 도메인 규칙이므로 implement 계층이 소유한다.
 */
@Component
public class InviteCodeGenerator {

    /** 혼동문자 O·0·I·1 제외 32글자. 길이가 2의 거듭제곱이라 무작위 편향 없이 인덱싱된다. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;

    /** 중복 재시도 한도. 초과 시 예외 — 정상 상황에서는 도달하지 않는다. */
    public static final int MAX_ATTEMPTS = 5;

    private final RoomInviteReader roomInviteReader;
    private final SecureRandom random = new SecureRandom();

    public InviteCodeGenerator(RoomInviteReader roomInviteReader) {
        this.roomInviteReader = roomInviteReader;
    }

    /** 아직 쓰이지 않은 코드를 반환한다. 한도까지 계속 충돌하면 InviteCodeGenerationFailedException. */
    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (roomInviteReader.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new InviteCodeGenerationFailedException();
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
