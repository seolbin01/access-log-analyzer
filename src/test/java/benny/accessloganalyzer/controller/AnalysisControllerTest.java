package benny.accessloganalyzer.controller;

import benny.accessloganalyzer.global.exception.BusinessException;
import benny.accessloganalyzer.global.exception.GlobalExceptionHandler;
import benny.accessloganalyzer.model.AnalysisResult;
import benny.accessloganalyzer.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
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

    @DisplayName("정상 CSV 파일 업로드 시 200과 analysisId를 반환한다")
    @Test
    void uploadSuccess() throws Exception {
        given(analysisService.analyze(any(InputStream.class))).willReturn(sampleResult());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "header\ndata".getBytes());

        mockMvc.perform(multipart("/analysis").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.analysisId").value("test-uuid-1234"));
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

    @DisplayName("서비스에서 BusinessException 발생 시 해당 에러 응답을 반환한다")
    @Test
    void propagatesServiceException() throws Exception {
        given(analysisService.analyze(any(InputStream.class)))
                .willThrow(BusinessException.invalidLogFile("유효한 로그 데이터가 없습니다"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.csv", "text/csv", "bad data".getBytes());

        mockMvc.perform(multipart("/analysis").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOG_FILE"))
                .andExpect(jsonPath("$.message").value("유효한 로그 데이터가 없습니다"));
    }

    // --- GET /analysis/{analysisId} ---

    @DisplayName("존재하는 analysisId로 조회하면 200과 분석 결과를 반환한다")
    @Test
    void getAnalysisResultSuccess() throws Exception {
        given(analysisService.getResult("test-uuid-1234")).willReturn(sampleResult());

        mockMvc.perform(get("/analysis/test-uuid-1234"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.analysisId").value("test-uuid-1234"))
                .andExpect(jsonPath("$.totalRequests").value(100))
                .andExpect(jsonPath("$.totalLines").value(100))
                .andExpect(jsonPath("$.statusGroupRatios.2xx").value(90.0))
                .andExpect(jsonPath("$.statusGroupRatios.4xx").value(10.0))
                .andExpect(jsonPath("$.topPaths").isArray())
                .andExpect(jsonPath("$.topStatusCodes").isArray())
                .andExpect(jsonPath("$.topIps").isArray())
                .andExpect(jsonPath("$.errorInfo.errorCount").value(0));
    }

    @DisplayName("존재하지 않는 analysisId로 조회하면 404와 ANALYSIS_NOT_FOUND를 반환한다")
    @Test
    void getAnalysisResultNotFound() throws Exception {
        given(analysisService.getResult("nonexistent"))
                .willThrow(BusinessException.analysisNotFound("분석 결과를 찾을 수 없습니다: nonexistent"));

        mockMvc.perform(get("/analysis/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
    }

    @DisplayName("top 파라미터를 지정하면 해당 개수만큼 반환한다")
    @Test
    void getAnalysisResultWithCustomTop() throws Exception {
        AnalysisResult result = new AnalysisResult(
                "test-uuid-top", FIXED_TIME, 100,
                Map.of("200", 60L, "404", 30L, "500", 10L),
                Map.of("2xx", 60L, "4xx", 30L, "5xx", 10L),
                Map.of("/a", 50L, "/b", 30L, "/c", 20L),
                Map.of("1.1.1.1", 60L, "2.2.2.2", 40L),
                100, 0, List.of()
        );
        given(analysisService.getResult("test-uuid-top")).willReturn(result);

        mockMvc.perform(get("/analysis/test-uuid-top").param("top", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topPaths.length()").value(2))
                .andExpect(jsonPath("$.topStatusCodes.length()").value(2))
                .andExpect(jsonPath("$.topIps.length()").value(2));
    }

    @DisplayName("top 파라미터 미지정 시 기본값 10이 적용된다")
    @Test
    void getAnalysisResultDefaultTop() throws Exception {
        given(analysisService.getResult("test-uuid-1234")).willReturn(sampleResult());

        mockMvc.perform(get("/analysis/test-uuid-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topPaths.length()").value(1))
                .andExpect(jsonPath("$.topStatusCodes.length()").value(2))
                .andExpect(jsonPath("$.topIps.length()").value(1));
    }
}
