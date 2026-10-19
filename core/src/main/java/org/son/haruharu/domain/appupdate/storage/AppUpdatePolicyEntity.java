package org.son.haruharu.domain.appupdate.storage;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicy;
@Entity
@Table(name = "app_update_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUpdatePolicyEntity {
    @Id private Long id;
    @Column(name = "ios_minimum_version", length = 20) private String iosMinimumVersion;
    @Column(name = "android_minimum_version", length = 20) private String androidMinimumVersion;
    @Column(nullable = false) private long revision;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    public static AppUpdatePolicyEntity from(AppUpdatePolicy policy) {
        var entity = new AppUpdatePolicyEntity(); entity.id = 1L;
        entity.iosMinimumVersion = policy.iosMinimumVersion(); entity.androidMinimumVersion = policy.androidMinimumVersion();
        entity.revision = policy.revision(); entity.updatedBy = policy.updatedBy(); entity.updatedAt = policy.updatedAt();
        return entity;
    }
    public AppUpdatePolicy toDomain() { return new AppUpdatePolicy(iosMinimumVersion, androidMinimumVersion, revision, updatedBy, updatedAt); }
}
