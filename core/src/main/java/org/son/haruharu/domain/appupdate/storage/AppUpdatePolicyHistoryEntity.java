package org.son.haruharu.domain.appupdate.storage;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicy;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicyHistory;
@Entity
@Table(name = "app_update_policy_history", uniqueConstraints = @UniqueConstraint(name = "uq_app_update_policy_history_revision", columnNames = "revision"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUpdatePolicyHistoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long revision;
    @Column(name = "previous_ios_minimum_version", length = 20) private String previousIosMinimumVersion;
    @Column(name = "previous_android_minimum_version", length = 20) private String previousAndroidMinimumVersion;
    @Column(name = "ios_minimum_version", length = 20) private String iosMinimumVersion;
    @Column(name = "android_minimum_version", length = 20) private String androidMinimumVersion;
    @Column(name = "changed_by", nullable = false) private Long changedBy;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt;
    public static AppUpdatePolicyHistoryEntity from(AppUpdatePolicy previous, AppUpdatePolicy next) {
        var entity = new AppUpdatePolicyHistoryEntity();entity.revision = next.revision();
        entity.previousIosMinimumVersion = previous.iosMinimumVersion();entity.previousAndroidMinimumVersion = previous.androidMinimumVersion();
        entity.iosMinimumVersion = next.iosMinimumVersion();entity.androidMinimumVersion = next.androidMinimumVersion();
        entity.changedBy = next.updatedBy();entity.changedAt = next.updatedAt();return entity;
    }
    public AppUpdatePolicyHistory toDomain() { return new AppUpdatePolicyHistory(id, revision, previousIosMinimumVersion, previousAndroidMinimumVersion, iosMinimumVersion, androidMinimumVersion, changedBy, changedAt); }
}
