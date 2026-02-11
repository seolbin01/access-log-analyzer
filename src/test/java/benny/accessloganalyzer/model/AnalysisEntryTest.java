package benny.accessloganalyzer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisEntryTest {

    @DisplayName("생성 직후 상태는 QUEUED이고 submittedAt이 기록된다")
    @Test
    void initialStateIsQueued() {
        LocalDateTime before = LocalDateTime.now();
        AnalysisEntry entry = new AnalysisEntry("test-id");
        LocalDateTime after = LocalDateTime.now();

        assertThat(entry.getAnalysisId()).isEqualTo("test-id");
        assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(entry.getSubmittedAt()).isBetween(before, after);
        assertThat(entry.getResult()).isNull();
        assertThat(entry.getErrorMessage()).isNull();
    }

    @DisplayName("startProcessing() 호출 시 QUEUED → IN_PROGRESS로 전이된다")
    @Test
    void startProcessingTransitionsToInProgress() {
        AnalysisEntry entry = new AnalysisEntry("test-id");

        entry.startProcessing();

        assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
    }

    @DisplayName("complete() 호출 시 COMPLETED로 전이되고 result가 저장된다")
    @Test
    void completeTransitionsToCompleted() {
        AnalysisEntry entry = new AnalysisEntry("test-id");
        entry.startProcessing();

        AnalysisResult result = new AnalysisResult(
                "test-id", LocalDateTime.now(), 100,
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                100, 0, java.util.List.of()
        );
        entry.complete(result);

        assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(entry.getResult()).isEqualTo(result);
    }

    @DisplayName("fail() 호출 시 FAILED로 전이되고 errorMessage가 저장된다")
    @Test
    void failTransitionsToFailed() {
        AnalysisEntry entry = new AnalysisEntry("test-id");
        entry.startProcessing();

        entry.fail("파싱 실패");

        assertThat(entry.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(entry.getErrorMessage()).isEqualTo("파싱 실패");
    }
}
