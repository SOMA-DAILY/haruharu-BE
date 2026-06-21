package org.son.haruharu.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("성공 데이터로 ok() 호출 시 isSuccess=true, data 포함, error=null 반환")
    void givenData_whenOk_thenSuccessTrueWithData() {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("ok()에 null 전달 시 isSuccess=true, data=null, error=null 반환")
    void givenNull_whenOk_thenSuccessTrueWithNullData() {
        ApiResponse<Void> response = ApiResponse.ok(null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("ApiErrorDetail로 error() 호출 시 isSuccess=false, error 중첩 구조 반환")
    void givenApiErrorDetail_whenError_thenSuccessFalseWithNestedError() {
        ApiResponse<Void> response = ApiResponse.error(new ApiErrorDetail("USER_NOT_FOUND", "존재하지 않는 사용자입니다."));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error().code()).isEqualTo("USER_NOT_FOUND");
        assertThat(response.error().message()).isEqualTo("존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("성공 응답 직렬화 시 isSuccess 키 사용, error 필드 미포함")
    void givenOkResponse_whenSerialize_thenIsSuccessKeyAndNoErrorField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(ApiResponse.ok("hello"));

        assertThat(json).contains("\"isSuccess\":true");
        assertThat(json).doesNotContain("\"error\"");
    }

    @Test
    @DisplayName("실패 응답 직렬화 시 error.code, error.message 중첩 구조 포함")
    void givenErrorResponse_whenSerialize_thenNestedErrorStructure() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(
                ApiResponse.error(new ApiErrorDetail("USER_NOT_FOUND", "존재하지 않는 사용자입니다.")));

        assertThat(json).contains("\"isSuccess\":false");
        assertThat(json).contains("\"error\":{\"code\":\"USER_NOT_FOUND\",\"message\":\"존재하지 않는 사용자입니다.\"}");
    }
}
