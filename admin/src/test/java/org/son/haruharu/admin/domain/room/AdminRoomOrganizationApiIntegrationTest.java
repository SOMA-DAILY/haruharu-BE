package org.son.haruharu.admin.domain.room;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
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

import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminRoomOrganizationApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private long room() {
        var e = RoomEntity.from(Room.create(LocalDate.now(), 3));
        em.persist(e);
        em.flush();
        return e.toDomain().id();
    }

    private long group(String name) throws Exception {
        var raw =
                mvc.perform(
                                post("/api/v1/admin/room-groups")
                                        .with(authentication(auth("OPERATOR")))
                                        .contentType("application/json")
                                        .content("{\"name\":\"" + name + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return new tools.jackson.databind.ObjectMapper()
                .readTree(raw)
                .path("data")
                .path("id")
                .asLong();
    }

    @Test
    void groupsClassifyFilterAndMoveWithPagination() throws Exception {
        long a = room(), b = room(), legacy = room(), g = group("앱 1기"), g2 = group("앱 2기");
        mvc.perform(
                        patch("/api/v1/admin/rooms/organization")
                                .with(authentication(auth("OPERATOR")))
                                .contentType("application/json")
                                .content(
                                        "{\"room_ids\":["
                                                + a
                                                + ","
                                                + b
                                                + "],\"group_id\":"
                                                + g
                                                + ",\"is_test\":false}"))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/admin/rooms")
                                .param("groupId", "" + g)
                                .param("isTest", "false")
                                .param("size", "1")
                                .with(authentication(auth("CS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.page.total_elements").value(2));
        mvc.perform(
                        patch("/api/v1/admin/rooms/" + b + "/type")
                                .with(authentication(auth("OPERATOR")))
                                .contentType("application/json")
                                .content("{\"is_test\":true}"))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/admin/rooms")
                                .param("groupId", "" + g)
                                .param("isTest", "false")
                                .with(authentication(auth("CS"))))
                .andExpect(jsonPath("$.data.page.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].room_id").value(a));
        mvc.perform(get("/api/v1/admin/rooms/" + legacy).with(authentication(auth("CS"))))
                .andExpect(jsonPath("$.data.is_test").isEmpty())
                .andExpect(jsonPath("$.data.group_id").isEmpty());
        mvc.perform(
                        patch("/api/v1/admin/rooms/organization")
                                .with(authentication(auth("OPERATOR")))
                                .contentType("application/json")
                                .content("{\"room_ids\":[" + b + "],\"group_id\":" + g2 + "}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/rooms/" + b).with(authentication(auth("CS"))))
                .andExpect(jsonPath("$.data.group_id").value(g2))
                .andExpect(jsonPath("$.data.is_test").value(true));
        mvc.perform(
                        get("/api/v1/admin/rooms")
                                .param("ungrouped", "true")
                                .with(authentication(auth("CS"))))
                .andExpect(jsonPath("$.data.page.total_elements").value(1));
        mvc.perform(get("/api/v1/admin/room-groups").with(authentication(auth("CS"))))
                .andExpect(jsonPath("$.data.content[0].id").value(g2));
    }

    @Test
    void batchRejectsMissingRoomWithoutChangingOthers() throws Exception {
        long a = room(), g = group("앱 rollback");
        mvc.perform(
                        patch("/api/v1/admin/rooms/organization")
                                .with(authentication(auth("OPERATOR")))
                                .contentType("application/json")
                                .content(
                                        "{\"room_ids\":["
                                                + a
                                                + ",999999999],\"group_id\":"
                                                + g
                                                + ",\"is_test\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/rooms/" + a).with(authentication(auth("CS"))))
                .andExpect(jsonPath("$.data.group_id").isEmpty());
    }

    @Test
    void permissionsValidationAndGroupUniqueness() throws Exception {
        long a = room();
        mvc.perform(
                        post("/api/v1/admin/room-groups")
                                .with(authentication(auth("CS")))
                                .contentType("application/json")
                                .content("{\"name\":\"앱 1기\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/api/v1/admin/rooms/organization")
                                .with(authentication(auth("CS")))
                                .contentType("application/json")
                                .content("{\"room_ids\":[" + a + "],\"is_test\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/room-groups")).andExpect(status().isUnauthorized());
        group("앱 unique");
        mvc.perform(
                        post("/api/v1/admin/room-groups")
                                .with(authentication(auth("OPERATOR")))
                                .contentType("application/json")
                                .content("{\"name\":\"  앱 unique  \"}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        patch("/api/v1/admin/rooms/organization")
                                .with(authentication(auth("OPERATOR")))
                                .contentType("application/json")
                                .content("{\"room_ids\":[],\"is_test\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allWritesRequireAuthenticationAndTypeRequiresOperator() throws Exception {
        long a = room();
        mvc.perform(
                        post("/api/v1/admin/room-groups")
                                .contentType("application/json")
                                .content("{\"name\":\"앱 auth\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        patch("/api/v1/admin/rooms/organization")
                                .contentType("application/json")
                                .content("{\"room_ids\":[" + a + "],\"is_test\":false}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        patch("/api/v1/admin/rooms/" + a + "/type")
                                .contentType("application/json")
                                .content("{\"is_test\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        patch("/api/v1/admin/rooms/" + a + "/type")
                                .with(authentication(auth("CS")))
                                .contentType("application/json")
                                .content("{\"is_test\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void newRoomDefaultsToLatestGroupAndCanChooseAnother() throws Exception {
        long first = group("앱 creation 1"), latest = group("앱 creation 2");
        String body =
                "{\"starts_at\":\"2035-01-01T08:00:00\",\"final_selection_opens_at\":\"2035-01-03T18:00:00\",\"final_selection_closes_at\":\"2035-01-03T20:00:00\",\"result_at\":\"2035-01-03T21:00:00\",\"ends_at\":\"2035-01-03T22:00:00\",\"total_days\":3,\"missions\":[]";
        var response =
                mvc.perform(
                                post("/api/v1/admin/rooms")
                                        .with(authentication(auth("OPERATOR")))
                                        .contentType("application/json")
                                        .content(body + "}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id =
                new tools.jackson.databind.ObjectMapper()
                        .readTree(response)
                        .path("data")
                        .path("room_id")
                        .asLong();
        mvc.perform(get("/api/v1/admin/rooms/" + id).with(authentication(auth("CS"))))
                .andExpect(jsonPath("$.data.group_id").value(latest))
                .andExpect(jsonPath("$.data.is_test").value(false));
        response =
                mvc.perform(
                                post("/api/v1/admin/rooms")
                                        .with(authentication(auth("OPERATOR")))
                                        .contentType("application/json")
                                        .content(
                                                body
                                                        + ",\"group_id\":"
                                                        + first
                                                        + ",\"is_test\":true}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        id =
                new tools.jackson.databind.ObjectMapper()
                        .readTree(response)
                        .path("data")
                        .path("room_id")
                        .asLong();
        mvc.perform(get("/api/v1/admin/rooms/" + id).with(authentication(auth("CS"))))
                .andExpect(jsonPath("$.data.group_id").value(first))
                .andExpect(jsonPath("$.data.is_test").value(true));
    }
}
