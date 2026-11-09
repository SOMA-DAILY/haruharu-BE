package org.son.haruharu.domain.replyrecommendation.implement;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 개인화 기록을 고른다(명세 5·7장, 연구 personal_history 이식). 허용된 방 기록 안에서
 * 시청자 본인 기록(own)과, 부모·자식 관계로 2단계까지 이어지는 업로더 기록(interaction)·그 밖의 부모 기록(parent_context).
 * 다른 사람의 기록은 문맥 해석에만 쓰이고 시청자 경험의 근거가 되지 않도록 역할을 남긴다.
 */
public final class PersonalHistorySelector {

    private PersonalHistorySelector() {
    }

    public static List<HistoryRecord> select(List<HistoryRecord> allowed, Long viewerId, Long uploaderId) {
        String viewer = String.valueOf(viewerId);
        String uploader = String.valueOf(uploaderId);
        Map<String, HistoryRecord> byId = new LinkedHashMap<>();
        allowed.forEach(record -> byId.put(record.sourceId(), record));

        Set<String> own = new LinkedHashSet<>();
        byId.values().stream().filter(record -> viewer.equals(record.actorId())).forEach(record -> own.add(record.sourceId()));
        if (own.isEmpty()) {
            return List.of();
        }
        Set<String> selected = new LinkedHashSet<>(own);
        for (int round = 0; round < 2; round++) {
            Set<String> parents = new LinkedHashSet<>();
            for (String id : selected) {
                byId.get(id).parentSourceIds().stream().filter(byId::containsKey).forEach(parents::add);
            }
            selected.addAll(parents);
            for (HistoryRecord record : byId.values()) {
                if (uploader.equals(record.actorId())
                        && record.parentSourceIds().stream().anyMatch(selected::contains)) {
                    selected.add(record.sourceId());
                }
            }
        }
        return selected.stream()
                .map(byId::get)
                .sorted(HistoryRecord.CHRONOLOGICAL)
                .map(record -> record.withRole(
                        own.contains(record.sourceId()) ? HistoryRecord.ROLE_OWN
                                : uploader.equals(record.actorId()) ? HistoryRecord.ROLE_INTERACTION
                                : HistoryRecord.ROLE_PARENT_CONTEXT,
                        record.parentSourceIds().stream().filter(selected::contains).toList()))
                .toList();
    }
}
