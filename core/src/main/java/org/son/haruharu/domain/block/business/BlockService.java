package org.son.haruharu.domain.block.business;

import org.son.haruharu.domain.block.business.command.CreateBlockCommand;
import org.son.haruharu.domain.block.business.dto.BlockResponse;
import org.son.haruharu.domain.block.business.dto.BlocksResponse;
import org.son.haruharu.domain.block.business.exception.BlockAlreadyExistsException;
import org.son.haruharu.domain.block.business.exception.CannotBlockSelfException;
import org.son.haruharu.domain.block.implement.Block;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.block.implement.BlockWriter;
import org.son.haruharu.domain.block.storage.exception.BlockNotFoundException;
import org.son.haruharu.domain.user.implement.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 차단(#408). 등록·목록 조회까지가 이 도메인의 범위 — 콘텐츠 숨김은 FE가 차단 목록으로 필터하고,
 * 1:1 제한·알림 억제·매칭 연계는 각 도메인 서비스가 BlockReader 게이트로 처리한다.
 * 중복 차단: 차단자·대상당 1회. 사전 exists 검사(깨끗한 409)에 더해 DB 유니크 제약이
 * 다중 인스턴스 동시 요청의 이중 등록을 차단한다(check-then-insert만으론 부족 — PR #171 원칙).
 */
@Service
@Transactional(readOnly = true)
public class BlockService {

    private final BlockReader blockReader;
    private final BlockWriter blockWriter;
    private final UserReader userReader;
    private final Clock clock;

    public BlockService(BlockReader blockReader, BlockWriter blockWriter,
                        UserReader userReader, Clock clock) {
        this.blockReader = blockReader;
        this.blockWriter = blockWriter;
        this.userReader = userReader;
        this.clock = clock;
    }

    @Transactional
    public BlockResponse block(Long blockerUserId, CreateBlockCommand command) {
        if (blockerUserId.equals(command.blockedUserId())) {
            throw new CannotBlockSelfException();
        }
        userReader.getById(command.blockedUserId()); // 대상 존재 검증(없으면 404)
        if (blockReader.hasBlocked(blockerUserId, command.blockedUserId())) {
            throw new BlockAlreadyExistsException();
        }
        Block saved = blockWriter.save(Block.of(
                blockerUserId, command.blockedUserId(), LocalDateTime.now(clock)));
        return BlockResponse.from(saved);
    }

    /** 차단 해제 — 내가 건 차단만 삭제한다. 상대가 나를 차단한 관계는 남으므로 1:1 제한이 유지될 수 있다. */
    @Transactional
    public void unblock(Long blockerUserId, Long blockedUserId) {
        if (blockWriter.delete(blockerUserId, blockedUserId) == 0) {
            throw new BlockNotFoundException();
        }
    }

    public BlocksResponse getMyBlocks(Long userId) {
        return new BlocksResponse(List.copyOf(blockReader.getBlockedUserIds(userId)));
    }
}
