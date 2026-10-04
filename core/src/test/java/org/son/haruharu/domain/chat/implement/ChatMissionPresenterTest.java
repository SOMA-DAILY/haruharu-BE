package org.son.haruharu.domain.chat.implement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.implement.*;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
class ChatMissionPresenterTest {
    @Test @DisplayName("사진은 일괄 조회하고 중복 서명 없이 각 카드에 붙이며 사진 없는 카드는 유지한다")
    void givenPhotoCards_whenPreparePage_thenFetchesOnceAndSigns() {
        var reader=mock(MissionImageReader.class);
        var storage=mock(S3StoragePort.class);
        var presenter=new ChatMissionPresenter(reader,storage);
        var card=new ChatMission(1L,2L,"DAY2_3","오늘의 미션",8L,null);
        var noPhoto=new ChatMission(2L,2L,"DAY2_5","오늘의 미션");
        given(reader.getAll(anyCollection())).willReturn(Map.of(8L,new MissionImage(8L,2L,7L,"users/2/mission-images/2/photo.png",20,30)));
        given(storage.publicBaseUrl()).willReturn("https://test.invalid/");
        given(storage.createPresignedImageGetUrl("https://test.invalid/users/2/mission-images/2/photo.png")).willReturn("https://test.invalid/signed");
        given(reader.get(8L)).willReturn(new MissionImage(8L,2L,7L,"users/2/mission-images/2/photo.png",20,30));
        var apply=presenter.prepare(java.util.Arrays.asList(card,card,noPhoto,null));
        assertThat(apply.apply(card).image().url()).endsWith("/signed");
        assertThat(apply.apply(noPhoto).image()).isNull();
        assertThat(apply.apply(null)).isNull();
        then(reader).should().getAll(anyCollection());
        then(storage).should().createPresignedImageGetUrl(anyString());
        var message=ChatMessage.mission(9L,7L,"음료",card);
        var stored=org.son.haruharu.domain.chat.storage.ChatMessageEntity.from(message).toDomain();
        assertThat(stored.mission().imageId()).isEqualTo(8L);
        assertThat(stored.mission().image()).isNull();
        assertThat(org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast.from(stored,presenter.present(card)).mission().imageId()).isEqualTo(8L);
    }
}
