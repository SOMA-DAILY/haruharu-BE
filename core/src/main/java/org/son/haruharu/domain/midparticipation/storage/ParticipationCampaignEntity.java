package org.son.haruharu.domain.midparticipation.storage;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.midparticipation.implement.ParticipationCampaign;
@Entity @Table(name="mid_participation_campaigns",indexes=@Index(name="ix_mid_campaign_room",columnList="room_id,id"))
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ParticipationCampaignEntity extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long roomId;
    @Column(nullable=false) private int seats;
    @Column(nullable=false) private int acceptedCount;
    @Column(nullable=false) private LocalDateTime expiresAt;
    private LocalDateTime cancelledAt;
    public static ParticipationCampaignEntity from(ParticipationCampaign c) {
        var e=new ParticipationCampaignEntity(); e.id=c.id(); e.roomId=c.roomId();e.seats=c.seats();e.acceptedCount=c.acceptedCount();
        e.expiresAt=c.expiresAt();e.cancelledAt=c.cancelledAt();e.createdAt=c.createdAt();return e;
    }
    public ParticipationCampaign toDomain() { return new ParticipationCampaign(id,roomId,seats,acceptedCount,expiresAt,cancelledAt,createdAt); }
}
