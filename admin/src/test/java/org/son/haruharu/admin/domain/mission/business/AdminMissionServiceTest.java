package org.son.haruharu.admin.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.domain.mission.business.command.CreateMissionCommand;
import org.son.haruharu.admin.domain.mission.business.command.UpdateMissionCommand;
import org.son.haruharu.admin.domain.mission.business.dto.AdminMissionResponse;
import org.son.haruharu.admin.domain.mission.business.exception.AdminMissionDuplicateException;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionReader;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionWriter;
import org.son.haruharu.admin.domain.mission.storage.exception.AdminMissionNotFoundException;
import org.son.haruharu.domain.mission.implement.Mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminMissionServiceTest {

    @Mock AdminMissionReader adminMissionReader;
    @Mock AdminMissionWriter adminMissionWriter;

    AdminMissionService service;

    @BeforeEach
    void setUp() {
        service = new AdminMissionService(adminMissionReader, adminMissionWriter);
    }

    private Mission mission(Long id, String description, int points, boolean active) {
        return new Mission(id, description, points, active);
    }

    @Test
    @DisplayName("신규 문구면 미션을 생성하고 active=true로 저장한다")
    void create_whenNew_saves() {
        given(adminMissionReader.existsByDescription("오늘 먹은 음식")).willReturn(false);
        given(adminMissionWriter.save(any())).willAnswer(inv -> {
            Mission m = inv.getArgument(0);
            return new Mission(1L, m.description(), m.points(), m.isActive());
        });

        AdminMissionResponse result = service.create(new CreateMissionCommand("오늘 먹은 음식", 5));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.description()).isEqualTo("오늘 먹은 음식");
        assertThat(result.points()).isEqualTo(5);
        assertThat(result.active()).isTrue();
        ArgumentCaptor<Mission> captor = ArgumentCaptor.forClass(Mission.class);
        verify(adminMissionWriter).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName("이미 존재하는 문구면 AdminMissionDuplicateException(409)이 발생하고 저장하지 않는다")
    void create_whenDuplicate_throws() {
        given(adminMissionReader.existsByDescription("오늘 먹은 음식")).willReturn(true);

        assertThatThrownBy(() -> service.create(new CreateMissionCommand("오늘 먹은 음식", 5)))
                .isInstanceOf(AdminMissionDuplicateException.class);
        verify(adminMissionWriter, never()).save(any());
    }

    @Test
    @DisplayName("수정 시 보낸 필드만 갱신하고 나머지는 유지한다(문구만)")
    void update_partial_onlyGivenFields() {
        given(adminMissionReader.getById(1L)).willReturn(mission(1L, "옛 문구", 5, true));
        given(adminMissionWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        AdminMissionResponse result = service.update(1L,
                new UpdateMissionCommand("새 문구", null, null));

        assertThat(result.description()).isEqualTo("새 문구");
        assertThat(result.points()).isEqualTo(5);   // 유지
        assertThat(result.active()).isTrue();        // 유지
    }

    @Test
    @DisplayName("active=false로 소프트 삭제(비활성화)한다")
    void update_deactivate() {
        given(adminMissionReader.getById(1L)).willReturn(mission(1L, "문구", 5, true));
        given(adminMissionWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        AdminMissionResponse result = service.update(1L,
                new UpdateMissionCommand(null, null, false));

        assertThat(result.active()).isFalse();
        assertThat(result.description()).isEqualTo("문구"); // 유지
    }

    @Test
    @DisplayName("문구를 다른 미션과 겹치게 수정하면 AdminMissionDuplicateException(409)")
    void update_whenDescriptionCollides_throws() {
        given(adminMissionReader.getById(1L)).willReturn(mission(1L, "옛 문구", 5, true));
        given(adminMissionReader.existsByDescription("겹치는 문구")).willReturn(true);

        assertThatThrownBy(() -> service.update(1L,
                new UpdateMissionCommand("겹치는 문구", null, null)))
                .isInstanceOf(AdminMissionDuplicateException.class);
        verify(adminMissionWriter, never()).save(any());
    }

    @Test
    @DisplayName("문구를 그대로 두고(자기 자신) 다른 필드만 수정하면 중복 검사에 걸리지 않는다")
    void update_sameDescription_doesNotTriggerDuplicateCheck() {
        given(adminMissionReader.getById(1L)).willReturn(mission(1L, "문구", 5, true));
        given(adminMissionWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        AdminMissionResponse result = service.update(1L,
                new UpdateMissionCommand("문구", 10, null));

        assertThat(result.points()).isEqualTo(10);
        verify(adminMissionReader, never()).existsByDescription(any());
    }

    @Test
    @DisplayName("없는 미션을 수정하면 AdminMissionNotFoundException(404)")
    void update_whenNotFound_throws() {
        given(adminMissionReader.getById(99L)).willThrow(new AdminMissionNotFoundException());

        assertThatThrownBy(() -> service.update(99L, new UpdateMissionCommand("x", null, null)))
                .isInstanceOf(AdminMissionNotFoundException.class);
    }

    @Test
    @DisplayName("목록은 활성/비활성 전체를 반환한다")
    void getMissions_returnsAll() {
        given(adminMissionReader.findAll()).willReturn(java.util.List.of(
                mission(1L, "활성 미션", 5, true),
                mission(2L, "비활성 미션", 5, false)));

        var result = service.getMissions();

        assertThat(result).extracting(AdminMissionResponse::active).containsExactly(true, false);
    }
}
