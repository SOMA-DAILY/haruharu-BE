package org.son.haruharu.domain.replyrecommendation.storage;

import java.util.List;

import org.son.haruharu.domain.replyrecommendation.implement.AiReplyJson;
import org.son.haruharu.domain.replyrecommendation.implement.EvidenceOwner;
import org.son.haruharu.domain.replyrecommendation.implement.HistoryRecord;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 분석·개인화 입력의 원문 출처(원문 대신 해시)를 남기는 SQL. 두 저장소가 함께 쓴다. */
final class EvidenceSql {

    private EvidenceSql() {
    }

    static void insert(JdbcClient jdbc, EvidenceOwner owner, Long ownerId, List<HistoryRecord> records) {
        for (HistoryRecord record : records) {
            jdbc.sql("""
                            INSERT INTO ai_reply.evidence_source
                                (owner_kind, owner_id, source_id, source_kind, source_ref_id, actor_id, occurred_at,
                                 available_at, revision, text_sha, role)
                            VALUES (:ownerKind, :ownerId, :sourceId, :kind, :refId, :actor,
                                    CAST(:occurredAt AS timestamptz), CAST(:availableAt AS timestamptz),
                                    :revision, :textSha, :role)
                            ON CONFLICT (owner_kind, owner_id, source_id) DO NOTHING""")
                    .param("ownerKind", owner.name())
                    .param("ownerId", ownerId)
                    .param("sourceId", record.sourceId())
                    .param("kind", record.kind())
                    .param("refId", record.sourceRefId())
                    .param("actor", record.actorId())
                    .param("occurredAt", record.occurredAt())
                    .param("availableAt", record.availableAt())
                    .param("revision", record.revision())
                    .param("textSha", AiReplyJson.sha256(record.text()))
                    .param("role", record.role())
                    .update();
        }
    }
}
