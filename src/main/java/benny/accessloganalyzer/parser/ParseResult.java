package benny.accessloganalyzer.parser;

import benny.accessloganalyzer.model.AccessLogEntry;

import java.util.List;

public record ParseResult(
        List<AccessLogEntry> entries,
        int totalLines,
        int errorCount,
        List<String> errorSamples
) {
}
