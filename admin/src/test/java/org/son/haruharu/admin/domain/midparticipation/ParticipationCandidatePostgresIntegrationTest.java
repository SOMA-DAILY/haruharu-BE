package org.son.haruharu.admin.domain.midparticipation;

import java.time.LocalDateTime;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.son.haruharu.admin.domain.midparticipation.business.ParticipationCandidateService;
import org.son.haruharu.admin.domain.midparticipation.implement.ParticipationCandidate;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ParticipationCandidatePostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.datasource.driver-class-name",POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto",()->"create-drop");
        r.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired ParticipationCandidateService candidates;
    @Autowired UserJpaRepository users;
    @Autowired RoomWriter rooms;
    @Autowired RoomMemberWriter members;
    @Autowired JdbcTemplate sql;
    @Autowired MockMvc mvc;
    private Long user() { return users.save(UserEntity.from(new User(null,UserStatus.ACTIVE,null))).toDomain().id(); }

    @Test void allUploadedVideosIncludingTheCurrentPartialDayAreCountedAndArchivedOriginalsRemain() {
        var now=LocalDateTime.now();var start=now.minusHours(2);
        var room=rooms.save(Room.createScheduled(start,now.plusDays(1),now.plusDays(1).plusHours(1),now.plusDays(1).plusHours(2),now.plusDays(2),3));
        Long uploader=user(),empty=user();
        var member=members.save(RoomMember.of(room.id(),uploader,"FEMALE",null,start));
        members.save(RoomMember.of(room.id(),empty,"MALE",null,start));
        sql.update("insert into video_posts(room_member_id,slot_start,video_url,is_hidden,created_at) select ?,?,'local-test-video',false,? from generate_series(1,3)",member.id(),start,now);
        var rows=candidates.candidates(null,null,0,100).content();
        var uploaded=rows.stream().filter(x->x.userId().equals(uploader)).findFirst().orElseThrow();
        assertThat(uploaded.videoCount()).isEqualTo(3);
        assertThat(rows.indexOf(uploaded)).isLessThan(rows.indexOf(rows.stream().filter(x->x.userId().equals(empty)).findFirst().orElseThrow()));
        sql.update("update room_members set member_removed_at=? where id=?",now,member.id());
        sql.update("update video_posts set member_removed_at=? where room_member_id=?",now,member.id());
        assertThat(candidates.candidates(String.valueOf(uploader),null,0,20).content().get(0).videoCount()).isEqualTo(3);
    }

    @Test void genderFilterUsesProfileGenderAndIncludesPeopleWithNoVideos() {
        Long female=user(),male=user();
        for(var row:List.of(new Object[]{female,"FEMALE"},new Object[]{male,"MALE"})) {
            sql.update("insert into user_profiles(user_id,nickname,gender,birth_year,created_at,updated_at) values (?,'QA 참가자',?,2000,now(),now())",row[0],row[1]);
        }
        var rows=candidates.candidates(null,"FEMALE",0,100);
        assertThat(rows.content()).allSatisfy(c->assertThat(c.gender()).isEqualTo("FEMALE"));
        assertThat(rows.content()).extracting(ParticipationCandidate::userId).contains(female).doesNotContain(male);
        assertThat(rows.content().stream().filter(c->c.userId().equals(female)).findFirst().orElseThrow().videoCount()).isZero();
        assertThat(rows.content()).isNotEmpty();
    }

    @Test void verifiedGenderWinsOverStaleOrMissingProfileGender() {
        Long stale=user(),missing=user();
        sql.update("insert into user_profiles(user_id,nickname,gender,birth_year,created_at,updated_at) values (?,'QA 프로필','MALE',2000,now(),now())",stale);
        for(Long id:List.of(stale,missing)) {
            sql.update("insert into user_identities(id,user_id,gender,created_at,updated_at) values (?, ?,'FEMALE',now(),now())",id,id);
        }
        var female=candidates.candidates(null,"FEMALE",0,100).content();
        assertThat(female).extracting(ParticipationCandidate::userId).contains(stale,missing);
        assertThat(female).filteredOn(c->List.of(stale,missing).contains(c.userId())).allSatisfy(c->assertThat(c.gender()).isEqualTo("FEMALE"));
        assertThat(candidates.candidates(null,"MALE",0,100).content()).extracting(ParticipationCandidate::userId).doesNotContain(stale,missing);
    }

    @Test void csCannotSendInvitationsOrRemoveMembers() throws Exception {
        var cs=new UsernamePasswordAuthenticationToken(1L,null,List.of(new SimpleGrantedAuthority("ROLE_CS")));
        for(String path:List.of("/api/v1/admin/mid-participation/outreach","/api/v1/admin/mid-participation/campaigns/1/resend",
                "/api/v1/admin/mid-participation/campaigns/1/cancel","/api/v1/admin/rooms/1/mid-participation","/api/v1/admin/rooms/1/members/1/remove")) {
            mvc.perform(post(path).with(authentication(cs)).contentType("application/json").content("{\"user_ids\":[1],\"seats\":1}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void additiveMigrationCanRunTwiceAndVerifyCleanly() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(root!=null && !Files.isDirectory(root.resolve("scripts/sql"))) root=root.getParent();
        assertThat(root).isNotNull();
        var up=Files.readString(root.resolve("scripts/sql/548-mid-participation-up.sql"));
        // 기존 DB의 Hibernate enum CHECK는 ddl-auto=update로 갱신되지 않는다.
        sql.execute("alter table notification_history drop constraint if exists notification_history_type_check");
        sql.execute("alter table notification_history add constraint notification_history_type_check check (type not in ('MID_PARTICIPATION_INTEREST','MID_PARTICIPATION_OFFER'))");
        sql.execute(up);sql.execute(up);
        for (String type : List.of("MID_PARTICIPATION_INTEREST", "MID_PARTICIPATION_OFFER")) {
            sql.update("insert into notification_history(user_id,type,title,body,payload_json,is_read,created_at,updated_at) values (?,?,'안내','안내','{}',false,now(),now())", user(), type);
        }
        var rows=sql.queryForList(Files.readString(root.resolve("scripts/sql/548-mid-participation-verify.sql")));
        assertThat(rows).allSatisfy(row->assertThat(((Number)row.get("violations")).longValue()).isZero());
    }
}
