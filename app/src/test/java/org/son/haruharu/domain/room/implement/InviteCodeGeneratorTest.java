package org.son.haruharu.domain.room.implement;

import java.util.Optional;
import java.util.Locale;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.exception.InviteCodeGenerationFailedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InviteCodeGeneratorTest {

    private static final String CODE_PATTERN = "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}";

    @Mock RoomInviteReader roomInviteReader;

    InviteCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new InviteCodeGenerator(roomInviteReader);
    }

    private RoomInvite existingInvite() {
        return new RoomInvite(1L, 1L, "EXIST1", RoomInviteStatus.ACTIVE, 6, 0, null, "admin");
    }

    @Test
    @DisplayName("생성된 코드는 6자리이며 혼동문자(O·0·I·1)를 포함하지 않는다")
    void givenNoCollision_whenGenerate_thenSixCharsWithoutAmbiguousLetters() {
        given(roomInviteReader.findByCode(anyString())).willReturn(Optional.empty());

        IntStream.range(0, 200).forEach(i -> {
            String code = generator.generate();
            assertThat(code).hasSize(6).matches(CODE_PATTERN);
            assertThat(code).doesNotContain("O").doesNotContain("0").doesNotContain("I").doesNotContain("1");
        });
    }

    @Test
    @DisplayName("이미 존재하는 코드가 나오면 재시도해 중복되지 않는 코드를 반환한다")
    void givenFirstCodeCollides_whenGenerate_thenRetriesAndReturnsFreeCode() {
        given(roomInviteReader.findByCode(anyString()))
                .willReturn(Optional.of(existingInvite()))
                .willReturn(Optional.empty());

        String code = generator.generate();

        assertThat(code).matches(CODE_PATTERN);
        verify(roomInviteReader, times(2)).findByCode(anyString());
    }

    @Test
    @DisplayName("재시도 한도까지 계속 중복이면 InviteCodeGenerationFailedException")
    void givenAlwaysCollides_whenGenerate_thenThrows() {
        given(roomInviteReader.findByCode(anyString())).willReturn(Optional.of(existingInvite()));

        assertThatThrownBy(() -> generator.generate())
                .isInstanceOf(InviteCodeGenerationFailedException.class);
        verify(roomInviteReader, times(InviteCodeGenerator.MAX_ATTEMPTS)).findByCode(anyString());
    }

    @Test
    @DisplayName("소문자와 공백이 있는 코드는 Locale.ROOT 대문자 6자리 코드로 정규화한다")
    void givenLowercaseTrimmedCode_whenNormalize_thenReturnsRootUppercaseCode() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(InviteCodeGenerator.normalize("  abj234  ")).contains("ABJ234");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("공백·길이 오류·혼동 문자가 포함된 코드는 정규화에 실패한다")
    void givenBlankWrongLengthOrAmbiguousCharacter_whenNormalize_thenReturnsEmpty() {
        assertThat(InviteCodeGenerator.normalize(null)).isEmpty();
        assertThat(InviteCodeGenerator.normalize("   ")).isEmpty();
        assertThat(InviteCodeGenerator.normalize("ABC23")).isEmpty();
        assertThat(InviteCodeGenerator.normalize("ABC2345")).isEmpty();
        assertThat(InviteCodeGenerator.normalize("ABCI23")).isEmpty();
        assertThat(InviteCodeGenerator.normalize("ABCO23")).isEmpty();
        assertThat(InviteCodeGenerator.normalize("ABC023")).isEmpty();
        assertThat(InviteCodeGenerator.normalize("ABC123")).isEmpty();
    }
}
