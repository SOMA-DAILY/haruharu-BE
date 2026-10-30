package org.son.haruharu.domain.midparticipation.storage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.midparticipation.implement.ParticipationPreference;
@Entity @Table(name="mid_participation_preferences") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ParticipationPreferenceEntity extends BaseEntity {
    @Id private Long userId;
    @Column(nullable=false) private boolean enabled;
    public static ParticipationPreferenceEntity from(ParticipationPreference p) {
        var e=new ParticipationPreferenceEntity(); e.userId=p.userId(); e.enabled=p.enabled(); return e;
    }
    public ParticipationPreference toDomain() { return new ParticipationPreference(userId,enabled); }
}
