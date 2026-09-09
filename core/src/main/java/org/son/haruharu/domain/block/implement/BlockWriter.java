package org.son.haruharu.domain.block.implement;

import org.springframework.stereotype.Component;

@Component
public class BlockWriter {

    private final BlockRepository blockRepository;

    public BlockWriter(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    public Block save(Block block) {
        return blockRepository.save(block);
    }

    /** 차단 해제(물리 삭제). 반환값은 삭제 행 수 — 0이면 애초에 차단하지 않은 대상. */
    public int delete(Long blockerUserId, Long blockedUserId) {
        return blockRepository.hardDeleteByBlockerAndBlocked(blockerUserId, blockedUserId);
    }
}
