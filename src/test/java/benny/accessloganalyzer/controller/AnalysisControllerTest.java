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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisController.class)
@Import(GlobalExceptionHandler.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    private AnalysisResult sampleResult() {
        return new AnalysisResult(
                "test-uuid-1234",
                LocalDateTime.of(2024, 1, 1, 0, 0),
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
}
