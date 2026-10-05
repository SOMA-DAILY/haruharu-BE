package org.son.haruharu.domain.mission.implement;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.command.OperatorMissionCommand;
import org.son.haruharu.domain.mission.implement.exception.InvalidMissionImageException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
class MissionImageOwnershipTest {
    @Test @DisplayName("다른 방의 사진과 없는 사진은 첨부를 거부한다")
    void givenForeignImage_whenRequireRoom_thenRejects() {
        var repository=mock(MissionImageRepository.class);
        var reader=new MissionImageReader(repository);
        given(repository.findById(8L)).willReturn(Optional.of(new MissionImage(8L,2L,7L,"key",20,30)));
        given(repository.findById(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> reader.requireRoom(8L,1L)).isInstanceOf(InvalidMissionImageException.class);
        assertThatThrownBy(() -> reader.requireRoom(9L,1L)).isInstanceOf(InvalidMissionImageException.class);
        reader.requireRoom(null,1L);
        reader.requireRoom(8L,2L);
    }
    @Test @DisplayName("사진 제거와 교체는 예약에 반영되며 재시도 및 발송 상태에도 사진이 보존된다")
    void givenPhotoReservation_whenEditAndSend_thenPreservesSnapshot() {
        var now=LocalDateTime.of(2026,10,4,20,0);
        var command=new OperatorMissionCommand("key","DAY2_3","음료",true,now.plusHours(1),8L);
        var mission=OperatorMission.create(1L,7L,command,now);
        assertThat(mission.matches(command)).isTrue();
        var without=new OperatorMissionCommand("key","DAY2_3","음료",true,now.plusHours(1),null);
        assertThat(mission.matches(without)).isFalse();
        assertThat(mission.edited(without).imageId()).isNull();
        assertThat(mission.sent(10L,now.plusHours(1)).imageId()).isEqualTo(8L);
        assertThat(mission.failed("failed").imageId()).isEqualTo(8L);
    }
    @Test @DisplayName("롤백하면 업로드한 키만 정리하고 커밋하면 보존한다")
    void givenUploadedImage_whenTransactionCompletes_thenCleansOnlyRollback() throws Exception {
        var storage=mock(org.son.haruharu.infrastructure.s3.S3StoragePort.class);
        var uploader=new MissionImageUploader(storage,2L);
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,30,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            var image=uploader.upload(1L,7L,bytes.toByteArray(),"image/png");
            var hook=org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().getFirst();
            hook.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED);
            then(storage).should(never()).deleteObjects(any());
            hook.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            then(storage).should().deleteObjects(java.util.List.of(image.objectKey()));
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }

}
