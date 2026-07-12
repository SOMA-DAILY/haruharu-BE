package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** 유저별 대표이미지 URL 배치 조회(쿼리 1회). 이미지 없는 유저는 키 자체가 없다. */
    public Map<Long, String> getRepresentativeImageUrls(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        // userId·sortedOrder 오름차순 조회라 유저별 첫 행이 대표이미지다
        for (UserImage image : userImageRepository.findAllByUserIdIn(userIds)) {
            result.putIfAbsent(image.userId(), image.imageUrl());
        }
        return result;
    }
}
