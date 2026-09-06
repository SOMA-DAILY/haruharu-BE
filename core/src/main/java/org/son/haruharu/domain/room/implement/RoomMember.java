package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.son.haruharu.domain.room.implement.exception.InvalidRoomMemberGenderException;

public record RoomMember(
        Long id,
        Long roomId,
        Long userId,
        String gender,
        String pseudonym,
        LocalDate birthday,
        LocalDateTime joinedAt
) {
    /**
     * 부여 가능한 성별. 입장 시점의 null(미수집)은 별개다 — 여기 담긴 값만 "부여된 성별"로 인정한다.
     * enum 전환은 final_selection·admin 조회 등 파급이 커서 보류하고(#318), 허용값은 도메인이 소유한다.
     */
    public static final Set<String> SUPPORTED_GENDERS = Set.of("MALE", "FEMALE");

    /**
     * 가명 미배정(성별 미상) 멤버의 표시 이름(#393). 매칭 전에는 어떤 경우에도 실명(닉네임)으로 폴백하지 않는다 —
     * 실명 폴백은 방 세션 익명성을 깨므로, 가명이 없으면 이 중립 플레이스홀더를 쓴다.
     */
    public static final String ANONYMOUS_NAME = "익명";

    /** 방 입장. 성별·생년월일은 본인인증값 스냅샷. 가명(pseudonym)은 입장 시 배정하며 여기선 null(호출측이 withPseudonym으로 부여). */
    public static RoomMember of(Long roomId, Long userId, String gender, LocalDate birthday, LocalDateTime joinedAt) {
        return new RoomMember(null, roomId, userId, gender, null, birthday, joinedAt);
    }

    /**
     * 성별 부여(#318). 초대코드 입장은 gender=null 이므로(온보딩 미수집) 운영진이 신청폼 기준으로 채운다.
     * <p>
     * 허용값 검증은 <b>도메인이 직접</b> 한다 — 최종선택의 "반대 성별" 판정이 이 스냅샷을 그대로 쓰므로
     * 호출측(admin·app·배치)이 무엇이든 임의 문자열이 들어오면 안 된다.
     */
    public RoomMember withGender(String gender) {
        // Set.of(...).contains(null) 은 NPE 라 null 을 먼저 거른다 — 부여 API 의 gender 누락이 500 으로 새지 않게.
        if (gender == null || !SUPPORTED_GENDERS.contains(gender)) {
            throw new InvalidRoomMemberGenderException();
        }
        return new RoomMember(id, roomId, userId, gender, pseudonym, birthday, joinedAt);
    }

    /** 방 세션 가명 부여(#393). 입장 시 성별·순번으로 배정한다(PseudonymPool). */
    public RoomMember withPseudonym(String pseudonym) {
        return new RoomMember(id, roomId, userId, gender, pseudonym, birthday, joinedAt);
    }

    /** 매칭 전 표시 이름(#393). 가명이 있으면 가명, 없으면(성별 미상) 중립 플레이스홀더 — 절대 실명으로 폴백하지 않는다. */
    public String displayPseudonym() {
        return pseudonym != null ? pseudonym : ANONYMOUS_NAME;
    }
}
