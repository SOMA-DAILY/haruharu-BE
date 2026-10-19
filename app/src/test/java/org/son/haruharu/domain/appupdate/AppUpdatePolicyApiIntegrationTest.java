package org.son.haruharu.domain.appupdate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AppUpdatePolicyApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Test void givenNoPolicy_whenAnonymousLookup_thenDisabledAndNoCache() throws Exception {
        mvc.perform(get("/api/v1/meta/app-update-policy")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.revision").value(0)).andExpect(jsonPath("$.data.updated_by").doesNotExist());
    }
    @Test void givenSavedPolicy_whenAnonymousLookup_thenReturnsBothPlatformsWithoutAdminIdentity() throws Exception {
        jdbc.update("insert into app_update_policy (id, ios_minimum_version, android_minimum_version, revision, updated_by) values (1, '1.1.1', '1.2.0', 7, 999)");
        mvc.perform(get("/api/v1/meta/app-update-policy")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ios_minimum_version").value("1.1.1"))
                .andExpect(jsonPath("$.data.android_minimum_version").value("1.2.0"))
                .andExpect(jsonPath("$.data.updated_by").doesNotExist());
    }
}
