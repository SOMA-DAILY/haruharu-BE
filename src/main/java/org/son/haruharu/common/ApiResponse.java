package org.son.haruharu.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiResponse<T>(
        @JsonProperty("isSuccess") boolean isSuccess,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) ApiErrorDetail error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ApiErrorDetail error) {
        return new ApiResponse<>(false, null, error);
    }
}
