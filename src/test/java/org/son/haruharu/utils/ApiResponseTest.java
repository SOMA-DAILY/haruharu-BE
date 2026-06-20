package org.son.haruharu.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("성공 데이터로 ok() 호출 시 success=true, data 포함, error=null 반환")
    void givenData_whenOk_thenSuccessTrueWithData() {
        String data = "hello";

        ApiResponse<String> response = ApiResponse.ok(data);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("ok()에 null 전달 시 success=true, data=null, error=null 반환")
    void givenNull_whenOk_thenSuccessTrueWithNullData() {
        ApiResponse<Void> response = ApiResponse.ok(null);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("에러 메시지로 error() 호출 시 success=false, data=null, error 포함 반환")
    void givenErrorMessage_whenError_thenSuccessFalseWithMessage() {
        ApiResponse<Void> response = ApiResponse.error("유저를 찾을 수 없습니다");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo("유저를 찾을 수 없습니다");
    }
}
