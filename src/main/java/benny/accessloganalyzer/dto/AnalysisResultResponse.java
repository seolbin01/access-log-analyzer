package benny.accessloganalyzer.dto;

import benny.accessloganalyzer.model.AnalysisResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AnalysisResultResponse(
        String analysisId,
        LocalDateTime analyzedAt,
        int totalRequests,
        int totalLines,
        Map<String, Double> statusGroupRatios,
        List<PathCount> topPaths,
        List<StatusCodeCount> topStatusCodes,
        List<IpCount> topIps,
        ErrorInfo errorInfo
) {

    public record PathCount(String path, long count, double percentage) {}
    public record StatusCodeCount(String statusCode, long count, double percentage) {}
    public record IpCount(String ip, long count, double percentage) {}
    public record ErrorInfo(int errorCount, List<String> errorSamples) {}

    public static AnalysisResultResponse from(AnalysisResult result, int topN) {
        int total = result.totalRequests();

        Map<String, Double> statusGroupRatios = buildStatusGroupRatios(result.statusGroupCounts(), total);

        List<PathCount> topPaths = result.pathCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(e -> new PathCount(e.getKey(), e.getValue(), percentage(e.getValue(), total)))
                .toList();

        List<StatusCodeCount> topStatusCodes = result.statusCodeCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(e -> new StatusCodeCount(e.getKey(), e.getValue(), percentage(e.getValue(), total)))
                .toList();

        List<IpCount> topIps = result.ipCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(e -> new IpCount(e.getKey(), e.getValue(), percentage(e.getValue(), total)))
                .toList();

        ErrorInfo errorInfo = new ErrorInfo(result.errorCount(), result.errorSamples());

        return new AnalysisResultResponse(
                result.analysisId(),
                result.analyzedAt(),
                total,
                result.totalLines(),
                statusGroupRatios,
                topPaths,
                topStatusCodes,
                topIps,
                errorInfo
        );
    }

    private static Map<String, Double> buildStatusGroupRatios(Map<String, Long> statusGroupCounts, int total) {
        Map<String, Double> ratios = new LinkedHashMap<>();
        for (String group : List.of("2xx", "3xx", "4xx", "5xx")) {
            long count = statusGroupCounts.getOrDefault(group, 0L);
            ratios.put(group, percentage(count, total));
        }
        return ratios;
    }

    private static double percentage(long count, int total) {
        if (total == 0) return 0.0;
        return BigDecimal.valueOf(count * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
