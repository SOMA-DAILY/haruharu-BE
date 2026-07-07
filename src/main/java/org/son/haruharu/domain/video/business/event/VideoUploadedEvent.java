package org.son.haruharu.domain.video.business.event;

/** 영상 업로드가 저장될 때 발행. 그 Day의 마지막 슬롯을 채웠는지(완주) 검사해 구름 보상을 지급하는 트리거. */
public record VideoUploadedEvent(Long roomMemberId, int dayNumber) {
}
