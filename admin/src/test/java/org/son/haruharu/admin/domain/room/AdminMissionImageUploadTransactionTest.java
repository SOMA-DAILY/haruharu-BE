package org.son.haruharu.admin.domain.room;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.son.haruharu.domain.mission.implement.MissionImageWriter;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.storage.RoomEntity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AdminMissionImageUploadTransactionTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean S3StoragePort storage;
    @MockitoSpyBean MissionImageWriter writer;
    Long roomId;
    @BeforeEach void setup() {
        roomId=new TransactionTemplate(transactions).execute(status -> {
            var start=LocalDateTime.now().plusHours(1);
            var entity=RoomEntity.from(Room.createScheduled(start,start.plusDays(2),start.plusDays(2).plusHours(1),start.plusDays(2).plusHours(2),start.plusDays(3),3));
            em.persist(entity);em.flush();return entity.toDomain().id();
        });
    }
    @AfterEach void cleanup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM operator_mission_images WHERE room_id = :id").setParameter("id",roomId).executeUpdate();
            em.createNativeQuery("DELETE FROM rooms WHERE id = :id").setParameter("id",roomId).executeUpdate();
        });
    }
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request() throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(20,30,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(7L,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATOR")));
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder) multipart("/api/v1/admin/rooms/"+roomId+"/operator-missions/images")
                .file(new org.springframework.mock.web.MockMultipartFile("file","reference.png","image/png",bytes.toByteArray())).with(authentication(auth));
    }
    @Test @DisplayName("테스트 외부 트랜잭션 없이 업로드하면 쓰기 트랜잭션으로 사진을 커밋한다")
    void givenNoOuterTransaction_whenUpload_thenCommitsImage() throws Exception {
        willAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            return null;
        }).given(storage).putImage(anyString(),anyString(),any());
        given(storage.publicBaseUrl()).willReturn("https://test.invalid/");
        given(storage.createPresignedImageGetUrl(anyString())).willReturn("https://test.invalid/signed");
        mvc.perform(request()).andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value("https://test.invalid/signed"));
        var count=new TransactionTemplate(transactions).execute(status -> em.createNativeQuery("SELECT count(*) FROM operator_mission_images WHERE room_id=:id",Long.class).setParameter("id",roomId).getSingleResult());
        assertThat(count).isEqualTo(1L);
        then(storage).should(never()).deleteObjects(any());
    }
    @Test @DisplayName("사진 레코드 저장 실패 시 HTTP 오류와 함께 S3 업로드를 롤백 정리한다")
    void givenDatabaseFailure_whenUpload_thenRemovesUploadedObject() throws Exception {
        willThrow(new org.springframework.dao.DataAccessResourceFailureException("test storage failure")).given(writer).save(any());
        mvc.perform(request()).andExpect(status().is5xxServerError());
        var key=org.mockito.ArgumentCaptor.forClass(String.class);
        then(storage).should().putImage(key.capture(),eq("image/png"),any());
        then(storage).should().deleteObjects(List.of(key.getValue()));
    }
}
