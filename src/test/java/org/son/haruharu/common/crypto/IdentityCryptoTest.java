package org.son.haruharu.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityCryptoTest {

    private final IdentityCrypto crypto = new IdentityCrypto("test-master-key-for-identity-crypto");

    @Test
    @DisplayName("암호화 후 복호화하면 원본 값이 복원된다")
    void givenPlainText_whenEncryptThenDecrypt_thenReturnsOriginal() {
        String ci = "1a2b3c-ci-value-88-chars";

        String decrypted = crypto.decrypt(crypto.encrypt(ci));

        assertThat(decrypted).isEqualTo(ci);
    }

    @Test
    @DisplayName("같은 값을 두 번 암호화하면 랜덤 IV로 인해 서로 다른 암호문이 생성된다")
    void givenSamePlainText_whenEncryptTwice_thenCiphertextsDiffer() {
        String ci = "same-ci-value";

        assertThat(crypto.encrypt(ci)).isNotEqualTo(crypto.encrypt(ci));
    }

    @Test
    @DisplayName("blind index는 같은 입력에 대해 항상 같은 값을 반환한다")
    void givenSamePlainText_whenBlindIndex_thenDeterministic() {
        String ci = "ci-value";

        assertThat(crypto.blindIndex(ci)).isEqualTo(crypto.blindIndex(ci));
    }

    @Test
    @DisplayName("서로 다른 입력의 blind index는 서로 다르다")
    void givenDifferentPlainText_whenBlindIndex_thenDiffer() {
        assertThat(crypto.blindIndex("ci-A")).isNotEqualTo(crypto.blindIndex("ci-B"));
    }

    @Test
    @DisplayName("null 입력은 null로 처리된다")
    void givenNull_whenEncryptOrBlindIndex_thenReturnsNull() {
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
        assertThat(crypto.blindIndex(null)).isNull();
    }

    @Test
    @DisplayName("빈 키로 생성하면 예측 가능한 고정 키를 막기 위해 예외가 발생한다")
    void givenBlankKey_whenConstruct_thenThrows() {
        assertThatThrownBy(() -> new IdentityCrypto(""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("32자 미만의 약한 키로 생성하면 예외가 발생한다")
    void givenShortKey_whenConstruct_thenThrows() {
        assertThatThrownBy(() -> new IdentityCrypto("too-short-key"))
                .isInstanceOf(IllegalStateException.class);
    }
}
