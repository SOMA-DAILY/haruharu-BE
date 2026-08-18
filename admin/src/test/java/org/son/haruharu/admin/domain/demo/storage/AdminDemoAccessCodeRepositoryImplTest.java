package org.son.haruharu.admin.domain.demo.storage;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.son.haruharu.domain.demoaccess.storage.DemoAccessCodeEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDemoAccessCodeRepositoryImplTest {

    @Mock AdminDemoAccessCodeJpaRepository jpaRepository;

    AdminDemoAccessCodeRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new AdminDemoAccessCodeRepositoryImpl(jpaRepository);
        given(jpaRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("기존 코드가 없으면 새 행을 생성한다")
    void givenNone_whenSave_thenCreates() {
        given(jpaRepository.findTopByOrderByIdDesc()).willReturn(Optional.empty());

        DemoAccessCode result = repository.save(DemoAccessCode.of("NEW-CODE"));

        assertThat(result.code()).isEqualTo("NEW-CODE");
    }

    @Test
    @DisplayName("기존 코드가 있으면 같은 행의 값만 in-place 갱신한다(행 남발 없음)")
    void givenExisting_whenSave_thenUpdatesInPlace() {
        DemoAccessCodeEntity existing = DemoAccessCodeEntity.from(DemoAccessCode.of("OLD"));
        given(jpaRepository.findTopByOrderByIdDesc()).willReturn(Optional.of(existing));

        DemoAccessCode result = repository.save(DemoAccessCode.of("ROTATED"));

        assertThat(result.code()).isEqualTo("ROTATED");
        assertThat(existing.toDomain().code()).isEqualTo("ROTATED"); // 같은 엔티티를 갱신
        verify(jpaRepository).save(existing);
    }
}
