package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserImageReader {

    private final UserImageRepository userImageRepository;

    public UserImageReader(UserImageRepository userImageRepository) {
        this.userImageRepository = userImageRepository;
    }

    public List<UserImage> getAllByUserId(Long userId) {
        return userImageRepository.findAllByUserId(userId);
    }

    /** 대표이미지(sortedOrder 최솟값) URL. 정렬 조회의 첫 원소. 이미지 없으면 null. */
    public String getRepresentativeImageUrl(Long userId) {
        List<UserImage> images = userImageRepository.findAllByUserId(userId);
        return images.isEmpty() ? null : images.get(0).imageUrl();
    }
}
