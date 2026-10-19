package org.son.haruharu.admin.domain.appupdate;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.son.haruharu.domain.appupdate.business.AppUpdatePolicyService;
import org.son.haruharu.domain.appupdate.business.command.UpdateAppUpdatePolicyCommand;
import org.son.haruharu.domain.appupdate.implement.exception.AppUpdatePolicyConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.MountableFile;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Testcontainers @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppUpdatePolicyPostgresIntegrationTest {
    static Path script() {
        Path p=Path.of("").toAbsolutePath();
        while(p!=null&&!Files.isRegularFile(p.resolve("scripts/sql/479-app-update-policy-up.sql")))p=p.getParent();
        if(p==null)throw new IllegalStateException("Policy SQL not found");
        return p.resolve("scripts/sql/479-app-update-policy-up.sql");
    }
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withCopyFileToContainer(MountableFile.forHostPath(script()), "/docker-entrypoint-initdb.d/policy.sql");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.datasource.driver-class-name",POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto",()->"update");
        r.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUpdatePolicyService service;
    @BeforeEach void reset() {
        jdbc.update("delete from app_update_policy_history");
        jdbc.update("update app_update_policy set ios_minimum_version=null,android_minimum_version=null,revision=0,updated_by=null,updated_at=null where id=1");
    }
    @Test void givenSameRevision_whenConcurrentWrites_thenOnlyOneCommits() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> results=List.of("1.1.1","1.2.0").stream().map(v->pool.submit(()->{
                start.await();try {service.update(new UpdateAppUpdatePolicyCommand(v,null,0),1L);return "saved";}
                catch(AppUpdatePolicyConflictException e){return "conflict";}
            })).toList();
            start.countDown();
            assertThat(List.of(results.get(0).get(10,TimeUnit.SECONDS),results.get(1).get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","conflict");
        }
        assertThat(jdbc.queryForObject("select revision from app_update_policy where id=1",Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from app_update_policy_history",Long.class)).isEqualTo(1L);
    }
    @Test void givenHistoryWriteFailure_whenSaving_thenPolicyWriteRollsBack() {
        jdbc.update("insert into app_update_policy_history(revision,changed_by,changed_at) values (1,1,current_timestamp)");
        assertThatThrownBy(()->service.update(new UpdateAppUpdatePolicyCommand("1.1.1",null,0),1L)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select revision from app_update_policy where id=1",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select ios_minimum_version from app_update_policy where id=1",String.class)).isNull();
    }
    @Test void givenExistingPolicy_whenReapplyingMigration_thenDoesNotOverwrite() throws Exception {
        service.update(new UpdateAppUpdatePolicyCommand("1.1.1","1.2.0",0),1L);
        jdbc.execute(Files.readString(script()));
        assertThat(service.get().iosMinimumVersion()).isEqualTo("1.1.1");
        assertThat(service.get().revision()).isEqualTo(1);
    }
}
