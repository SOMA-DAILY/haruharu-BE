package org.son.haruharu.domain.mission.storage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.implement.MissionImage;
@Entity @Table(name="operator_mission_images") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MissionImageEntity extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long roomId;
    @Column(nullable=false) private Long createdBy;
    @Column(nullable=false, unique=true, length=300) private String objectKey;
    @Column(nullable=false) private int width;
    @Column(nullable=false) private int height;
    public static MissionImageEntity from(MissionImage image) {
        var entity = new MissionImageEntity(); entity.id=image.id(); entity.roomId=image.roomId(); entity.createdBy=image.createdBy();
        entity.objectKey=image.objectKey(); entity.width=image.width(); entity.height=image.height(); return entity;
    }
    public MissionImage toDomain() { return new MissionImage(id,roomId,createdBy,objectKey,width,height); }
}
