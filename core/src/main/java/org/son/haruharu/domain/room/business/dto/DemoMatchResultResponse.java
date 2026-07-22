package org.son.haruharu.domain.room.business.dto;

/**
 * 데모 최종선택 결과 응답(#266). chatRoomId는 MATCHED일 때만 값이 있고 FAILED면 null이다
 * (FE가 이 값으로 채팅 화면 진입 여부를 판단한다).
 */
public record DemoMatchResultResponse(
        Long roomId,
        String outcome,
        Long chatRoomId
) {
}
