package org.son.haruharu.domain.room.implement;

/**
 * 방 멤버가 들어온 경로(#513). 초대코드 정원은 INVITE 인원만 센다 — 운영진이 앱 신청자를 넣어도 웹 몫 자리가 줄지 않게.
 * 이 칸이 생기기 전의 멤버는 모두 초대코드로 들어왔으므로 INVITE 로 백필한다.
 */
public enum RoomEntryPath {
    /** 초대코드(웹 신청·공용 코드)로 입장 */
    INVITE,
    /** 앱 기수 신청을 운영진이 방에 배정 */
    APPLICATION,
    /** 사전 동의 후 관리자 지명 제안을 선착순 수락한 무료 중간 참여 */
    MID_PARTICIPATION
}
