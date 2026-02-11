package benny.accessloganalyzer.controller;

import benny.accessloganalyzer.dto.AnalysisResponse;
import benny.accessloganalyzer.dto.AnalysisResultResponse;
import benny.accessloganalyzer.global.exception.BusinessException;
import benny.accessloganalyzer.global.exception.ErrorResponse;
import benny.accessloganalyzer.model.AnalysisResult;
import benny.accessloganalyzer.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "Analysis", description = "로그 분석 API")
@RestController
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(summary = "로그 파일 업로드 및 분석", description = "CSV 형식의 접속 로그 파일을 업로드하여 분석을 실행합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "분석 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 파일 형식 또는 빈 파일",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/analysis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw BusinessException.invalidLogFile("업로드된 파일이 비어 있습니다");
        }

        try {
            AnalysisResult result = analysisService.analyze(file.getInputStream());
            return new AnalysisResponse(result.analysisId());
        } catch (IOException e) {
            throw BusinessException.invalidLogFile("파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    @Operation(summary = "분석 결과 조회", description = "분석 ID로 분석 결과를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "분석 결과를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/analysis/{analysisId}")
    public AnalysisResultResponse getAnalysisResult(
            @PathVariable String analysisId,
            @RequestParam(defaultValue = "10") int top) {
        AnalysisResult result = analysisService.getResult(analysisId);
        return AnalysisResultResponse.from(result, top);
    }
}
