package org.son.haruharu.domain.block.business.dto;

import org.son.haruharu.domain.block.implement.Block;

/** 차단 등록 결과. 전역 SNAKE_CASE 직렬화로 block_id로 응답된다. */
public record BlockResponse(Long blockId) {

    public static BlockResponse from(Block block) {
        return new BlockResponse(block.id());
    }
}
