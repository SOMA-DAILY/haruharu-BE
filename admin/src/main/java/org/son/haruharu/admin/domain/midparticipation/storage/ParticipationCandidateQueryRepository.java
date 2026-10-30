package org.son.haruharu.admin.domain.midparticipation.storage;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.son.haruharu.admin.domain.midparticipation.implement.*;

/** 영상 게시물만 전체 기록에서 집계한다. 퇴장 비노출 원본은 유지하고 일반 삭제는 제외한다. */
@Repository @RequiredArgsConstructor
public class ParticipationCandidateQueryRepository implements ParticipationCandidateRepository {
    private final EntityManager em;
    private static final String GENDER="case when ident.id is not null then case when ident.gender in ('MALE','FEMALE') then ident.gender end else p.gender end";
    private static final String SOURCE="""
        from users u left join user_profiles p on p.user_id=u.id and p.deleted_at is null
        left join user_identities ident on ident.user_id=u.id and ident.deleted_at is null
        """;
    private static final String FILTER=" where u.deleted_at is null and u.status='ACTIVE' "
            +" and (cast(:gender as text) is null or ("+GENDER+")=cast(:gender as text)) "
            +" and (:keyword='' or lower(coalesce(p.nickname,'')) like :search or cast(u.id as text)=:keyword) ";
    private static final String VIDEOS="""
        with videos as (
            select m.user_id,count(*) video_count,max(v.created_at) last_video_uploaded_at
            from video_posts v join room_members m on m.id=v.room_member_id
            join rooms r on r.id=m.room_id
            where v.deleted_at is null and m.deleted_at is null and r.deleted_at is null and r.status<>'CANCELLED'
            group by m.user_id
        )
        """;
    @SuppressWarnings("unchecked")
    public List<ParticipationCandidate> find(String keyword,String gender,int offset,int limit) {
        String sql=VIDEOS+" select u.id,p.nickname,"+GENDER+",coalesce(v.video_count,0),v.last_video_uploaded_at "
                +SOURCE+" left join videos v on v.user_id=u.id "+FILTER+
                " order by coalesce(v.video_count,0) desc,v.last_video_uploaded_at desc nulls last,u.id desc";
        List<Object[]> rows=parameters(em.createNativeQuery(sql),keyword,gender)
                .setFirstResult(offset).setMaxResults(limit).getResultList();
        return rows.stream().map(r->new ParticipationCandidate(((Number)r[0]).longValue(),(String)r[1],(String)r[2],
                ((Number)r[3]).longValue(),time(r[4]))).toList();
    }
    public long count(String keyword,String gender) {
        return ((Number)parameters(em.createNativeQuery("select count(*) "+SOURCE+FILTER),keyword,gender).getSingleResult()).longValue();
    }
    private Query parameters(Query q,String keyword,String gender) {
        String k=keyword==null?"":keyword.strip().toLowerCase(java.util.Locale.ROOT);
        return q.setParameter("keyword",k).setParameter("search","%"+k+"%").setParameter("gender",gender);
    }
    private static LocalDateTime time(Object o) { return o==null?null:o instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)o; }
}
