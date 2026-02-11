package benny.accessloganalyzer.model;

import java.time.LocalDateTime;

public class AnalysisEntry {

    private final String analysisId;
    private final LocalDateTime submittedAt;
    private final long submittedOrder;
    private volatile AnalysisStatus status;
    private volatile AnalysisResult result;
    private volatile String errorMessage;

    public AnalysisEntry(String analysisId) {
        this(analysisId, 0);
    }

    public AnalysisEntry(String analysisId, long submittedOrder) {
        this.analysisId = analysisId;
        this.submittedAt = LocalDateTime.now();
        this.submittedOrder = submittedOrder;
        this.status = AnalysisStatus.QUEUED;
    }

    public void startProcessing() {
        this.status = AnalysisStatus.IN_PROGRESS;
    }

    public void complete(AnalysisResult result) {
        this.result = result;
        this.status = AnalysisStatus.COMPLETED;
    }

    public void fail(String message) {
        this.errorMessage = message;
        this.status = AnalysisStatus.FAILED;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public long getSubmittedOrder() {
        return submittedOrder;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public AnalysisResult getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
