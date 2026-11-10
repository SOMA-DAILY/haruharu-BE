package org.son.haruharu.domain.replyrecommendation.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.replyrecommendation.implement.SavepointRunner;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션에 묶인 JDBC 연결(JPA 트랜잭션이 노출한 같은 연결)에 savepoint 를 걸고 작업을 실행한다.
 * PostgreSQL 은 문장 하나가 실패하면 트랜잭션 전체가 중단되므로, 실패한 작업만 되돌려 바깥 트랜잭션을 살린다.
 */
@Component
public class JdbcSavepointRunner implements SavepointRunner {

    private static final Logger log = LoggerFactory.getLogger(JdbcSavepointRunner.class);

    private final DataSource dataSource;

    public JdbcSavepointRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean runIsolated(Runnable work) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            try {
                work.run();
                return true;
            } catch (RuntimeException failure) {
                warn(failure);
                return false;
            }
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            Savepoint savepoint;
            try {
                savepoint = connection.setSavepoint();
            } catch (SQLException failure) {
                warn(failure);
                return false;
            }
            try {
                work.run();
                connection.releaseSavepoint(savepoint);
                return true;
            } catch (RuntimeException | SQLException failure) {
                try {
                    connection.rollback(savepoint);
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                warn(failure);
                return false;
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static void warn(Exception failure) {
        // 원문은 남기지 않는다. 예외 종류만 남긴다(DB 메시지에 값이 실릴 수 있다, #259).
        log.atWarn().addKeyValue("event", "REPLY_RECOMMENDATION_ISOLATED_WRITE_FAILED")
                .addKeyValue("error", failure.getClass().getSimpleName())
                .log("savepoint 안의 답장 추천 작업 실패, 그 부분만 되돌림");
    }
}
