package org.son.haruharu.batch.config;

import java.sql.DatabaseMetaData;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

/**
 * BATCH_* 메타 테이블 스키마 초기화(#357). Boot 4.1의 spring-boot-batch 모듈은 구 {@code spring.batch.jdbc.*}
 * 스키마 자동 초기화를 더 이상 제공하지 않으므로 여기서 명시적으로 생성한다.
 * <p>
 * DataSource 만 의존한다. Boot의 DB 초기화 정렬 규칙은 JPA {@code entityManagerFactory}가 이 초기화기 뒤에
 * 생성되도록 depends-on 을 건다. 초기화기가 트랜잭션매니저(→EMF)를 의존하면 순환이 생기므로,
 * JDBC JobRepository를 배선하는 {@link BatchInfraConfig}(트랜잭션매니저 의존)와 분리해 둔다.
 */
@Configuration
public class BatchSchemaConfig {

    /**
     * spring-batch-core 가 제공하는 벤더별 스키마({@code schema-postgresql.sql}, {@code schema-h2.sql} 등)를 실행한다.
     * 범용 초기화기의 {@code @@platform@@} 치환은 {@code spring.sql.init.platform}(기본 all)에 의존해 자동 해석되지
     * 않으므로, DataSource 메타데이터로 벤더를 직접 판별해 구체 경로를 넘긴다.
     * 재기동 시 {@code CREATE TABLE} 충돌은 {@code continueOnError}로 흡수한다(이미 존재하면 무시).
     */
    @Bean
    public DataSourceScriptDatabaseInitializer batchJobRepositoryDatabaseInitializer(DataSource dataSource) {
        String location = "classpath:org/springframework/batch/core/schema-" + resolvePlatform(dataSource) + ".sql";
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(location));
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        settings.setContinueOnError(true);
        return new DataSourceScriptDatabaseInitializer(dataSource, settings);
    }

    private String resolvePlatform(DataSource dataSource) {
        try {
            String productName = JdbcUtils.commonDatabaseName(
                    JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName));
            return DatabaseDriver.fromProductName(productName).getId(); // postgresql / h2 ...
        } catch (MetaDataAccessException e) {
            throw new IllegalStateException("배치 스키마 초기화용 DB 벤더 판별 실패", e);
        }
    }
}
