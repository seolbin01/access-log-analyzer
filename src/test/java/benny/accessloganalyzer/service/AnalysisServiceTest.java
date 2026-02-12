package benny.accessloganalyzer.service;

import benny.accessloganalyzer.global.exception.BusinessException;
import benny.accessloganalyzer.model.AnalysisEntry;
import benny.accessloganalyzer.model.AnalysisStatus;
import benny.accessloganalyzer.parser.AccessLogCsvParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisServiceTest {

    private AnalysisService analysisService;

    private static final String HEADER = "timestamp,clientIp,httpMethod,requestUri,userAgent,httpStatus,httpVersion,receivedBytes,sentBytes,clientResponseTime,sslProtocol,originalRequestUriWithArgs";

    /** 동기 Executor: 제출 즉시 실행 */
    private static final Executor SYNC_EXECUTOR = Runnable::run;

    @BeforeEach
    void setUp() {
        analysisService = new AnalysisService(new AccessLogCsvParser(), 200_000, SYNC_EXECUTOR);
    }

    private Path toTempFile(String csv) throws IOException {
        Path tempFile = Files.createTempFile("test-log-", ".csv");
        Files.writeString(tempFile, csv);
        return tempFile;
    }

    private String csvLine(String ip, String method, String path, int status) {
        return String.format("2024-01-01T00:00:00,%s,%s,%s,Mozilla/5.0,%d,HTTP/1.1,100,200,0.5,TLSv1.3,%s?q=1",
                ip, method, path, status, path);
    }

    @Nested
    @DisplayName("비동기 제출")
    class AsyncSubmissionTest {

        @DisplayName("submitAnalysis는 analysisId를 반환하고 동기 executor에서 즉시 COMPLETED된다")
        @Test
        void submitReturnsIdAndCompletesSync() throws Exception {
            String csv = HEADER + "\n" + csvLine("1.1.1.1", "GET", "/a", 200);

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));

            assertThat(analysisId).isNotNull();
            AnalysisEntry entry = analysisService.getEntry(analysisId);
            assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
            assertThat(entry.getResult()).isNotNull();
            assertThat(entry.getResult().totalRequests()).isEqualTo(1);
        }

        @DisplayName("잘못된 CSV 데이터 제출 시 FAILED 상태가 된다")
        @Test
        void submitWithInvalidDataResultsInFailed() throws Exception {
            String csv = HEADER + "\n" + "bad,line,only";

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));

            AnalysisEntry entry = analysisService.getEntry(analysisId);
            assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(entry.getErrorMessage()).isNotNull();
        }

        @DisplayName("executor가 거부하면 ANALYSIS_QUEUE_FULL 예외가 발생한다")
        @Test
        void rejectsWhenQueueFull() throws Exception {
            Executor rejectingExecutor = task -> {
                throw new RejectedExecutionException("queue full");
            };
            AnalysisService fullService = new AnalysisService(
                    new AccessLogCsvParser(), 200_000, rejectingExecutor);

            String csv = HEADER + "\n" + csvLine("1.1.1.1", "GET", "/a", 200);

            assertThatThrownBy(() -> fullService.submitAnalysis(toTempFile(csv)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("분석 큐가 가득 찼습니다");
        }

        @DisplayName("존재하지 않는 ID로 getEntry 호출 시 예외가 발생한다")
        @Test
        void getEntryThrowsOnNotFound() {
            assertThatThrownBy(() -> analysisService.getEntry("non-existent-id"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("분석 결과를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("큐 위치")
    class QueuePositionTest {

        @DisplayName("QUEUED 상태 entry의 큐 위치를 submittedAt 기준으로 계산한다")
        @Test
        void calculatesQueuePosition() throws Exception {
            // NOOP executor로 태스크를 실행하지 않아 QUEUED 상태 유지
            Executor noopExecutor = task -> {};
            AnalysisService noopService = new AnalysisService(
                    new AccessLogCsvParser(), 200_000, noopExecutor);

            String csv = HEADER + "\n" + csvLine("1.1.1.1", "GET", "/a", 200);
            String id1 = noopService.submitAnalysis(toTempFile(csv));
            String id2 = noopService.submitAnalysis(toTempFile(csv));
            String id3 = noopService.submitAnalysis(toTempFile(csv));

            AnalysisEntry entry1 = noopService.getEntry(id1);
            AnalysisEntry entry2 = noopService.getEntry(id2);
            AnalysisEntry entry3 = noopService.getEntry(id3);

            assertThat(noopService.getQueuePosition(entry1)).isEqualTo(1);
            assertThat(noopService.getQueuePosition(entry2)).isEqualTo(2);
            assertThat(noopService.getQueuePosition(entry3)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("통계 집계")
    class AggregationTest {

        @DisplayName("총 요청 수를 올바르게 집계한다")
        @Test
        void countsTotal() throws Exception {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/a", 200) + "\n"
                    + csvLine("2.2.2.2", "POST", "/b", 404) + "\n"
                    + csvLine("3.3.3.3", "GET", "/c", 500);

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));
            AnalysisEntry entry = analysisService.getEntry(analysisId);

            assertThat(entry.getResult().totalRequests()).isEqualTo(3);
        }

        @DisplayName("개별 상태코드별 카운트를 집계한다")
        @Test
        void countsStatusCodes() throws Exception {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/a", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/b", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/c", 404);

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));
            AnalysisEntry entry = analysisService.getEntry(analysisId);

            assertThat(entry.getResult().statusCodeCounts()).containsEntry("200", 2L);
            assertThat(entry.getResult().statusCodeCounts()).containsEntry("404", 1L);
        }

        @DisplayName("상태코드 그룹별(2xx/4xx/5xx) 카운트를 집계한다")
        @Test
        void countsStatusGroups() throws Exception {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/a", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/b", 201) + "\n"
                    + csvLine("1.1.1.1", "GET", "/c", 404) + "\n"
                    + csvLine("1.1.1.1", "GET", "/d", 500);

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));
            AnalysisEntry entry = analysisService.getEntry(analysisId);

            assertThat(entry.getResult().statusGroupCounts()).containsEntry("2xx", 2L);
            assertThat(entry.getResult().statusGroupCounts()).containsEntry("4xx", 1L);
            assertThat(entry.getResult().statusGroupCounts()).containsEntry("5xx", 1L);
        }

        @DisplayName("Path별 카운트를 집계한다")
        @Test
        void countsPath() throws Exception {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/api/users", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/api/users", 200) + "\n"
                    + csvLine("1.1.1.1", "GET", "/api/orders", 200);

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));
            AnalysisEntry entry = analysisService.getEntry(analysisId);

            assertThat(entry.getResult().pathCounts()).containsEntry("/api/users", 2L);
            assertThat(entry.getResult().pathCounts()).containsEntry("/api/orders", 1L);
        }

        @DisplayName("IP별 카운트를 집계한다")
        @Test
        void countsIp() throws Exception {
            String csv = HEADER + "\n"
                    + csvLine("10.0.0.1", "GET", "/a", 200) + "\n"
                    + csvLine("10.0.0.1", "GET", "/b", 200) + "\n"
                    + csvLine("10.0.0.2", "GET", "/c", 200);

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));
            AnalysisEntry entry = analysisService.getEntry(analysisId);

            assertThat(entry.getResult().ipCounts()).containsEntry("10.0.0.1", 2L);
            assertThat(entry.getResult().ipCounts()).containsEntry("10.0.0.2", 1L);
        }

        @DisplayName("파싱 에러 정보(총 라인 수, 에러 수, 샘플)를 포함한다")
        @Test
        void includesErrorInfo() throws Exception {
            String csv = HEADER + "\n"
                    + csvLine("1.1.1.1", "GET", "/a", 200) + "\n"
                    + "invalid,line,only,three,fields";

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));
            AnalysisEntry entry = analysisService.getEntry(analysisId);

            assertThat(entry.getResult().totalRequests()).isEqualTo(1);
            assertThat(entry.getResult().totalLines()).isEqualTo(2);
            assertThat(entry.getResult().errorCount()).isEqualTo(1);
            assertThat(entry.getResult().errorSamples()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("검증")
    class ValidationTest {

        @DisplayName("헤더만 있는 빈 CSV는 FAILED 상태가 된다")
        @Test
        void rejectsEmptyData() throws Exception {
            String csv = HEADER + "\n";

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));
            AnalysisEntry entry = analysisService.getEntry(analysisId);

            assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(entry.getErrorMessage()).contains("유효한 로그 데이터가 없습니다");
        }

        @DisplayName("헤더도 없는 빈 파일은 FAILED 상태가 된다")
        @Test
        void rejectsCompletelyEmpty() throws Exception {
            String analysisId = analysisService.submitAnalysis(toTempFile(""));
            AnalysisEntry entry = analysisService.getEntry(analysisId);

            assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(entry.getErrorMessage()).contains("유효한 로그 데이터가 없습니다");
        }

        @DisplayName("모든 라인이 파싱 에러인 경우 FAILED 상태가 된다")
        @Test
        void rejectsAllErrors() throws Exception {
            String csv = HEADER + "\n"
                    + "bad,line\n"
                    + "another,bad,line";

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));
            AnalysisEntry entry = analysisService.getEntry(analysisId);

            assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(entry.getErrorMessage()).contains("유효한 로그 데이터가 없습니다");
        }

        @DisplayName("라인 제한을 초과하면 FAILED 상태가 된다")
        @Test
        void rejectsExceedingMaxLines() throws Exception {
            AnalysisService smallLimitService = new AnalysisService(
                    new AccessLogCsvParser(), 3, SYNC_EXECUTOR);

            StringBuilder csv = new StringBuilder(HEADER + "\n");
            for (int i = 0; i < 4; i++) {
                csv.append(csvLine("1.1.1.1", "GET", "/a", 200)).append("\n");
            }

            String analysisId = smallLimitService.submitAnalysis(toTempFile(csv.toString()));
            AnalysisEntry entry = smallLimitService.getEntry(analysisId);

            assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(entry.getErrorMessage()).contains("최대 라인 수");
        }
    }

    @Nested
    @DisplayName("저장 및 조회")
    class StorageTest {

        @DisplayName("분석 결과를 UUID로 저장하고 조회할 수 있다")
        @Test
        void storesAndRetrieves() throws Exception {
            String csv = HEADER + "\n" + csvLine("1.1.1.1", "GET", "/a", 200);

            String analysisId = analysisService.submitAnalysis(toTempFile(csv));

            assertThat(analysisId).isNotNull();
            AnalysisEntry entry = analysisService.getEntry(analysisId);
            assertThat(entry.getResult()).isNotNull();
            assertThat(entry.getResult().analysisId()).isEqualTo(analysisId);
        }

        @DisplayName("존재하지 않는 ID로 조회하면 예외를 발생시킨다")
        @Test
        void throwsOnNotFound() {
            assertThatThrownBy(() -> analysisService.getEntry("non-existent-id"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("분석 결과를 찾을 수 없습니다");
        }
    }
}
