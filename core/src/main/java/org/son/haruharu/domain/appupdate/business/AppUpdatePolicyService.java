package org.son.haruharu.domain.appupdate.business;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.son.haruharu.domain.appupdate.business.command.UpdateAppUpdatePolicyCommand;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicyReader;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicyWriter;
import org.son.haruharu.domain.appupdate.business.dto.AppUpdatePolicyResponse;
import org.son.haruharu.domain.appupdate.business.dto.AppUpdatePolicyHistoryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
public class AppUpdatePolicyService {
    private final AppUpdatePolicyReader reader;
    private final AppUpdatePolicyWriter writer;
    private final Clock clock;
    public AppUpdatePolicyService(AppUpdatePolicyReader reader, AppUpdatePolicyWriter writer, Clock clock) {
        this.reader = reader; this.writer = writer; this.clock = clock;
    }
    public AppUpdatePolicyResponse get() { return AppUpdatePolicyResponse.from(reader.get()); }
    public List<AppUpdatePolicyHistoryResponse> history() { return reader.history().stream().map(AppUpdatePolicyHistoryResponse::from).toList(); }
    @Transactional
    public AppUpdatePolicyResponse update(UpdateAppUpdatePolicyCommand command, Long adminId) {
        return AppUpdatePolicyResponse.from(writer.update(command.iosMinimumVersion(), command.androidMinimumVersion(), command.revision(), adminId, LocalDateTime.now(clock)));
    }
}
