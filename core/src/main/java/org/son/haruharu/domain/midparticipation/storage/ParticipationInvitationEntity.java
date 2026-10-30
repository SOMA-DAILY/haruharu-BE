package org.son.haruharu.domain.midparticipation.storage;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.midparticipation.implement.ParticipationInvitation;
@org.hibernate.annotations.SQLRestriction("member_removed_at is null")
@Entity @Table(name="mid_participation_invitations",
 uniqueConstraints=@UniqueConstraint(name="uk_mid_invitation_recipient",columnNames={"campaign_id","user_id"}),
 indexes={@Index(name="ix_mid_invitation_user",columnList="user_id,id"),@Index(name="ix_mid_invitation_campaign",columnList="campaign_id,id")})
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ParticipationInvitationEntity extends BaseEntity {
    @Column(name="member_removed_at",updatable=false) private java.time.LocalDateTime memberRemovedAt;
    @org.hibernate.annotations.ColumnDefault("0") @Column(nullable=false,updatable=false) private int notifiedVersion;
    @org.hibernate.annotations.ColumnDefault("0") @Column(nullable=false,updatable=false) private int notificationClaimVersion;
    @Column(updatable=false) private LocalDateTime notificationClaimedAt;
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long userId;
    private Long campaignId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ParticipationInvitation.Kind kind;
    @Column(nullable=false) private int deliveryVersion;
    @Column(nullable=false) private int seenVersion;
    private LocalDateTime declinedAt;
    private LocalDateTime acceptedAt;
    private Long acceptedMemberId;
    @Column(nullable=false) private LocalDateTime deliveredAt;
    public static ParticipationInvitationEntity from(ParticipationInvitation i) {
        var e=new ParticipationInvitationEntity();e.id=i.id();e.userId=i.userId();e.campaignId=i.campaignId();e.kind=i.kind();
        e.deliveryVersion=i.deliveryVersion();e.seenVersion=i.seenVersion();e.declinedAt=i.declinedAt();e.acceptedAt=i.acceptedAt();
        e.acceptedMemberId=i.acceptedMemberId();e.createdAt=i.createdAt();e.deliveredAt=i.deliveredAt();return e;
    }
    public ParticipationInvitation toDomain() { return new ParticipationInvitation(id,userId,campaignId,kind,deliveryVersion,seenVersion,declinedAt,acceptedAt,acceptedMemberId,createdAt,deliveredAt); }
}
