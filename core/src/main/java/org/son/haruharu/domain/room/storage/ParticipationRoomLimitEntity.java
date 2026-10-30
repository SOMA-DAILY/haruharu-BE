package org.son.haruharu.domain.room.storage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
@Entity @Table(name="mid_participation_room_limits") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ParticipationRoomLimitEntity extends BaseEntity {
    @Id private Long roomId;
    @Column(nullable=false) private int capacity;
    public static ParticipationRoomLimitEntity of(Long roomId,int capacity) {
        var e=new ParticipationRoomLimitEntity();e.roomId=roomId;e.capacity=capacity;return e;
    }
    public int capacity() { return capacity; }
}
