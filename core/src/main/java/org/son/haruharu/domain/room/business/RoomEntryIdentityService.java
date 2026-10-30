package org.son.haruharu.domain.room.business;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.exception.IdentityRequiredException;
import org.son.haruharu.domain.room.implement.RoomMember;
/** 초대·중간 참여가 같은 PASS 게이트와 방 성별·생년 스냅샷을 쓴다. */
@Service
public class RoomEntryIdentityService {
    private final UserProfileReader profiles;
    private final UserIdentityReader identities;
    private final boolean required;
    public RoomEntryIdentityService(UserProfileReader profiles,UserIdentityReader identities,
            @Value("${haruharu.room.join-identity-required:false}") boolean required) {
        this.profiles=profiles;this.identities=identities;this.required=required;
    }
    public record Snapshot(String gender,LocalDate birthday) {}
    public Snapshot snapshot(Long userId) {
        var identity=identities.findByUserId(userId);
        if(required && identity.isEmpty()) throw new IdentityRequiredException();
        if(identity.isPresent()) {
            var verified=identity.orElseThrow(IdentityRequiredException::new);
            return new Snapshot(verified.gender()!=null && RoomMember.SUPPORTED_GENDERS.contains(verified.gender())?verified.gender():null,verified.birthday());
        }
        return new Snapshot(profiles.findByUserId(userId).map(p->p.gender()==null?null:p.gender().name()).orElse(null),null);
    }
}
