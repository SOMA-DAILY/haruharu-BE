package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 연(또는 이미 있던) 1:1 대화방(#540). */
public record PairChatRoomResponse(@Schema(description = "PAIR 채팅방 id", example = "345") Long chatRoomId) {}
