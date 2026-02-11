package benny.accessloganalyzer.parser;

import benny.accessloganalyzer.model.AccessLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AccessLogCsvParser {

    private static final int EXPECTED_FIELD_COUNT = 12;
    private static final int MAX_ERROR_SAMPLES = 10;

    private final CsvLineParser csvLineParser = new CsvLineParser();

    public ParseResult parse(InputStream inputStream) {
        List<AccessLogEntry> entries = new ArrayList<>();
        int totalLines = 0;
        int errorCount = 0;
        List<String> errorSamples = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            // 첫 줄(헤더) 스킵
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new ParseResult(entries, 0, 0, errorSamples);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                // 빈 라인 스킵 (에러 카운트에 포함하지 않음)
                if (line.isBlank()) {
                    continue;
                }

                totalLines++;

                try {
                    AccessLogEntry entry = parseLine(line);
                    entries.add(entry);
                } catch (Exception e) {
                    errorCount++;
                    if (errorSamples.size() < MAX_ERROR_SAMPLES) {
                        errorSamples.add(line);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV 파일 읽기 실패", e);
        }

        log.info("CSV 파싱 완료: totalLines={}, successCount={}, errorCount={}", totalLines, entries.size(), errorCount);
        if (errorCount > 0) {
            log.warn("파싱 오류 발생: errorCount={}, samples={}", errorCount, errorSamples);
        }

        return new ParseResult(entries, totalLines, errorCount, errorSamples);
    }

    private AccessLogEntry parseLine(String line) {
        String[] fields = csvLineParser.parse(line);

        if (fields.length != EXPECTED_FIELD_COUNT) {
            throw new IllegalArgumentException(
                    "필드 수 불일치: expected=" + EXPECTED_FIELD_COUNT + ", actual=" + fields.length);
        }

        return new AccessLogEntry(
                fields[0].trim(),
                fields[1].trim(),
                fields[2].trim(),
                fields[3].trim(),
                fields[4].trim(),
                Integer.parseInt(fields[5].trim()),
                fields[6].trim(),
                Long.parseLong(fields[7].trim()),
                Long.parseLong(fields[8].trim()),
                Double.parseDouble(fields[9].trim()),
                fields[10].trim(),
                fields[11].trim()
        );
    }
}
