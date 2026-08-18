package org.son.haruharu.admin.domain.demo.business;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.domain.demo.business.dto.AdminDemoAccessCodeResponse;
import org.son.haruharu.admin.domain.demo.implement.AdminDemoAccessCodeReader;
import org.son.haruharu.admin.domain.demo.implement.AdminDemoAccessCodeWriter;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminDemoServiceTest {

    @Mock AdminDemoAccessCodeReader reader;
    @Mock AdminDemoAccessCodeWriter writer;

    AdminDemoService service;

    @BeforeEach
    void setUp() {
        service = new AdminDemoService(reader, writer);
    }

    @Test
    @DisplayName("코드가 없으면 configured=false로 반환한다(데모 로그인 닫힘)")
    void givenNoCode_whenGet_thenNotConfigured() {
        given(reader.findCurrent()).willReturn(Optional.empty());

        AdminDemoAccessCodeResponse result = service.getCode();

        assertThat(result.configured()).isFalse();
        assertThat(result.code()).isNull();
    }

    @Test
    @DisplayName("코드가 있으면 값과 configured=true를 반환한다")
    void givenCode_whenGet_thenConfigured() {
        given(reader.findCurrent()).willReturn(Optional.of(new DemoAccessCode(1L, "HARU-2026")));

        AdminDemoAccessCodeResponse result = service.getCode();

        assertThat(result.configured()).isTrue();
        assertThat(result.code()).isEqualTo("HARU-2026");
    }

    @Test
    @DisplayName("코드 설정 시 저장된 값을 configured 응답으로 돌려준다")
    void givenNewCode_whenSet_thenReturnsSavedCode() {
        given(writer.save(any())).willReturn(new DemoAccessCode(1L, "ROTATED"));

        AdminDemoAccessCodeResponse result = service.setCode("ROTATED");

        assertThat(result.configured()).isTrue();
        assertThat(result.code()).isEqualTo("ROTATED");
    }
}
