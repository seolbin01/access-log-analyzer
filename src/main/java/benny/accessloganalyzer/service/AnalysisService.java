package benny.accessloganalyzer.service;

import benny.accessloganalyzer.global.exception.BusinessException;
import benny.accessloganalyzer.model.AccessLogEntry;
import benny.accessloganalyzer.model.AnalysisEntry;
import benny.accessloganalyzer.model.AnalysisResult;
import benny.accessloganalyzer.model.AnalysisStatus;
import benny.accessloganalyzer.parser.AccessLogCsvParser;
import benny.accessloganalyzer.parser.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnalysisService {

    private static final int DEFAULT_MAX_LINES = 200_000;

    private final AccessLogCsvParser parser;
    private final int maxLines;
    private final Executor executor;
    private final ConcurrentHashMap<String, AnalysisEntry> store = new ConcurrentHashMap<>();
    private final AtomicLong orderSequence = new AtomicLong();

    @Autowired
    public AnalysisService(AccessLogCsvParser parser, Executor analysisExecutor) {
        this(parser, DEFAULT_MAX_LINES, analysisExecutor);
    }

    AnalysisService(AccessLogCsvParser parser, int maxLines, Executor executor) {
        this.parser = parser;
        this.maxLines = maxLines;
        this.executor = executor;
    }

    public String submitAnalysis(byte[] fileBytes) {
        String analysisId = UUID.randomUUID().toString();
        AnalysisEntry entry = new AnalysisEntry(analysisId, orderSequence.incrementAndGet());
        store.put(analysisId, entry);

        try {
            executor.execute(() -> executeAnalysis(analysisId, fileBytes));
        } catch (RejectedExecutionException e) {
            store.remove(analysisId);
            throw BusinessException.analysisQueueFull("분석 큐가 가득 찼습니다. 잠시 후 다시 시도해주세요.");
        }

        return analysisId;
    }

    private void executeAnalysis(String analysisId, byte[] fileBytes) {
        AnalysisEntry entry = store.get(analysisId);
        entry.startProcessing();

        log.info("분석 시작: analysisId={}", analysisId);
        long startNanos = System.nanoTime();

        try {
            AnalysisResult result = analyze(new ByteArrayInputStream(fileBytes), analysisId);
            entry.complete(result);

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("분석 완료: analysisId={}, totalLines={}, errorCount={}, duration={}ms",
                    analysisId, result.totalLines(), result.errorCount(), durationMs);
        } catch (Exception e) {
            log.error("분석 실패: analysisId={}", analysisId, e);
            entry.fail(e.getMessage());
        }
    }

    private AnalysisResult analyze(InputStream inputStream, String analysisId) {
        ParseResult parseResult = parser.parse(inputStream);

        validate(parseResult);

        List<AccessLogEntry> entries = parseResult.entries();

        Map<String, Long> statusCodeCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        e -> String.valueOf(e.httpStatus()),
                        Collectors.counting()));

        Map<String, Long> statusGroupCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        e -> (e.httpStatus() / 100) + "xx",
                        Collectors.counting()));

        Map<String, Long> pathCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        AccessLogEntry::requestUri,
                        Collectors.counting()));

        Map<String, Long> ipCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        AccessLogEntry::clientIp,
                        Collectors.counting()));

        return new AnalysisResult(
                analysisId,
                LocalDateTime.now(),
                entries.size(),
                statusCodeCounts,
                statusGroupCounts,
                pathCounts,
                ipCounts,
                parseResult.totalLines(),
                parseResult.errorCount(),
                parseResult.errorSamples()
        );
    }

    public AnalysisEntry getEntry(String analysisId) {
        AnalysisEntry entry = store.get(analysisId);
        if (entry == null) {
            throw BusinessException.analysisNotFound("분석 결과를 찾을 수 없습니다: " + analysisId);
        }
        return entry;
    }

    public int getQueuePosition(AnalysisEntry targetEntry) {
        long position = store.values().stream()
                .filter(e -> e.getStatus() == AnalysisStatus.QUEUED)
                .filter(e -> e.getSubmittedOrder() <= targetEntry.getSubmittedOrder())
                .count();
        return (int) position;
    }

    private void validate(ParseResult parseResult) {
        if (parseResult.totalLines() > maxLines) {
            throw BusinessException.invalidLogFile(
                    "최대 라인 수(" + maxLines + ")를 초과했습니다: " + parseResult.totalLines());
        }
        if (parseResult.entries().isEmpty()) {
            throw BusinessException.invalidLogFile("유효한 로그 데이터가 없습니다");
        }
    }
}
