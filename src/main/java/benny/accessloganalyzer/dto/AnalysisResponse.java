package benny.accessloganalyzer.dto;

import benny.accessloganalyzer.model.AnalysisStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisResponse(
        String analysisId,
        AnalysisStatus status,
        Integer queuePosition,
        String errorMessage
) {

    public static AnalysisResponse queued(String analysisId, int queuePosition) {
        return new AnalysisResponse(analysisId, AnalysisStatus.QUEUED, queuePosition, null);
    }

    public static AnalysisResponse inProgress(String analysisId) {
        return new AnalysisResponse(analysisId, AnalysisStatus.IN_PROGRESS, null, null);
    }

    public static AnalysisResponse failed(String analysisId, String errorMessage) {
        return new AnalysisResponse(analysisId, AnalysisStatus.FAILED, null, errorMessage);
    }
}
