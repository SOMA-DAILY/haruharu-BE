package org.son.haruharu.admin.domain.appupdate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdminAppUpdatePolicyApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private static final String URL = "/api/v1/admin/app-update-policy";
    @BeforeEach void seed() { jdbc.update("insert into app_update_policy (id, revision) values (1, 0)"); }
    private UsernamePasswordAuthenticationToken auth(String role) { return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))); }
    private String body(String ios, String android, long revision) { return "{\"ios_minimum_version\":%s,\"android_minimum_version\":%s,\"revision\":%d}".formatted(ios, android, revision); }
    @Test void givenSuper_whenSaveThenClear_thenPolicyAndHistoryAreAtomic() throws Exception {
        mvc.perform(get(URL).with(authentication(auth("CS")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(0));
        mvc.perform(put(URL).with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(body("\"1.1.1\"", "\"1.2.0\"", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ios_minimum_version").value("1.1.1")).andExpect(jsonPath("$.data.revision").value(1));
        mvc.perform(put(URL).with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(body("null", "\"1.2.0\"", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(2));
        mvc.perform(get(URL+"/history").with(authentication(auth("SUPER")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2)).andExpect(jsonPath("$.data[0].previous_ios_minimum_version").value("1.1.1"));
        assertThat(jdbc.queryForObject("select count(*) from app_update_policy_history", Long.class)).isEqualTo(2L);
    }
    @Test void givenInvalidOrStaleWrite_whenSave_thenPreservesPolicyAndHistory() throws Exception {
        mvc.perform(put(URL).with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(body("\"v1.1\"", "null", 0))).andExpect(status().isBadRequest());
        mvc.perform(put(URL).with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(body("\"1.1.1\"", "null", 99))).andExpect(status().isConflict());
        mvc.perform(put(URL).with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content("{\"ios_minimum_version\":\"1.1.1\"}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select revision from app_update_policy where id=1", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from app_update_policy_history", Long.class)).isZero();
    }
    @Test void givenUnchangedPolicy_whenSave_thenDoesNotCreateRevision() throws Exception {
        mvc.perform(put(URL).with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(body("null", "null", 0))).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(0));
        assertThat(jdbc.queryForObject("select count(*) from app_update_policy_history", Long.class)).isZero();
    }
    @Test void givenNonSuperOrAnonymous_whenWrite_thenDenied() throws Exception {
        for(String role:List.of("OPERATOR","CS")) mvc.perform(put(URL).with(authentication(auth(role))).contentType(MediaType.APPLICATION_JSON).content(body("\"1.1.1\"", "null", 0))).andExpect(status().isForbidden());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"1.1.1\"", "null", 0))).andExpect(status().isUnauthorized());
    }
}
