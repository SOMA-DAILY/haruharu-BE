package org.son.haruharu.admin.domain.user.implement;

import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.user.implement.User;

/** 유저 목록/상세용 유저+프로필 조합. profile은 없을 수 있다(null). */
public record UserWithProfile(User user, UserProfile profile) {
}
