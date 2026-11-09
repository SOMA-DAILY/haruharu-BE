package org.son.haruharu.domain.replyrecommendation.implement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * ai_reply 스키마의 JSON·해시 도우미. 작업기와 같은 계약(snake_case 키)으로 쓰고, 해시는 키를 정렬한 JSON 으로 만든다.
 * 앱의 웹 ObjectMapper 설정과 무관하게 동작하도록 따로 둔다.
 */
public final class AiReplyJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            // 작업기가 카드에 칸을 더해도 앱이 깨지지 않게(계약 칸만 읽는다).
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private AiReplyJson() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ai_reply JSON 직렬화 실패: " + value.getClass().getSimpleName(), e);
        }
    }

    public static <T> T read(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ai_reply JSON 해석 실패", e);
        }
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 서비스 표의 KST LocalDateTime 을 UTC ISO 문자열로 바꾼다. */
    public static String iso(LocalDateTime time, ZoneId zone) {
        return time.atZone(zone).toInstant().toString();
    }

    public static Instant instant(LocalDateTime time, ZoneId zone) {
        return time.atZone(zone).toInstant();
    }
}
