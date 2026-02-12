package benny.accessloganalyzer.controller;

import benny.accessloganalyzer.client.IpInfo;
import benny.accessloganalyzer.client.IpInfoClient;
import benny.accessloganalyzer.global.exception.BusinessException;
import benny.accessloganalyzer.global.exception.GlobalExceptionHandler;
import benny.accessloganalyzer.model.AnalysisEntry;
import benny.accessloganalyzer.model.AnalysisResult;
import benny.accessloganalyzer.model.AnalysisStatus;
import benny.accessloganalyzer.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisController.class)
@Import(GlobalExceptionHandler.class)
class AnalysisControllerTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2024, 1, 1, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @MockitoBean
    private IpInfoClient ipInfoClient;

    private AnalysisResult sampleResult() {
        return new AnalysisResult(
                "test-uuid-1234",
                FIXED_TIME,
                100,
                Map.of("200", 90L, "404", 10L),
                Map.of("2xx", 90L, "4xx", 10L),
                Map.of("/api/test", 100L),
                Map.of("1.1.1.1", 100L),
                100,
                0,
                List.of()
        );
    }

    private AnalysisEntry createEntryWithStatus(String id, AnalysisStatus status) {
        AnalysisEntry entry = new AnalysisEntry(id);
        if (status == AnalysisStatus.IN_PROGRESS || status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED) {
            entry.startProcessing();
        }
        if (status == AnalysisStatus.COMPLETED) {
            entry.complete(sampleResult());
        }
        if (status == AnalysisStatus.FAILED) {
            entry.fail("분석 실패 메시지");
        }
        return entry;
    }

    // --- POST /analysis ---

    @Nested
    @DisplayName("POST /analysis")
    class PostAnalysisTest {

        @DisplayName("정상 CSV 파일 업로드 시 202와 QUEUED 상태를 반환한다")
        @Test
        void uploadReturns202WithQueued() throws Exception {
            AnalysisEntry queuedEntry = new AnalysisEntry("test-uuid-1234");
            given(analysisService.submitAnalysis(any(Path.class))).willReturn("test-uuid-1234");
            given(analysisService.getEntry("test-uuid-1234")).willReturn(queuedEntry);
            given(analysisService.getQueuePosition(queuedEntry)).willReturn(1);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.csv", "text/csv", "header\ndata".getBytes());

            mockMvc.perform(multipart("/analysis").file(file))
                    .andExpect(status().isAccepted())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.analysisId").value("test-uuid-1234"))
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.queuePosition").value(1));
        }

        @DisplayName("빈 파일 업로드 시 400과 INVALID_LOG_FILE 코드를 반환한다")
        @Test
        void rejectsEmptyFile() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.csv", "text/csv", new byte[0]);

            mockMvc.perform(multipart("/analysis").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_LOG_FILE"));
        }

        @DisplayName("큐가 가득 찼을 때 503을 반환한다")
        @Test
        void returns503WhenQueueFull() throws Exception {
            given(analysisService.submitAnalysis(any(Path.class)))
                    .willThrow(BusinessException.analysisQueueFull("분석 큐가 가득 찼습니다"));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.csv", "text/csv", "header\ndata".getBytes());

            mockMvc.perform(multipart("/analysis").file(file))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ANALYSIS_QUEUE_FULL"));
        }
    }

    // --- GET /analysis/{analysisId} ---

    @Nested
    @DisplayName("GET /analysis/{analysisId}")
    class GetAnalysisTest {

        @DisplayName("QUEUED 상태이면 상태와 큐 위치를 반환한다")
        @Test
        void returnsQueuedWithPosition() throws Exception {
            AnalysisEntry entry = createEntryWithStatus("test-uuid-1234", AnalysisStatus.QUEUED);
            given(analysisService.getEntry("test-uuid-1234")).willReturn(entry);
            given(analysisService.getQueuePosition(entry)).willReturn(2);

            mockMvc.perform(get("/analysis/test-uuid-1234"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analysisId").value("test-uuid-1234"))
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.queuePosition").value(2));
        }

        @DisplayName("IN_PROGRESS 상태이면 상태만 반환한다")
        @Test
        void returnsInProgress() throws Exception {
            AnalysisEntry entry = createEntryWithStatus("test-uuid-1234", AnalysisStatus.IN_PROGRESS);
            given(analysisService.getEntry("test-uuid-1234")).willReturn(entry);

            mockMvc.perform(get("/analysis/test-uuid-1234"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analysisId").value("test-uuid-1234"))
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.queuePosition").doesNotExist());
        }

        @DisplayName("COMPLETED 상태이면 전체 분석 결과를 반환한다")
        @Test
        void returnsCompletedWithResult() throws Exception {
            AnalysisEntry entry = createEntryWithStatus("test-uuid-1234", AnalysisStatus.COMPLETED);
            given(analysisService.getEntry("test-uuid-1234")).willReturn(entry);
            given(ipInfoClient.lookupTopIps(anyMap(), anyInt())).willReturn(Map.of());

            mockMvc.perform(get("/analysis/test-uuid-1234"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analysisId").value("test-uuid-1234"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalRequests").value(100))
                    .andExpect(jsonPath("$.totalLines").value(100))
                    .andExpect(jsonPath("$.statusGroupRatios.2xx").value(90.0))
                    .andExpect(jsonPath("$.statusGroupRatios.4xx").value(10.0))
                    .andExpect(jsonPath("$.topPaths").isArray())
                    .andExpect(jsonPath("$.topStatusCodes").isArray())
                    .andExpect(jsonPath("$.topIps").isArray())
                    .andExpect(jsonPath("$.errorInfo.errorCount").value(0));
        }

        @DisplayName("FAILED 상태이면 에러 메시지를 반환한다")
        @Test
        void returnsFailedWithMessage() throws Exception {
            AnalysisEntry entry = createEntryWithStatus("test-uuid-1234", AnalysisStatus.FAILED);
            given(analysisService.getEntry("test-uuid-1234")).willReturn(entry);

            mockMvc.perform(get("/analysis/test-uuid-1234"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analysisId").value("test-uuid-1234"))
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.errorMessage").value("분석 실패 메시지"));
        }

        @DisplayName("존재하지 않는 analysisId로 조회하면 404를 반환한다")
        @Test
        void returnsNotFound() throws Exception {
            given(analysisService.getEntry("nonexistent"))
                    .willThrow(BusinessException.analysisNotFound("분석 결과를 찾을 수 없습니다: nonexistent"));

            mockMvc.perform(get("/analysis/nonexistent"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
        }

        @DisplayName("COMPLETED 상태에서 top 파라미터를 지정하면 해당 개수만큼 반환한다")
        @Test
        void completedWithCustomTop() throws Exception {
            AnalysisResult result = new AnalysisResult(
                    "test-uuid-top", FIXED_TIME, 100,
                    Map.of("200", 60L, "404", 30L, "500", 10L),
                    Map.of("2xx", 60L, "4xx", 30L, "5xx", 10L),
                    Map.of("/a", 50L, "/b", 30L, "/c", 20L),
                    Map.of("1.1.1.1", 60L, "2.2.2.2", 40L),
                    100, 0, List.of()
            );
            AnalysisEntry entry = new AnalysisEntry("test-uuid-top");
            entry.startProcessing();
            entry.complete(result);
            given(analysisService.getEntry("test-uuid-top")).willReturn(entry);
            given(ipInfoClient.lookupTopIps(anyMap(), anyInt())).willReturn(Map.of());

            mockMvc.perform(get("/analysis/test-uuid-top").param("top", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topPaths.length()").value(2))
                    .andExpect(jsonPath("$.topStatusCodes.length()").value(2))
                    .andExpect(jsonPath("$.topIps.length()").value(2));
        }

        @DisplayName("COMPLETED 상태에서 topIps에 IP 지리 정보가 포함된다")
        @Test
        void completedIncludesIpGeoInfo() throws Exception {
            AnalysisEntry entry = createEntryWithStatus("test-uuid-1234", AnalysisStatus.COMPLETED);
            given(analysisService.getEntry("test-uuid-1234")).willReturn(entry);
            given(ipInfoClient.lookupTopIps(anyMap(), anyInt())).willReturn(
                    Map.of("1.1.1.1", new IpInfo("AU", "New South Wales", "Sydney", "Cloudflare"))
            );

            mockMvc.perform(get("/analysis/test-uuid-1234"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topIps[0].ip").value("1.1.1.1"))
                    .andExpect(jsonPath("$.topIps[0].country").value("AU"))
                    .andExpect(jsonPath("$.topIps[0].region").value("New South Wales"))
                    .andExpect(jsonPath("$.topIps[0].city").value("Sydney"))
                    .andExpect(jsonPath("$.topIps[0].org").value("Cloudflare"));
        }
    }
}
