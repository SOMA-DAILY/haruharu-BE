package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.exception.InvalidRoomMemberGenderException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomMemberTest {

    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 8, 20, 19, 0);

    @Test
    @DisplayName("withGender는 성별만 바꾸고 나머지 필드는 보존한다 (#318 운영진 성별 부여)")
    void givenMemberWithoutGender_whenWithGender_thenGenderAssignedAndFieldsKept() {
        RoomMember member = new RoomMember(3L, 7L, 901L, null, null, LocalDate.of(2000, 1, 1), JOINED_AT);

        RoomMember assigned = member.withGender("FEMALE");

        assertThat(assigned.gender()).isEqualTo("FEMALE");
        assertThat(assigned.id()).isEqualTo(3L);
        assertThat(assigned.roomId()).isEqualTo(7L);
        assertThat(assigned.userId()).isEqualTo(901L);
        assertThat(assigned.birthday()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(assigned.joinedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    @DisplayName("withGender는 이미 부여된 성별도 교체한다 (운영진 정정)")
    void givenMemberWithGender_whenWithGender_thenReplaced() {
        RoomMember member = new RoomMember(3L, 7L, 901L, "MALE", null, null, JOINED_AT);

        assertThat(member.withGender("FEMALE").gender()).isEqualTo("FEMALE");
    }

    @Test
    @DisplayName("withGender에 MALE·FEMALE 외의 값을 주면 도메인이 거부한다")
    void givenUnsupportedGender_whenWithGender_thenThrows() {
        RoomMember member = new RoomMember(3L, 7L, 901L, null, null, null, JOINED_AT);

        assertThatThrownBy(() -> member.withGender("OTHER"))
                .isInstanceOf(InvalidRoomMemberGenderException.class);
        assertThatThrownBy(() -> member.withGender("female"))
                .isInstanceOf(InvalidRoomMemberGenderException.class);
    }

    @Test
    @DisplayName("withGender에 null을 주면 도메인이 거부한다 (미수집 상태로 되돌리지 못한다)")
    void givenNullGender_whenWithGender_thenThrows() {
        RoomMember member = new RoomMember(3L, 7L, 901L, "MALE", null, null, JOINED_AT);

        assertThatThrownBy(() -> member.withGender(null))
                .isInstanceOf(InvalidRoomMemberGenderException.class);
    }

    @Test
    @DisplayName("withPseudonym은 가명만 바꾸고 나머지 필드는 보존한다 (#393)")
    void givenMember_whenWithPseudonym_thenPseudonymAssignedAndFieldsKept() {
        RoomMember member = new RoomMember(3L, 7L, 901L, "MALE", null, LocalDate.of(2000, 1, 1), JOINED_AT);

        RoomMember assigned = member.withPseudonym("민준");

        assertThat(assigned.pseudonym()).isEqualTo("민준");
        assertThat(assigned.gender()).isEqualTo("MALE");
        assertThat(assigned.id()).isEqualTo(3L);
        assertThat(assigned.birthday()).isEqualTo(LocalDate.of(2000, 1, 1));
    }

    @Test
    @DisplayName("displayPseudonym은 가명이 있으면 가명을, 없으면 익명 플레이스홀더를 반환한다 (실명 폴백 금지 — #393)")
    void givenPseudonymOrNull_whenDisplayPseudonym_thenReturnsPseudonymOrAnonymous() {
        RoomMember withName = new RoomMember(3L, 7L, 901L, "MALE", "현우", null, JOINED_AT);
        RoomMember withoutName = new RoomMember(3L, 7L, 901L, null, null, null, JOINED_AT);

        assertThat(withName.displayPseudonym()).isEqualTo("현우");
        assertThat(withoutName.displayPseudonym()).isEqualTo(RoomMember.ANONYMOUS_NAME);
    }

    // ── #513 입장 경로 ──

    @Test
    @DisplayName("초대코드 입장 멤버는 입장 경로 INVITE 이고 신청 id 가 없다")
    void givenInviteJoin_whenOf_thenEntryPathInviteWithoutApplication() {
        RoomMember member = RoomMember.of(7L, 901L, "MALE", null, JOINED_AT);

        assertThat(member.entryPath()).isEqualTo(RoomEntryPath.INVITE);
        assertThat(member.applicationId()).isNull();
    }

    @Test
    @DisplayName("기존 7칸 생성자는 초대코드 입장으로 본다(기존 행 백필과 같은 기본값)")
    void givenLegacyConstructor_whenCreate_thenEntryPathInvite() {
        RoomMember member = new RoomMember(3L, 7L, 901L, null, null, null, JOINED_AT);

        assertThat(member.entryPath()).isEqualTo(RoomEntryPath.INVITE);
        assertThat(member.applicationId()).isNull();
    }

    @Test
    @DisplayName("앱 신청으로 들어온 멤버는 입장 경로 APPLICATION 과 신청 id 를 갖는다")
    void givenApplication_whenFromApplication_thenEntryPathApplicationWithId() {
        RoomMember member = RoomMember.fromApplication(7L, 901L, "FEMALE", LocalDate.of(2001, 3, 4), 55L, JOINED_AT);

        assertThat(member.entryPath()).isEqualTo(RoomEntryPath.APPLICATION);
        assertThat(member.applicationId()).isEqualTo(55L);
        assertThat(member.gender()).isEqualTo("FEMALE");
        assertThat(member.birthday()).isEqualTo(LocalDate.of(2001, 3, 4));
    }

    @Test
    @DisplayName("앱 신청 배정은 신청 id 가 없으면 거부한다(쓰는 쪽 검증)")
    void givenNoApplicationId_whenFromApplication_thenThrows() {
        assertThatThrownBy(() -> RoomMember.fromApplication(7L, 901L, "MALE", null, null, JOINED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DB 에서 복원하는 생성자는 짝이 어긋난 행도 예외 없이 읽는다 — 행 하나 때문에 방 조회 전체가 깨지지 않게")
    void givenMismatchedStoredRow_whenRestore_thenReadsWithoutThrowing() {
        RoomMember applicationWithoutId = new RoomMember(1L, 7L, 901L, null, null, null, JOINED_AT,
                RoomEntryPath.APPLICATION, null);
        RoomMember inviteWithId = new RoomMember(2L, 7L, 902L, null, null, null, JOINED_AT, RoomEntryPath.INVITE, 55L);

        assertThat(applicationWithoutId.entryPath()).isEqualTo(RoomEntryPath.APPLICATION);
        assertThat(inviteWithId.applicationId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("성별·가명을 부여해도 입장 경로와 신청 id 는 그대로다")
    void givenApplicationMember_whenWithGenderAndPseudonym_thenEntryPathKept() {
        RoomMember member = RoomMember.fromApplication(7L, 901L, null, null, 55L, JOINED_AT);

        RoomMember updated = member.withGender("MALE").withPseudonym("민준");

        assertThat(updated.entryPath()).isEqualTo(RoomEntryPath.APPLICATION);
        assertThat(updated.applicationId()).isEqualTo(55L);
    }
}
