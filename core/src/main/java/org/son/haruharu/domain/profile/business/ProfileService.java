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

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private static final int MIN_IMAGES = 0;
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
        return ProfileResponse.from(profile, images, s3StoragePort::createPresignedImageGetUrl);
    }

    /** 푸시·표시용 닉네임만 경량 조회(이미지 미로드). */
    public String getNickname(Long userId) {
        return userProfileReader.getByUserId(userId).nickname();
    }

    /** 온보딩 완료 판정용: 공백이 아닌 닉네임이 저장되어 있는지 여부. */
    public boolean hasCompletedProfile(Long userId) {
        // 온보딩 완료 조건(#392): 닉네임 + 성별. 성별은 프로필 PUT 에선 nullable(편집 호환)이고 완료 게이트에서만 필수다.
        return userProfileReader.findByUserId(userId)
            .map(profile -> profile.nickname() != null && !profile.nickname().isBlank()
                    && profile.gender() != null)
            .orElse(false);
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

    /**
     * 기수 신청서의 학교·학과·번호를 프로필에 옮겨 적는다(#511). 학교는 affiliation 칸에 둔다.
     * 세 칸만 UPDATE 해 같은 시각의 다른 프로필 수정을 덮어쓰지 않는다. 프로필이 없으면 그 세 칸으로 새로 만든다.
     */
    @Transactional
    public void saveApplicantContact(Long userId, String school, String department, String phone) {
        if (!userProfileWriter.updateApplicantContact(userId, school, department, phone, LocalDateTime.now())) {
            userProfileWriter.save(UserProfile.empty(userId).withApplicantContact(school, department, phone));
        }
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileCommand command) {
        UserProfile existing = userProfileReader.findByUserId(userId)
            .orElseGet(() -> UserProfile.empty(userId));
        UserProfile updated = command.applyTo(existing);
        UserProfile saved = userProfileWriter.save(updated);
        List<UserImage> images = userImageReader.getAllByUserId(userId);
        return ProfileResponse.from(saved, images, s3StoragePort::createPresignedImageGetUrl);
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
        // 조회 응답의 presigned URL(쿼리스트링 포함)이 그대로 되돌아와도 원본 objectUrl로 저장되도록 정규화한다.
        List<String> urls = command.imageUrls().stream().map(ProfileService::stripQuery).toList();
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
        return ImagesResponse.from(urls.stream()
            .map(s3StoragePort::createPresignedImageGetUrl)
            .toList());
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new ImageInvalidExtensionException();
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    /** presigned URL의 쿼리스트링을 제거해 원본 objectUrl로 정규화한다. */
    private static String stripQuery(String url) {
        if (url == null) {
            return null;
        }
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
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
