package org.son.haruharu.domain.replyrecommendation.implement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonalHistorySelectorTest {

    private static HistoryRecord record(String id, String actor, String at, String... parents) {
        return new HistoryRecord(id, actor, HistoryRecord.KIND_CHAT, HistoryRecord.ROLE_ROOM, at, at, "r-" + id,
                "text " + id, List.of(parents));
    }

    @Test
    @DisplayName("시청자 기록이 없으면 타인의 경험만 있어도 개인화 기록을 만들지 않는다(PER01)")
    void givenOnlyOthersRecords_whenSelect_thenEmpty() {
        var records = List.of(record("chat:1", "20", "2026-11-08T01:00:00Z"),
                record("chat:2", "30", "2026-11-08T01:01:00Z", "chat:1"));

        assertThat(PersonalHistorySelector.select(records, 10L, 20L)).isEmpty();
    }

    @Test
    @DisplayName("본인 기록은 own, 이어진 업로더 기록은 interaction, 그 밖의 부모는 parent_context 로 남긴다")
    void givenThreadedRecords_whenSelect_thenRolesKeepActors() {
        var records = List.of(
                record("chat:1", "30", "2026-11-08T01:00:00Z"),                 // 제3자 질문
                record("chat:2", "10", "2026-11-08T01:01:00Z", "chat:1"),       // 시청자 답(부모 chat:1)
                record("chat:3", "20", "2026-11-08T01:02:00Z", "chat:2"),       // 업로더가 시청자에게 답
                record("chat:4", "20", "2026-11-08T01:03:00Z"),                 // 관계없는 업로더 글
                record("chat:5", "30", "2026-11-08T01:04:00Z"));                // 관계없는 제3자 글

        var selected = PersonalHistorySelector.select(records, 10L, 20L);

        assertThat(selected).extracting(HistoryRecord::sourceId).containsExactly("chat:1", "chat:2", "chat:3");
        assertThat(selected).extracting(HistoryRecord::role)
                .containsExactly(HistoryRecord.ROLE_PARENT_CONTEXT, HistoryRecord.ROLE_OWN, HistoryRecord.ROLE_INTERACTION);
        assertThat(selected.get(0).actorId()).isEqualTo("30");
    }

    @Test
    @DisplayName("부모 관계는 두 단계까지만 따라간다")
    void givenLongChain_whenSelect_thenStopsAfterTwoHops() {
        var records = List.of(
                record("chat:1", "30", "2026-11-08T01:00:00Z"),
                record("chat:2", "31", "2026-11-08T01:01:00Z", "chat:1"),
                record("chat:3", "32", "2026-11-08T01:02:00Z", "chat:2"),
                record("chat:4", "10", "2026-11-08T01:03:00Z", "chat:3"));

        var selected = PersonalHistorySelector.select(records, 10L, 20L);

        assertThat(selected).extracting(HistoryRecord::sourceId).containsExactly("chat:2", "chat:3", "chat:4");
        assertThat(selected.get(0).parentSourceIds()).isEmpty();
    }
}
