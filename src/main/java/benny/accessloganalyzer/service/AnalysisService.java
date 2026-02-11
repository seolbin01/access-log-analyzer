package benny.accessloganalyzer.service;

import benny.accessloganalyzer.global.exception.BusinessException;
import benny.accessloganalyzer.model.AccessLogEntry;
import benny.accessloganalyzer.model.AnalysisResult;
import benny.accessloganalyzer.parser.AccessLogCsvParser;
import benny.accessloganalyzer.parser.ParseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private static final int DEFAULT_MAX_LINES = 200_000;

    private final AccessLogCsvParser parser;
    private final int maxLines;
    private final ConcurrentHashMap<String, AnalysisResult> store = new ConcurrentHashMap<>();

    @Autowired
    public AnalysisService(AccessLogCsvParser parser) {
        this(parser, DEFAULT_MAX_LINES);
    }

    AnalysisService(AccessLogCsvParser parser, int maxLines) {
        this.parser = parser;
        this.maxLines = maxLines;
    }

    public AnalysisResult analyze(InputStream inputStream) {
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

        String analysisId = UUID.randomUUID().toString();

        AnalysisResult result = new AnalysisResult(
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

        store.put(analysisId, result);
        return result;
    }

    public AnalysisResult getResult(String analysisId) {
        AnalysisResult result = store.get(analysisId);
        if (result == null) {
            throw BusinessException.analysisNotFound("분석 결과를 찾을 수 없습니다: " + analysisId);
        }
        return result;
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
