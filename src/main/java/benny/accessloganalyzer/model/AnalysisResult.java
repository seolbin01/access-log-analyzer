package benny.accessloganalyzer.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AnalysisResult(
        String analysisId,
        LocalDateTime analyzedAt,
        int totalRequests,
        Map<String, Long> statusCodeCounts,
        Map<String, Long> statusGroupCounts,
        Map<String, Long> pathCounts,
        Map<String, Long> ipCounts,
        int totalLines,
        int errorCount,
        List<String> errorSamples
) {
}
