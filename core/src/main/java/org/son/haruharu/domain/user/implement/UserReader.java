package org.son.haruharu.domain.user.implement;

import org.son.haruharu.domain.user.storage.exception.UserNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class UserReader {

    private final UserRepository userRepository;

    public UserReader(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);
    }

    /**
     * 유저 행을 비관적 락으로 잠근다(#321). 방 입장처럼 "유저당 1개" 불변식을 지켜야 하는 쓰기에서,
     * 검사와 저장 사이의 경쟁을 유저 단위로 직렬화한다. 반드시 <b>그 검사(예: hasCurrentRoom) 직전</b>에
     * 호출해야 뒤이은 read 가 락 뒤에 온다(트랜잭션 안에서 호출, 락은 커밋 시 해제).
     */
    public void lockById(Long id) {
        userRepository.findByIdForUpdate(id)
                .orElseThrow(UserNotFoundException::new);
    }
}
