package org.son.haruharu.diagnostics;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

/** Opt-in diagnostic listener, injected only by test-context-profile.init.gradle. */
public final class TestContextMetrics implements SpringApplicationRunListener {
    private static final Path OUTPUT = Path.of(System.getProperty("test.context.metrics"));
    private static final AtomicLong PEAK_HEAP = new AtomicLong();
    // Keep numeric IDs only: retaining ApplicationContext here would invalidate the measurement.
    private static final Set<Integer> OPEN = new HashSet<>();
    private static int nextId;
    private static int created;
    private static int maxOpen;

    static {
        write("jvm\tpid=" + ProcessHandle.current().pid()
                + "\tmaxHeapBytes=" + Runtime.getRuntime().maxMemory());
        Thread sampler = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                sampleHeap();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "test-context-heap-sampler");
        sampler.setDaemon(true);
        sampler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sampleHeap();
            synchronized (TestContextMetrics.class) {
                write("summary\tcreated=" + created + "\tmaxOpen=" + maxOpen
                        + "\tpeakHeapBytes=" + PEAK_HEAP.get());
            }
        }, "test-context-metrics-summary"));
    }

    public TestContextMetrics(SpringApplication application, String[] args) {
    }

    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        final int id;
        synchronized (TestContextMetrics.class) {
            id = ++nextId;
        }
        context.addApplicationListener(event -> {
            if (event.getSource() != context) {
                return;
            }
            synchronized (TestContextMetrics.class) {
                if (event instanceof ContextRefreshedEvent && OPEN.add(id)) {
                    created++;
                    maxOpen = Math.max(maxOpen, OPEN.size());
                    write("refresh\tid=" + id + "\topen=" + OPEN.size());
                } else if (event instanceof ContextClosedEvent && OPEN.remove(id)) {
                    write("close\tid=" + id + "\topen=" + OPEN.size());
                }
            }
        });
    }

    private static void sampleHeap() {
        PEAK_HEAP.accumulateAndGet(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(), Math::max);
    }

    private static void write(String record) {
        try {
            Files.writeString(OUTPUT, System.nanoTime() + "\t" + record + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write test context metrics", e);
        }
    }
}
