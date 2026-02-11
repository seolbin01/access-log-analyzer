package benny.accessloganalyzer.service;

import benny.accessloganalyzer.global.exception.BusinessException;
import benny.accessloganalyzer.model.AnalysisResult;
import benny.accessloganalyzer.parser.AccessLogCsvParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisServiceTest {

    private AnalysisService analysisService;

    private static final String HEADER = "timestamp,clientIp,httpMethod,requestUri,userAgent,httpStatus,httpVersion,receivedBytes,sentBytes,clientResponseTime,sslProtocol,originalRequestUriWithArgs";

    @BeforeEach
    void setUp() {
        analysisService = new AnalysisService(new AccessLogCsvParser(), 200_000);
    }

    private InputStream toInputStream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String csvLine(String ip, String method, String path, int status) {
        return String.format("2024-01-01T00:00:00,%s,%s,%s,Mozilla/5.0,%d,HTTP/1.1,100,200,0.5,TLSv1.3,%s?q=1",
                ip, method, path, status, path);
    }

    @Nested
    @DisplayName("통계 집계")
    class AggregationTest {

        @DisplayName("총 요청 수를 올바르게 집계한다")
        @Test
        void countsTotal() {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/a", 200) + "\n"
                    + csvLine("2.2.2.2", "POST", "/b", 404) + "\n"
                    + csvLine("3.3.3.3", "GET", "/c", 500);

            AnalysisResult result = analysisService.analyze(toInputStream(csv));

            assertThat(result.totalRequests()).isEqualTo(3);
        }

        @DisplayName("개별 상태코드별 카운트를 집계한다")
        @Test
        void countsStatusCodes() {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/a", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/b", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/c", 404);

            AnalysisResult result = analysisService.analyze(toInputStream(csv));

            assertThat(result.statusCodeCounts()).containsEntry("200", 2L);
            assertThat(result.statusCodeCounts()).containsEntry("404", 1L);
        }

        @DisplayName("상태코드 그룹별(2xx/4xx/5xx) 카운트를 집계한다")
        @Test
        void countsStatusGroups() {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/a", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/b", 201) + "\n"
                    + csvLine("1.1.1.1", "GET", "/c", 404) + "\n"
                    + csvLine("1.1.1.1", "GET", "/d", 500);

            AnalysisResult result = analysisService.analyze(toInputStream(csv));

            assertThat(result.statusGroupCounts()).containsEntry("2xx", 2L);
            assertThat(result.statusGroupCounts()).containsEntry("4xx", 1L);
            assertThat(result.statusGroupCounts()).containsEntry("5xx", 1L);
        }

        @DisplayName("Path별 카운트를 집계한다")
        @Test
        void countsPath() {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/api/users", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/api/users", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/api/orders", 200);

            AnalysisResult result = analysisService.analyze(toInputStream(csv));

            assertThat(result.pathCounts()).containsEntry("/api/users", 2L);
            assertThat(result.pathCounts()).containsEntry("/api/orders", 1L);
        }

        @DisplayName("IP별 카운트를 집계한다")
        @Test
        void countsIp() {
            String csv = HEADER + "\n"
                    + csvLine("10.0.0.1", "GET", "/a", 200) + "\n"
                    + csvLine("10.0.0.1", "GET", "/b", 200) + "\n"
                    + csvLine("10.0.0.2", "GET", "/c", 200);

            AnalysisResult result = analysisService.analyze(toInputStream(csv));

            assertThat(result.ipCounts()).containsEntry("10.0.0.1", 2L);
            assertThat(result.ipCounts()).containsEntry("10.0.0.2", 1L);
        }

        @DisplayName("파싱 에러 정보(총 라인 수, 에러 수, 샘플)를 포함한다")
        @Test
        void includesErrorInfo() {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/a", 200) + "\n"
                    + "invalid,line,only,three,fields";

            AnalysisResult result = analysisService.analyze(toInputStream(csv));

            assertThat(result.totalRequests()).isEqualTo(1);
            assertThat(result.totalLines()).isEqualTo(2);
            assertThat(result.errorCount()).isEqualTo(1);
            assertThat(result.errorSamples()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("검증")
    class ValidationTest {

        @DisplayName("헤더만 있는 빈 CSV는 예외를 발생시킨다")
        @Test
        void rejectsEmptyData() {
            String csv = HEADER + "\n";

            assertThatThrownBy(() -> analysisService.analyze(toInputStream(csv)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("유효한 로그 데이터가 없습니다");
        }

        @DisplayName("헤더도 없는 빈 파일은 예외를 발생시킨다")
        @Test
        void rejectsCompletelyEmpty() {
            assertThatThrownBy(() -> analysisService.analyze(toInputStream("")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("유효한 로그 데이터가 없습니다");
        }

        @DisplayName("모든 라인이 파싱 에러인 경우 예외를 발생시킨다")
        @Test
        void rejectsAllErrors() {
            String csv = HEADER + "\n"
                    + "bad,line\n"
                    + "another,bad,line";

            assertThatThrownBy(() -> analysisService.analyze(toInputStream(csv)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("유효한 로그 데이터가 없습니다");
        }

        @DisplayName("라인 제한을 초과하면 예외를 발생시킨다")
        @Test
        void rejectsExceedingMaxLines() {
            AnalysisService smallLimitService = new AnalysisService(new AccessLogCsvParser(), 3);

            StringBuilder csv = new StringBuilder(HEADER + "\n");
            for (int i = 0; i < 4; i++) {
                csv.append(csvLine("1.1.1.1", "GET", "/a", 200)).append("\n");
            }

            assertThatThrownBy(() -> smallLimitService.analyze(toInputStream(csv.toString())))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("최대 라인 수");
        }
    }

    @Nested
    @DisplayName("저장 및 조회")
    class StorageTest {

        @DisplayName("분석 결과를 UUID로 저장하고 조회할 수 있다")
        @Test
        void storesAndRetrieves() {
            String csv = HEADER + "\n" + csvLine("1.1.1.1", "GET", "/a", 200);

            AnalysisResult result = analysisService.analyze(toInputStream(csv));

            assertThat(result.analysisId()).isNotNull();
            assertThat(analysisService.getResult(result.analysisId())).isEqualTo(result);
        }

        @DisplayName("존재하지 않는 ID로 조회하면 예외를 발생시킨다")
        @Test
        void throwsOnNotFound() {
            assertThatThrownBy(() -> analysisService.getResult("non-existent-id"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("분석 결과를 찾을 수 없습니다");
        }
    }
}
