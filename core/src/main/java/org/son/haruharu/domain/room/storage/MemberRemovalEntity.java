package org.son.haruharu.domain.room.storage;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.room.implement.MemberRemoval;
@Entity @Table(name="room_member_removals",
 uniqueConstraints=@UniqueConstraint(name="uk_room_removed_user",columnNames={"room_id","user_id"}),
 indexes=@Index(name="ix_room_removed_user",columnList="room_id,user_id"))
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MemberRemovalEntity extends BaseEntity {
    @Id private Long memberId;
    @Column(nullable=false) private Long roomId;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false) private LocalDateTime removedAt;
    @Column(length=200) private String reason;
    private Long adminId;
    public static MemberRemovalEntity from(MemberRemoval r) {
        var e=new MemberRemovalEntity();e.memberId=r.memberId();e.roomId=r.roomId();e.userId=r.userId();
        e.removedAt=r.removedAt();e.reason=r.reason();e.adminId=r.adminId();return e;
    }
    public MemberRemoval toDomain() { return new MemberRemoval(memberId,roomId,userId,removedAt,reason,adminId); }
}
