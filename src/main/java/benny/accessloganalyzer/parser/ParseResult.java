package benny.accessloganalyzer.parser;

import java.util.List;

public record ParseResult(
        int successCount,
        int totalLines,
        int errorCount,
        List<String> errorSamples
) {
}
