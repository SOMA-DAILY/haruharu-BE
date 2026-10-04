package org.son.haruharu.domain.chat.storage;

/** 반응 집계 쿼리 프로젝션(#456). */
public interface MessageReactionCountView {
    Long getMessageId();

    String getReactionType();

    long getCount();
}
