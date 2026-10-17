package org.son.haruharu.domain.notification.implement;

import java.util.Collection;
import java.util.List;

public interface PushBundleRepository {

    /** 같은 묶음의 수신자 행을 잠가 읽는다. 동시에 들어온 업로드가 같은 상태를 덮어쓰지 않게 한다. */
    List<PushBundle> findAllForUpdate(Collection<Long> userIds, String bundleKey);

    List<PushBundle> saveAll(Collection<PushBundle> bundles);
}
