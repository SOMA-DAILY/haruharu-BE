package org.son.haruharu.domain.profile.business;

import org.son.haruharu.domain.profile.business.command.UpdateImagesCommand;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.business.dto.ImageUploadUrlResponse;
import org.son.haruharu.domain.profile.business.dto.ImagesResponse;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.profile.business.exception.ImageInvalidExtensionException;
import org.son.haruharu.domain.profile.business.exception.ImageInvalidUrlException;
import org.son.haruharu.domain.profile.business.exception.ImageMaxExceededException;
import org.son.haruharu.domain.profile.business.exception.ImageMinLimitException;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserImageWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.infrastructure.s3.PresignedUpload;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private static final int MIN_IMAGES = 1;
    private static final int MAX_IMAGES = 10;

    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "png", "image/png",
        "webp", "image/webp"
    );

    private final UserProfileReader userProfileReader;
    private final UserProfileWriter userProfileWriter;
    private final UserImageReader userImageReader;
    private final UserImageWriter userImageWriter;
    private final S3StoragePort s3StoragePort;

    public ProfileService(
        UserProfileReader userProfileReader,
        UserProfileWriter userProfileWriter,
        UserImageReader userImageReader,
        UserImageWriter userImageWriter,
        S3StoragePort s3StoragePort
    ) {
        this.userProfileReader = userProfileReader;
        this.userProfileWriter = userProfileWriter;
        this.userImageReader = userImageReader;
        this.userImageWriter = userImageWriter;
        this.s3StoragePort = s3StoragePort;
    }

    public ProfileResponse getProfile(Long userId) {
        UserProfile profile = userProfileReader.getByUserId(userId);
        List<UserImage> images = userImageReader.getAllByUserId(userId);
        return ProfileResponse.from(profile, images);
    }

    /** 푸시·표시용 닉네임만 경량 조회(이미지 미로드). */
    public String getNickname(Long userId) {
        return userProfileReader.getByUserId(userId).nickname();
    }

    /** 온보딩 완료 판정용: 프로필(nickname)과 이미지가 모두 갖춰졌는지 여부. */
    public boolean hasCompletedProfile(Long userId) {
        boolean profileFilled = userProfileReader.findByUserId(userId)
            .map(profile -> profile.nickname() != null && !profile.nickname().isBlank())
            .orElse(false);
        boolean hasImage = !userImageReader.getAllByUserId(userId).isEmpty();
        return profileFilled && hasImage;
    }

    @Transactional
    public void deleteAllImages(Long userId) {
        List<UserImage> images = userImageReader.getAllByUserId(userId);
        s3StoragePort.deleteObjects(toKeys(images.stream().map(UserImage::imageUrl).toList()));
        userImageWriter.deleteAllByUserId(userId);
    }

    /** 회원 탈퇴 시 프로필·이미지 삭제(soft). 이미지 원본은 S3에서 함께 제거한다. */
    @Transactional
    public void deleteProfileAndImages(Long userId) {
        deleteAllImages(userId);
        userProfileWriter.deleteByUserId(userId);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileCommand command) {
        UserProfile existing = userProfileReader.findByUserId(userId)
            .orElseGet(() -> UserProfile.empty(userId));
        UserProfile updated = command.applyTo(existing);
        UserProfile saved = userProfileWriter.save(updated);
        List<UserImage> images = userImageReader.getAllByUserId(userId);
        return ProfileResponse.from(saved, images);
    }

    public ImageUploadUrlResponse createImageUploadUrl(Long userId, String filename) {
        String extension = extractExtension(filename);
        String contentType = ALLOWED_IMAGE_TYPES.get(extension);
        if (contentType == null) {
            throw new ImageInvalidExtensionException();
        }
        String key = "users/%s/images/%s.%s".formatted(userId, UUID.randomUUID(), extension);
        PresignedUpload upload = s3StoragePort.createPresignedPutUrl(key, contentType);
        return ImageUploadUrlResponse.from(upload);
    }

    @Transactional
    public ImagesResponse updateImages(Long userId, UpdateImagesCommand command) {
        List<String> urls = command.imageUrls();
        if (urls.size() < MIN_IMAGES) {
            throw new ImageMinLimitException();
        }
        if (urls.size() > MAX_IMAGES) {
            throw new ImageMaxExceededException();
        }
        // 발급 단계의 사용자 격리가 저장 단계에서 우회되지 않도록, 본인 prefix의 URL만 허용한다.
        String ownPrefix = s3StoragePort.publicBaseUrl() + "users/" + userId + "/images/";
        for (String url : urls) {
            if (url == null || !url.startsWith(ownPrefix)) {
                throw new ImageInvalidUrlException();
            }
        }
        // 새 목록에 없는 기존 이미지의 S3 객체를 정리(orphan 방지).
        Set<String> newUrls = new HashSet<>(urls);
        List<String> orphanUrls = userImageReader.getAllByUserId(userId).stream()
            .map(UserImage::imageUrl)
            .filter(url -> !newUrls.contains(url))
            .toList();
        s3StoragePort.deleteObjects(toKeys(orphanUrls));

        userImageWriter.deleteAllByUserId(userId);
        for (int order = 0; order < urls.size(); order++) {
            // id는 null로 두어 영속화 시 @GeneratedValue(IDENTITY)가 생성하게 한다(신규 INSERT 보장).
            userImageWriter.save(new UserImage(null, userId, urls.get(order), order));
        }
        return ImagesResponse.from(urls);
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new ImageInvalidExtensionException();
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    /** 공개 객체 URL 목록을 S3 객체 키 목록으로 변환한다(베이스 prefix와 불일치하는 URL은 제외). */
    private List<String> toKeys(List<String> urls) {
        String base = s3StoragePort.publicBaseUrl();
        return urls.stream()
            .filter(url -> url != null && url.startsWith(base))
            .map(url -> url.substring(base.length()))
            .toList();
    }
}
