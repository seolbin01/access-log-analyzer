package benny.accessloganalyzer.controller;

import benny.accessloganalyzer.dto.AnalysisResponse;
import benny.accessloganalyzer.dto.AnalysisResultResponse;
import benny.accessloganalyzer.global.exception.BusinessException;
import benny.accessloganalyzer.model.AnalysisResult;
import benny.accessloganalyzer.service.AnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analysis")
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

    @GetMapping("/analysis/{analysisId}")
    public AnalysisResultResponse getAnalysisResult(
            @PathVariable String analysisId,
            @RequestParam(defaultValue = "10") int top) {
        AnalysisResult result = analysisService.getResult(analysisId);
        return AnalysisResultResponse.from(result, top);
    }
}
