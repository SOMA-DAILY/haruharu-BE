package org.son.haruharu.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("성공 데이터로 ok() 호출 시 success=true, data 포함, error=null 반환")
    void givenData_whenOk_thenSuccessTrueWithData() {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.error()).isNull();
        assertThat(response.code()).isNull();
    }

    @Test
    @DisplayName("ok()에 null 전달 시 success=true, data=null, error=null 반환")
    void givenNull_whenOk_thenSuccessTrueWithNullData() {
        ApiResponse<Void> response = ApiResponse.ok(null);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isNull();
        assertThat(response.code()).isNull();
    }

    @Test
    @DisplayName("에러 메시지와 코드로 error() 호출 시 success=false, error와 code 포함 반환")
    void givenErrorMessageAndCode_whenError_thenSuccessFalseWithMessageAndCode() {
        ApiResponse<Void> response = ApiResponse.error("존재하지 않는 사용자입니다.", "USER_NOT_FOUND");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo("존재하지 않는 사용자입니다.");
        assertThat(response.code()).isEqualTo("USER_NOT_FOUND");
    }
}
