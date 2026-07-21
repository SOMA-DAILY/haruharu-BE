package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VideoPostJpaRepository extends JpaRepository<VideoPostEntity, Long> {

    long countByRoomMemberIdAndDayNumberAndDeletedAtIsNull(Long roomMemberId, int dayNumber);

    @Query("select distinct v.slotIndex from VideoPostEntity v "
            + "where v.roomMemberId = :roomMemberId and v.dayNumber = :dayNumber and v.deletedAt is null")
    List<Integer> findUploadedSlotIndexes(@Param("roomMemberId") Long roomMemberId,
                                          @Param("dayNumber") int dayNumber);

    boolean existsByRoomMemberIdAndDayNumberAndSlotIndexAndDeletedAtIsNull(
            Long roomMemberId, int dayNumber, int slotIndex);

    boolean existsByRoomMemberIdAndDayNumberAndSlotIndexAndMissionIdIsNotNull(
            Long roomMemberId, int dayNumber, int slotIndex);

    List<VideoPostEntity> findByRoomMemberIdInAndDayNumberAndDeletedAtIsNullAndIsHiddenFalse(
            List<Long> roomMemberIds, int dayNumber);

    /** 한 멤버의 활성(비삭제·비숨김) 영상 전체를 Day·슬롯 오름차순으로. 매칭 상대 프로필 카드용. */
    List<VideoPostEntity> findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderByDayNumberAscSlotIndexAsc(
            Long roomMemberId);

    // 벌크 DELETE(즉시 실행). 데모 리셋에서 삭제 직후 같은 Day를 재시드하므로, 삭제가 재시드 INSERT보다
    // 먼저 실행되도록 파생 delete 대신 벌크로 즉시 삭제한다.
    // clearAutomatically는 쓰지 않는다 — 리셋은 같은 트랜잭션에서 방 demoDay 갱신 등 아직 flush 안 된 변경을
    // 안고 있어, 컨텍스트를 비우면 그 변경이 폐기된다. 리셋 경로는 삭제 대상 영상을 이후 재조회하지 않아 stale도 없다.
    @Modifying
    @Query("delete from VideoPostEntity v where v.roomMemberId in :roomMemberIds")
    void deleteByRoomMemberIdIn(@Param("roomMemberIds") List<Long> roomMemberIds);
}
