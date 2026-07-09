package org.son.haruharu.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.metamodel.EntityType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모든 @Entity의 STRING enum 컬럼 길이가 가장 긴 enum 상수를 담을 수 있는지 전수 검증한다(#169 회귀 방지).
 * coin_ledger.reason varchar(20) < UPLOAD_COMPLETION_REWARD(24자)로 보상 INSERT가 전면 실패했던 부류의 버그를,
 * 특정 enum이 아니라 메타모델 스캔으로 잡는다 — 이후 어떤 엔티티/enum이 추가돼도 자동 커버.
 * (H2는 enum 컬럼을 네이티브 ENUM 타입으로 생성해 length를 무시하므로 INSERT 통합 테스트로는 재현되지 않는다.
 *  PostgreSQL은 varchar(length)로 생성되어 런타임에 터진다 — 그래서 스키마 정의 자체를 검증한다.)
 */
@SpringBootTest
@ActiveProfiles("test")
class EnumColumnLengthIntegrationTest {

    private static final int DEFAULT_COLUMN_LENGTH = 255; // @Column 미지정 시 JPA 기본값

    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("모든 엔티티의 STRING enum 컬럼은 가장 긴 enum 상수 이름보다 길이가 크거나 같다")
    void givenAllEntities_whenScanEnumColumns_thenLengthFitsLongestConstant() {
        List<String> violations = new ArrayList<>();

        for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
            for (Field field : allFields(entity.getJavaType())) {
                if (!field.getType().isEnum() || !isStringEnumerated(field)) {
                    continue;
                }
                int columnLength = columnLength(field);
                int longest = longestConstantLength(field.getType());
                if (longest > columnLength) {
                    violations.add("%s.%s: length=%d < 최장 enum 상수 %d자 (%s)".formatted(
                            entity.getJavaType().getSimpleName(), field.getName(),
                            columnLength, longest, longestConstantName(field.getType())));
                }
            }
        }

        assertThat(violations)
                .withFailMessage("enum 상수가 컬럼 길이를 초과함 — @Column(length) 확장 + DB ALTER 필요:%n%s",
                        String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }

    private boolean isStringEnumerated(Field field) {
        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        return enumerated != null && enumerated.value() == EnumType.STRING;
    }

    private int columnLength(Field field) {
        Column column = field.getAnnotation(Column.class);
        return column != null ? column.length() : DEFAULT_COLUMN_LENGTH;
    }

    private int longestConstantLength(Class<?> enumType) {
        return longestConstantName(enumType).length();
    }

    private String longestConstantName(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name())
                .reduce("", (a, b) -> a.length() >= b.length() ? a : b);
    }
}
