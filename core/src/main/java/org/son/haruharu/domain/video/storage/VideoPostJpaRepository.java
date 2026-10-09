package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoPostJpaRepository extends JpaRepository<VideoPostEntity, Long> {

    /** 주어진 슬롯 창(slot_start 목록) 중 이 멤버가 업로드(미삭제)한 슬롯들. 슬롯별 잠금 판정용 배치 조회. */
    @Query("select distinct v.slotStart from VideoPostEntity v "
            + "where v.roomMemberId = :roomMemberId and v.slotStart in :slotStarts and v.deletedAt is null")
    List<LocalDateTime> findUploadedSlotStarts(@Param("roomMemberId") Long roomMemberId,
                                               @Param("slotStarts") List<LocalDateTime> slotStarts);

    /** 주어진 방 멤버들과 슬롯 중 활성·비숨김 영상이 하나라도 있는 슬롯들. */
    @Query("select distinct v.slotStart from VideoPostEntity v "
            + "where v.roomMemberId in :roomMemberIds and v.slotStart in :slotStarts "
            + "and v.deletedAt is null and v.isHidden = false")
    List<LocalDateTime> findVisibleSlotStarts(@Param("roomMemberIds") List<Long> roomMemberIds,
                                              @Param("slotStarts") List<LocalDateTime> slotStarts);

    /** 업로드 가능 여부 게이트: 해당 slot_start에 활성(미삭제) 영상이 이미 있는지. 재촬영은 DELETE 선행 필요. */
    boolean existsByRoomMemberIdAndSlotStartAndDeletedAtIsNull(Long roomMemberId, LocalDateTime slotStart);

    /** 피드: 그 방 멤버들이 주어진 슬롯 창(slot_start 목록)에 올린 활성·비숨김 영상. */
    List<VideoPostEntity> findByRoomMemberIdInAndSlotStartInAndDeletedAtIsNullAndIsHiddenFalse(
            List<Long> roomMemberIds, List<LocalDateTime> slotStarts);

    /** 한 멤버의 활성(비삭제·비숨김) 영상 전체를 slot_start 오름차순으로. 매칭 상대 프로필 카드용. */
    List<VideoPostEntity> findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderBySlotStartAsc(
            Long roomMemberId);

    /** 주어진 방의 멤버들 중에 해당 슬롯들에 활성(미삭제) 영상을 올린 멤버의 id **/
    @Query("select distinct v.roomMemberId from VideoPostEntity v " +
            "where v.roomMemberId in :roomMemberIds " +
            "and v.slotStart in :slotStarts " +
            "and v.deletedAt is null")
    List<Long> findUploadedRoomMemberIds(@Param("roomMemberIds") List<Long> roomMemberIds,
                                         @Param("slotStarts") List<LocalDateTime> slotStarts);
}
