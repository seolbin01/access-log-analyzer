package benny.accessloganalyzer.dto;

import benny.accessloganalyzer.model.AnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisResultResponseTest {

    private final LocalDateTime FIXED_TIME = LocalDateTime.of(2024, 1, 1, 12, 0, 0);

    private AnalysisResult createResult(
            int totalRequests,
            Map<String, Long> statusCodeCounts,
            Map<String, Long> statusGroupCounts,
            Map<String, Long> pathCounts,
            Map<String, Long> ipCounts,
            int totalLines,
            int errorCount,
            List<String> errorSamples
    ) {
        return new AnalysisResult(
                "test-id", FIXED_TIME, totalRequests,
                statusCodeCounts, statusGroupCounts, pathCounts, ipCounts,
                totalLines, errorCount, errorSamples
        );
    }

    @DisplayName("from()은 AnalysisResult의 기본 필드를 그대로 매핑한다")
    @Test
    void mapsBasicFields() {
        AnalysisResult result = createResult(
                1000, Map.of("200", 1000L), Map.of("2xx", 1000L),
                Map.of("/api", 1000L), Map.of("1.1.1.1", 1000L),
                1050, 50, List.of("error line 1")
        );

        AnalysisResultResponse response = AnalysisResultResponse.from(result, 10);

        assertThat(response.analysisId()).isEqualTo("test-id");
        assertThat(response.analyzedAt()).isEqualTo(FIXED_TIME);
        assertThat(response.totalRequests()).isEqualTo(1000);
        assertThat(response.totalLines()).isEqualTo(1050);
        assertThat(response.errorInfo().errorCount()).isEqualTo(50);
        assertThat(response.errorInfo().errorSamples()).containsExactly("error line 1");
    }

    @DisplayName("statusGroupRatios는 2xx~5xx 4개 그룹을 포함하며, 없는 그룹은 0.0이다")
    @Test
    void statusGroupRatiosIncludesAllGroups() {
        AnalysisResult result = createResult(
                1000, Map.of("200", 800L, "404", 200L),
                Map.of("2xx", 800L, "4xx", 200L),
                Map.of("/api", 1000L), Map.of("1.1.1.1", 1000L),
                1000, 0, List.of()
        );

        AnalysisResultResponse response = AnalysisResultResponse.from(result, 10);

        assertThat(response.statusGroupRatios())
                .containsEntry("2xx", 80.0)
                .containsEntry("3xx", 0.0)
                .containsEntry("4xx", 20.0)
                .containsEntry("5xx", 0.0);
    }

    @DisplayName("비율 계산은 소수점 1자리로 반올림된다")
    @Test
    void percentageRoundedToOneDecimal() {
        AnalysisResult result = createResult(
                3, Map.of("200", 1L, "404", 2L),
                Map.of("2xx", 1L, "4xx", 2L),
                Map.of("/a", 1L, "/b", 2L), Map.of("1.1.1.1", 3L),
                3, 0, List.of()
        );

        AnalysisResultResponse response = AnalysisResultResponse.from(result, 10);

        // 1/3 = 33.3333... → 33.3
        // 2/3 = 66.6666... → 66.7
        assertThat(response.statusGroupRatios().get("2xx")).isEqualTo(33.3);
        assertThat(response.statusGroupRatios().get("4xx")).isEqualTo(66.7);

        assertThat(response.topPaths().get(0).percentage()).isEqualTo(66.7);
        assertThat(response.topPaths().get(1).percentage()).isEqualTo(33.3);
    }

    @DisplayName("topPaths는 count 내림차순으로 정렬되고 topN개로 제한된다")
    @Test
    void topPathsSortedAndLimited() {
        AnalysisResult result = createResult(
                100,
                Map.of("200", 100L),
                Map.of("2xx", 100L),
                Map.of("/a", 10L, "/b", 50L, "/c", 30L, "/d", 5L, "/e", 5L),
                Map.of("1.1.1.1", 100L),
                100, 0, List.of()
        );

        AnalysisResultResponse response = AnalysisResultResponse.from(result, 3);

        assertThat(response.topPaths()).hasSize(3);
        assertThat(response.topPaths().get(0).path()).isEqualTo("/b");
        assertThat(response.topPaths().get(0).count()).isEqualTo(50);
        assertThat(response.topPaths().get(0).percentage()).isEqualTo(50.0);
        assertThat(response.topPaths().get(1).path()).isEqualTo("/c");
        assertThat(response.topPaths().get(2).path()).isEqualTo("/a");
    }

    @DisplayName("topStatusCodes는 count 내림차순으로 정렬된다")
    @Test
    void topStatusCodesSorted() {
        AnalysisResult result = createResult(
                100,
                Map.of("200", 60L, "404", 30L, "500", 10L),
                Map.of("2xx", 60L, "4xx", 30L, "5xx", 10L),
                Map.of("/api", 100L),
                Map.of("1.1.1.1", 100L),
                100, 0, List.of()
        );

        AnalysisResultResponse response = AnalysisResultResponse.from(result, 10);

        assertThat(response.topStatusCodes()).hasSize(3);
        assertThat(response.topStatusCodes().get(0).statusCode()).isEqualTo("200");
        assertThat(response.topStatusCodes().get(0).count()).isEqualTo(60);
        assertThat(response.topStatusCodes().get(0).percentage()).isEqualTo(60.0);
        assertThat(response.topStatusCodes().get(1).statusCode()).isEqualTo("404");
        assertThat(response.topStatusCodes().get(2).statusCode()).isEqualTo("500");
    }

    @DisplayName("topIps는 count 내림차순으로 정렬된다")
    @Test
    void topIpsSorted() {
        AnalysisResult result = createResult(
                100,
                Map.of("200", 100L),
                Map.of("2xx", 100L),
                Map.of("/api", 100L),
                Map.of("1.1.1.1", 70L, "2.2.2.2", 30L),
                100, 0, List.of()
        );

        AnalysisResultResponse response = AnalysisResultResponse.from(result, 10);

        assertThat(response.topIps()).hasSize(2);
        assertThat(response.topIps().get(0).ip()).isEqualTo("1.1.1.1");
        assertThat(response.topIps().get(0).count()).isEqualTo(70);
        assertThat(response.topIps().get(0).percentage()).isEqualTo(70.0);
        assertThat(response.topIps().get(1).ip()).isEqualTo("2.2.2.2");
    }

    @DisplayName("항목이 topN보다 적으면 전체를 반환한다")
    @Test
    void returnsAllWhenFewerThanTopN() {
        AnalysisResult result = createResult(
                50,
                Map.of("200", 50L),
                Map.of("2xx", 50L),
                Map.of("/only", 50L),
                Map.of("1.1.1.1", 50L),
                50, 0, List.of()
        );

        AnalysisResultResponse response = AnalysisResultResponse.from(result, 10);

        assertThat(response.topPaths()).hasSize(1);
        assertThat(response.topStatusCodes()).hasSize(1);
        assertThat(response.topIps()).hasSize(1);
    }
}
