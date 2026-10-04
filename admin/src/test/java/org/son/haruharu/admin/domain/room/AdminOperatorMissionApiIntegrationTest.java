package org.son.haruharu.admin.domain.room;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdminOperatorMissionApiIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.son.haruharu.infrastructure.s3.S3StoragePort storage;
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    Long roomId;
    LocalDateTime starts=LocalDateTime.now().plusHours(1).withNano(0);
    @BeforeEach void setup() {
        var entity=RoomEntity.from(Room.createScheduled(starts,starts.plusDays(2),starts.plusDays(2).plusHours(1),
                starts.plusDays(2).plusHours(2),starts.plusDays(3),3));
        em.persist(entity);em.flush();roomId=entity.toDomain().id();
    }
    UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(7L,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }
    String path(){return "/api/v1/admin/rooms/"+roomId+"/operator-missions";}
    String body(String key,LocalDateTime at) {
        return """
            {"request_key":"%s","template_id":"DAY1_1","content":"지금 있는 곳 보여주기","notify_participants":false,"scheduled_at":"%s"}
            """.formatted(key,at);
    }
    @Test void givenOperator_whenScheduleEditAndCancel_thenPersistLatestState() throws Exception {
        var result=mvc.perform(post(path()).with(authentication(auth("OPERATOR"))).contentType("application/json")
                .content(body("api-test",starts))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED")).andExpect(jsonPath("$.data.created_by").value(7))
                .andReturn().getResponse().getContentAsString();
        Long id=com.jayway.jsonpath.JsonPath.parse(result).read("$.data.id",Long.class);
        mvc.perform(put(path()+"/"+id).with(authentication(auth("OPERATOR"))).contentType("application/json")
                .content(body("api-test",starts.plusMinutes(30)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduled_at").value(starts.plusMinutes(30).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));
        mvc.perform(post(path()+"/"+id+"/cancel").with(authentication(auth("OPERATOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
    @Test void givenNonOperator_whenAccessMissions_thenDeny() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        for(String role:List.of("CS","USER")) {
            mvc.perform(get(path()).with(authentication(auth(role)))).andExpect(status().isForbidden());
            mvc.perform(post(path()).with(authentication(auth(role))).contentType("application/json").content(body("deny",starts)))
                    .andExpect(status().isForbidden());
        }
    }
    @Test void givenSameRequestKey_whenCreateTwice_thenReturnOneMission() throws Exception {
        mvc.perform(get(path()).with(authentication(auth("SUPER")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templates.length()").value(20));
        for(int i=0;i<2;i++) mvc.perform(post(path()).with(authentication(auth("OPERATOR"))).contentType("application/json")
                .content(body("same-key",starts))).andExpect(status().isAccepted());
        mvc.perform(get(path()).with(authentication(auth("OPERATOR")))).andExpect(jsonPath("$.data.items.length()").value(1));
    }
    @Test void givenInvalidScheduleOrId_whenMutateMission_thenReject() throws Exception {
        mvc.perform(post(path()).with(authentication(auth("OPERATOR"))).contentType("application/json")
                .content(body("bad-time",starts.plusDays(2)))).andExpect(status().isBadRequest());
        mvc.perform(delete(path()+"/999999").with(authentication(auth("OPERATOR")))).andExpect(status().isNotFound());
    }
    @Test @DisplayName("관리자가 사진을 업로드해 예약하고 제거할 수 있으며 다른 방의 사진은 거부한다")
    void givenUploadedPhoto_whenScheduleAndRemove_thenUsesVerifiedAttachment() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,30,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        org.mockito.BDDMockito.given(storage.publicBaseUrl()).willReturn("https://test.invalid/");
        org.mockito.BDDMockito.given(storage.createPresignedImageGetUrl(org.mockito.ArgumentMatchers.anyString())).willReturn("https://test.invalid/signed");
        var file=new org.springframework.mock.web.MockMultipartFile("file","reference.png","image/png",bytes.toByteArray());
        mvc.perform(multipart(path()+"/images").file(file)).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path()+"/images").file(file).with(authentication(auth("CS")))).andExpect(status().isForbidden());
        var upload=mvc.perform(multipart(path()+"/images").file(file).with(authentication(auth("OPERATOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.width").value(20))
                .andExpect(jsonPath("$.data.url").value("https://test.invalid/signed")).andReturn().getResponse().getContentAsString();
        Long imageId=com.jayway.jsonpath.JsonPath.parse(upload).read("$.data.id",Long.class);
        String photoBody=body("photo-key",starts).strip().replace("}",", \"image_id\": "+imageId+"}");
        var result=mvc.perform(post(path()).with(authentication(auth("OPERATOR"))).contentType("application/json").content(photoBody))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.image.id").value(imageId)).andReturn().getResponse().getContentAsString();
        Long missionId=com.jayway.jsonpath.JsonPath.parse(result).read("$.data.id",Long.class);
        mvc.perform(put(path()+"/"+missionId).with(authentication(auth("SUPER"))).contentType("application/json").content(body("photo-key",starts.plusMinutes(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.image").isEmpty());
        var other=RoomEntity.from(Room.createScheduled(starts,starts.plusDays(2),starts.plusDays(2).plusHours(1),starts.plusDays(2).plusHours(2),starts.plusDays(3),3));
        em.persist(other);em.flush();
        mvc.perform(post("/api/v1/admin/rooms/"+other.toDomain().id()+"/operator-missions").with(authentication(auth("OPERATOR"))).contentType("application/json").content(photoBody))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_MISSION_IMAGE"));
        mvc.perform(multipart(path()+"/images").file(new org.springframework.mock.web.MockMultipartFile("file","bad.png","image/png",new byte[]{1})).with(authentication(auth("OPERATOR"))))
                .andExpect(status().isBadRequest());
    }

}
