package benny.accessloganalyzer.global.exception;

import benny.accessloganalyzer.client.IpInfoClient;
import benny.accessloganalyzer.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @MockitoBean
    private IpInfoClient ipInfoClient;

    @RestController
    static class TestController {

        @GetMapping("/test/analysis-not-found")
        void analysisNotFound() {
            throw BusinessException.analysisNotFound("분석 결과를 찾을 수 없습니다: test-id");
        }

        @GetMapping("/test/invalid-log-file")
        void invalidLogFile() {
            throw BusinessException.invalidLogFile("잘못된 로그 파일 형식입니다");
        }

        @GetMapping("/test/external-api-error")
        void externalApiError() {
            throw BusinessException.externalApiError("ipinfo API 호출 실패");
        }

        @GetMapping("/test/unexpected-error")
        void unexpectedError() {
            throw new RuntimeException("예상치 못한 오류");
        }

        @GetMapping("/test/max-upload-size")
        void maxUploadSize() {
            throw new MaxUploadSizeExceededException(50 * 1024 * 1024);
        }
    }

    @DisplayName("analysisNotFound 예외 발생 시 404와 ANALYSIS_NOT_FOUND 코드를 반환한다")
    @Test
    void handleAnalysisNotFound() throws Exception {
        mockMvc.perform(get("/test/analysis-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("분석 결과를 찾을 수 없습니다: test-id"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @DisplayName("invalidLogFile 예외 발생 시 400과 INVALID_LOG_FILE 코드를 반환한다")
    @Test
    void handleInvalidLogFile() throws Exception {
        mockMvc.perform(get("/test/invalid-log-file"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_LOG_FILE"))
                .andExpect(jsonPath("$.message").value("잘못된 로그 파일 형식입니다"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @DisplayName("externalApiError 예외 발생 시 502와 EXTERNAL_API_ERROR 코드를 반환한다")
    @Test
    void handleExternalApiError() throws Exception {
        mockMvc.perform(get("/test/external-api-error"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("ipinfo API 호출 실패"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @DisplayName("예상치 못한 RuntimeException 발생 시 500과 INTERNAL_SERVER_ERROR 코드를 반환한다")
    @Test
    void handleUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @DisplayName("MaxUploadSizeExceededException 발생 시 400과 FILE_SIZE_EXCEEDED 코드를 반환한다")
    @Test
    void handleMaxUploadSizeExceeded() throws Exception {
        mockMvc.perform(get("/test/max-upload-size"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("FILE_SIZE_EXCEEDED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @DisplayName("파일 파라미터 누락 시 400과 MISSING_FILE 코드를 반환한다")
    @Test
    void handleMissingFile() throws Exception {
        mockMvc.perform(multipart("/analysis"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MISSING_FILE"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @DisplayName("에러 응답에 status, code, message, timestamp 필드가 모두 포함된다")
    @Test
    void errorResponseContainsAllFields() throws Exception {
        mockMvc.perform(get("/test/analysis-not-found"))
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString());
    }
}
