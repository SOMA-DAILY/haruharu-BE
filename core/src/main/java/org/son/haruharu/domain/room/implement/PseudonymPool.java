package org.son.haruharu.domain.room.implement;

import java.util.List;

/**
 * 방 세션(3일) 동안 멤버를 가리는 가명 풀(#393). 입장 순번(같은 성별 기존 멤버 수, 0-based)으로 배정한다.
 * <p>
 * 정원은 어드민이 초대코드 발급 수로 통제(3:3 상정)하지만, 정원 미확정 대비 안전망으로 풀 크기를 성별당 6개로 넉넉히 두고,
 * 그마저 초과하면 풀을 순환하며 숫자 접미사를 붙여 <b>어떤 순번이든 절대 배정에 실패하지 않게</b> 한다.
 * 성별이 미상(null·미지원)이면 배정할 수 없어 null을 반환한다(가명 없이 저장 — 운영진이 성별 부여 후 재계산 여지).
 */
public final class PseudonymPool {

    private static final List<String> MALE = List.of("민준", "정석", "현우", "도윤", "시우", "하준");
    private static final List<String> FEMALE = List.of("유진", "지우", "서연", "서윤", "하은", "지아");

    private PseudonymPool() {
    }

    /**
     * @param gender    "MALE"/"FEMALE" (RoomMember.SUPPORTED_GENDERS와 동일). 그 외·null은 배정 불가.
     * @param joinIndex 같은 성별 기존 멤버 수(0-based 입장 순번)
     * @return 배정된 가명, 성별 미상이면 null
     */
    public static String assign(String gender, int joinIndex) {
        List<String> pool = poolFor(gender);
        if (pool == null) {
            return null;
        }
        String base = pool.get(joinIndex % pool.size());
        int cycle = joinIndex / pool.size();
        return cycle == 0 ? base : base + (cycle + 1);
    }

    private static List<String> poolFor(String gender) {
        if ("MALE".equals(gender)) {
            return MALE;
        }
        if ("FEMALE".equals(gender)) {
            return FEMALE;
        }
        return null;
    }
}
