package org.son.haruharu.domain.mission.common.implement;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;

/**
 * 미션에 붙는 사진 한 장(선택). 유형과 무관한 공통 속성이고, details JSON 의 예약 키 `image` 로 저장한다.
 * 키는 서버가 업로드 때 만든 `mission-images/{uuid}-{가로}x{세로}.{확장자}` 만 받는다(다른 객체 노출 방지).
 */
public record MissionImage(String key, int width, int height) {
    /** details JSON 에서 공통 코드가 쓰는 예약 키. 유형별 정의는 이 이름을 쓰지 않는다. */
    public static final String STORED_KEY = "image";
    public static final String KEY_PREFIX = "mission-images/";
    private static final Pattern KEY = Pattern.compile(
            "^mission-images/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}-([1-9]\\d{0,4})x([1-9]\\d{0,4})\\.(?:png|jpg)$");

    public MissionImage {
        if (key == null || !KEY.matcher(key).matches() || width < 1 || height < 1) throw invalid();
    }

    public static MissionImage create(UUID id, int width, int height, String extension) {
        return new MissionImage(KEY_PREFIX + id + "-" + width + "x" + height + "." + extension, width, height);
    }

    /** 요청으로 받은 키. 비어 있으면 사진 없음(null). 크기는 서버가 지은 키 이름에서 읽는다. */
    public static MissionImage parse(String key) {
        if (key == null || key.isBlank()) return null;
        Matcher matcher = KEY.matcher(key);
        if (!matcher.matches()) throw invalid();
        return new MissionImage(key, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    public Map<String, Object> toStored() {
        return Map.of("key", key, "width", width, "height", height);
    }

    /** 저장된 값이 없거나 읽을 수 없으면 사진 없음으로 본다. */
    public static MissionImage fromStored(Object stored) {
        if (!(stored instanceof Map<?, ?> map) || !(map.get("key") instanceof String key)
                || !(map.get("width") instanceof Number width) || !(map.get("height") instanceof Number height)
                || !KEY.matcher(key).matches() || width.intValue() < 1 || height.intValue() < 1) return null;
        return new MissionImage(key, width.intValue(), height.intValue());
    }

    private static InvalidMissionException invalid() {
        return new InvalidMissionException("image_key", "업로드한 미션 사진을 선택해 주세요");
    }
}
