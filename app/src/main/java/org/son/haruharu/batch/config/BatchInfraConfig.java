package org.son.haruharu.batch.config;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.BatchConfigurationException;
import org.springframework.batch.core.configuration.support.DefaultBatchConfiguration;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 배치 공용 인프라 설정(#357). 스케줄러를 배치 잡으로 전환하는 전체 기반이며, <b>멱등/멀티 인스턴스 단일 실행</b>의
 * 실제 근간이 여기서 배선된다.
 * <p>
 * <b>왜 이 클래스가 필요한가</b>: Spring Batch 6 / Boot 4.1의 기본 오토컨픽({@code SpringBootBatchDefaultConfiguration})은
 * {@link DefaultBatchConfiguration}을 상속하고, 그 {@code jobRepository()}는 무조건 {@code ResourcelessJobRepository}
 * (인메모리·프로세스 로컬)를 반환한다. 그 상태로는 BATCH_* 테이블을 쓰지 않아 인스턴스 간 상태 공유가 없고,
 * 여러 서버가 같은 시각에 잡을 launch 하면 각자 "오늘 처음"이라 판단해 <b>모두 중복 실행</b>한다.
 * <p>
 * 이 설정은 {@code DefaultBatchConfiguration}을 상속해 {@code jobRepository()}를 <b>JDBC 백엔드</b>로 override 한다.
 * 이 빈이 존재하면 Boot의 Resourceless 기본 구성은 {@code @ConditionalOnMissingBean(DefaultBatchConfiguration)}
 * 조건으로 back-off 된다. 그 결과 잡 메타데이터가 공유 RDS의 {@code BATCH_JOB_INSTANCE(JOB_NAME, JOB_KEY)}
 * 유니크 제약으로 관리되어, 같은 식별 파라미터(예: 일 버킷)의 두 번째 launch 는 인스턴스가 달라도 거부된다.
 * <p>
 * BATCH_* 스키마 생성은 순환 참조(초기화기 → EMF → 이 설정의 트랜잭션매니저 → EMF)를 피하려고 DataSource 만
 * 의존하는 {@link BatchSchemaConfig}로 분리했다.
 */
@Configuration
public class BatchInfraConfig extends DefaultBatchConfiguration {

    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;

    public BatchInfraConfig(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.transactionManager = transactionManager;
    }

    /** 앱 DataSource/트랜잭션매니저에 바인딩된 JDBC JobRepository. 잡 이력·멱등 좌표계를 공유 RDS에 영속한다. */
    @Bean
    @Override
    public JobRepository jobRepository() {
        try {
            JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTransactionManager(transactionManager);
            factory.afterPropertiesSet();
            return factory.getObject();
        } catch (Exception e) {
            throw new BatchConfigurationException("JDBC JobRepository 구성 실패", e);
        }
    }
}
