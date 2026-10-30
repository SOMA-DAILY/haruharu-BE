package org.son.haruharu.domain.room.business;
import java.time.LocalDate;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.exception.IdentityRequiredException;
import org.son.haruharu.domain.room.implement.RoomMember;
/** 초대·중간 참여가 같은 PASS 게이트와 방 성별·생년 스냅샷을 쓴다. */
@Service
@Slf4j
public class RoomEntryIdentityService {
    private final UserProfileReader profiles;
    private final UserIdentityReader identities;
    private final boolean required;
    public RoomEntryIdentityService(UserProfileReader profiles,UserIdentityReader identities,
            @Value("${haruharu.room.join-identity-required:false}") boolean required) {
        this.profiles=profiles;this.identities=identities;this.required=required;
    }
    public record Snapshot(String gender,LocalDate birthday) {}
    public Snapshot snapshot(Long roomId,Long userId) {
        var identity=identities.findByUserId(userId);
        if(required && identity.isEmpty()) throw new IdentityRequiredException();
        if(identity.isPresent()) {
            var verified=identity.get();
            if(verified.gender()!=null && RoomMember.SUPPORTED_GENDERS.contains(verified.gender())) return new Snapshot(verified.gender(),verified.birthday());
            log.warn("PASS 성별을 방 멤버 성별로 쓸 수 없어 비워 둔다 — 운영진 성별 부여 필요. roomId={}, userId={}",roomId,userId);
            return new Snapshot(null,verified.birthday());
        }
        return new Snapshot(profiles.findByUserId(userId).map(p->p.gender()==null?null:p.gender().name()).orElse(null),null);
    }
}
