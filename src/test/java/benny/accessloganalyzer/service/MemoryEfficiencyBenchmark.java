package benny.accessloganalyzer.service;

import benny.accessloganalyzer.model.AnalysisEntry;
import benny.accessloganalyzer.parser.AccessLogCsvParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;

/**
 * 메모리 효율성 벤치마크 — 개선 전후 비교용.
 *
 * 실행: ./gradlew test --tests "*.MemoryEfficiencyBenchmark" -i
 */
@Tag("benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemoryEfficiencyBenchmark {

    private static final int LINE_COUNT = 200_000;
    private static final int WARMUP_LINE_COUNT = 1_000;
    private static final int ITERATIONS = 3;

    private static final Executor SYNC_EXECUTOR = Runnable::run;
    private static final String HEADER = "TimeGenerated [UTC],ClientIp,HttpMethod,RequestUri,UserAgent,HttpStatus,HttpVersion,ReceivedBytes,SentBytes,ClientResponseTime,SslProtocol,OriginalRequestUriWithArgs";

    private static byte[] largeCsv;
    private static byte[] warmupCsv;

    @BeforeAll
    static void generateTestData() {
        System.out.println("=== 테스트 데이터 생성 중 ===");

        warmupCsv = generateCsv(WARMUP_LINE_COUNT);
        largeCsv = generateCsv(LINE_COUNT);

        System.out.printf("CSV 데이터 크기: %.2f MB (%,d lines)%n",
                largeCsv.length / (1024.0 * 1024.0), LINE_COUNT);
        System.out.println();
    }

    @Test
    @Order(1)
    void warmup() {
        System.out.println("=== 워밍업 (JIT 컴파일 유도) ===");
        AnalysisService service = createService();
        for (int i = 0; i < 5; i++) {
            service.submitAnalysis(warmupCsv);
        }
        System.out.println("워밍업 완료\n");
    }

    @Test
    @Order(2)
    void benchmarkMemoryAndTime() {
        System.out.println("=== 메모리 효율성 벤치마크 시작 ===");
        System.out.printf("대상: %,d lines × %d iterations%n%n", LINE_COUNT, ITERATIONS);

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        long[] durations = new long[ITERATIONS];
        long[] peakMemories = new long[ITERATIONS];
        long[] memoryDeltas = new long[ITERATIONS];
        long[] gcCounts = new long[ITERATIONS];
        long[] gcTimes = new long[ITERATIONS];

        for (int i = 0; i < ITERATIONS; i++) {
            AnalysisService service = createService();

            // GC 정리 후 기준 메모리 측정
            forceGc();
            long baselineMemory = usedHeapBytes(memoryBean);
            long gcCountBefore = totalGcCount(gcBeans);
            long gcTimeBefore = totalGcTimeMs(gcBeans);

            // 실행
            long startNanos = System.nanoTime();
            String analysisId = service.submitAnalysis(largeCsv);
            long endNanos = System.nanoTime();

            // 결과 확인
            AnalysisEntry entry = service.getEntry(analysisId);

            // GC 정리 전 피크 메모리 (객체가 아직 살아있을 때)
            long peakMemory = usedHeapBytes(memoryBean);

            long gcCountAfter = totalGcCount(gcBeans);
            long gcTimeAfter = totalGcTimeMs(gcBeans);

            durations[i] = (endNanos - startNanos) / 1_000_000;
            peakMemories[i] = peakMemory;
            memoryDeltas[i] = peakMemory - baselineMemory;
            gcCounts[i] = gcCountAfter - gcCountBefore;
            gcTimes[i] = gcTimeAfter - gcTimeBefore;

            System.out.printf("[Iteration %d] time=%dms, memDelta=%.2fMB, peak=%.2fMB, GC=%d회(%dms), totalRequests=%d%n",
                    i + 1,
                    durations[i],
                    memoryDeltas[i] / (1024.0 * 1024.0),
                    peakMemories[i] / (1024.0 * 1024.0),
                    gcCounts[i],
                    gcTimes[i],
                    entry.getResult().totalRequests());
        }

        System.out.println();
        printSummary("실행 시간 (ms)", durations);
        printSummary("메모리 증가량 (bytes)", memoryDeltas);
        printSummary("GC 횟수", gcCounts);
        printSummary("GC 소요 (ms)", gcTimes);

        System.out.println();
        System.out.println("────────────────────────────────────────────────────────────");
        System.out.printf("평균 실행 시간: %d ms%n", avg(durations));
        System.out.printf("평균 메모리 증가: %.2f MB%n", avg(memoryDeltas) / (1024.0 * 1024.0));
        System.out.printf("평균 GC 횟수: %d 회%n", avg(gcCounts));
        System.out.printf("평균 GC 소요: %d ms%n", avg(gcTimes));
        System.out.println("────────────────────────────────────────────────────────────");
    }

    // --- helpers ---

    private AnalysisService createService() {
        return new AnalysisService(new AccessLogCsvParser(), LINE_COUNT + 1, SYNC_EXECUTOR);
    }

    private static byte[] generateCsv(int lineCount) {
        Random random = new Random(42);
        String[] paths = {"/api/users", "/api/orders", "/api/products", "/api/auth/login",
                "/api/auth/logout", "/event/banner/popup", "/assets/main.css",
                "/assets/app.js", "/health", "/api/search"};
        String[] ips = new String[500];
        for (int i = 0; i < ips.length; i++) {
            ips[i] = (random.nextInt(223) + 1) + "." + random.nextInt(256) + "."
                    + random.nextInt(256) + "." + (random.nextInt(254) + 1);
        }
        int[] statuses = {200, 200, 200, 200, 200, 201, 301, 302, 400, 404, 404, 500};
        String[] methods = {"GET", "GET", "GET", "POST", "PUT", "DELETE"};
        String[] agents = {
                "\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\"",
                "MyThreadedApp/1.0",
                "PostmanRuntime/7.32.3",
                "curl/8.1.2"
        };

        StringBuilder sb = new StringBuilder(lineCount * 200);
        sb.append(HEADER).append('\n');

        for (int i = 0; i < lineCount; i++) {
            String ip = ips[random.nextInt(ips.length)];
            String method = methods[random.nextInt(methods.length)];
            String path = paths[random.nextInt(paths.length)];
            String agent = agents[random.nextInt(agents.length)];
            int status = statuses[random.nextInt(statuses.length)];
            long received = random.nextInt(5000) + 100;
            long sent = random.nextInt(10000) + 200;
            double responseTime = Math.round(random.nextDouble() * 2.0 * 1000.0) / 1000.0;

            sb.append("\"1/29/2026, 5:44:10.000 AM\",");
            sb.append(ip).append(',');
            sb.append(method).append(',');
            sb.append(path).append(',');
            sb.append(agent).append(',');
            sb.append(status).append(',');
            sb.append("HTTP/1.1,");
            sb.append(received).append(',');
            sb.append(sent).append(',');
            sb.append(responseTime).append(',');
            sb.append("TLSv1.3,");
            sb.append(path).append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void forceGc() {
        System.gc();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    }

    private static long usedHeapBytes(MemoryMXBean bean) {
        return bean.getHeapMemoryUsage().getUsed();
    }

    private static long totalGcCount(List<GarbageCollectorMXBean> beans) {
        return beans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private static long totalGcTimeMs(List<GarbageCollectorMXBean> beans) {
        return beans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    private static long avg(long[] values) {
        long sum = 0;
        for (long v : values) sum += v;
        return sum / values.length;
    }

    private static void printSummary(String label, long[] values) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  %-24s", label));
        for (int i = 0; i < values.length; i++) {
            sb.append(String.format(" [%d]=%,d", i + 1, values[i]));
        }
        System.out.println(sb);
    }
}
