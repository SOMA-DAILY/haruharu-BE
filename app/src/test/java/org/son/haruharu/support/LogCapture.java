package org.son.haruharu.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * 테스트에서 로그 이벤트를 붙잡는다(#259).
 *
 * 로그 자체가 산출물인 기능(access log·인증 실패 로그)에서는 "무엇을 남기는가"뿐 아니라
 * <b>"무엇을 남기지 않는가"</b>(토큰 원문·PII 부재)를 단언해야 하는데, 그러려면 로그 이벤트를
 * 직접 잡는 것 외에 방법이 없다. 그 ListAppender 부착·해제 보일러플레이트를 모은다.
 *
 * <pre>
 * private LogCapture logs;
 *
 * &#64;BeforeEach void setUp() { logs = LogCapture.of(AccessLogFilter.class); }
 * &#64;AfterEach  void tearDown() { logs.close(); }
 * </pre>
 */
public final class LogCapture implements AutoCloseable {

    private final List<Logger> loggers;
    private final ListAppender<ILoggingEvent> appender;

    private LogCapture(List<Logger> loggers, ListAppender<ILoggingEvent> appender) {
        this.loggers = loggers;
        this.appender = appender;
    }

    /** 지정한 클래스들의 로거에 앱엔더를 붙인다. 여러 개를 넘기면 한 캡처로 합쳐 본다. */
    public static LogCapture of(Class<?>... sources) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        List<Logger> loggers = Arrays.stream(sources)
                .map(source -> (Logger) LoggerFactory.getLogger(source))
                .peek(logger -> logger.addAppender(appender))
                .toList();
        return new LogCapture(loggers, appender);
    }

    /** DEBUG 등 기본 레벨보다 낮은 로그를 봐야 할 때. */
    public LogCapture atLevel(Level level) {
        loggers.forEach(logger -> logger.setLevel(level));
        return this;
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public List<ILoggingEvent> at(Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level).toList();
    }

    public ILoggingEvent first() {
        return appender.list.getFirst();
    }

    /** 남은 로그 메시지 전문. 특정 문자열의 부재를 단언할 때 쓴다. */
    public String text() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Override
    public void close() {
        loggers.forEach(logger -> logger.detachAppender(appender));
    }
}
