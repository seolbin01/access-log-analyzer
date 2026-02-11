package benny.accessloganalyzer.parser;

import java.util.ArrayList;
import java.util.List;

public class CsvLineParser {

    public String[] parse(String line) {
        if (line.isEmpty()) {
            return new String[]{""};
        }

        // BOM 제거
        if (line.charAt(0) == '\uFEFF') {
            line = line.substring(1);
        }

        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        int len = line.length();

        while (i <= len) {
            if (i == len) {
                // 라인 끝: 현재 필드 추가
                fields.add(current.toString());
                break;
            }

            char ch = line.charAt(i);

            if (current.isEmpty() && ch == '"') {
                // QUOTED 모드 시작
                i++;
                while (i < len) {
                    char qc = line.charAt(i);
                    if (qc == '"') {
                        if (i + 1 < len && line.charAt(i + 1) == '"') {
                            // 이스케이프된 따옴표
                            current.append('"');
                            i += 2;
                        } else {
                            // 닫는 따옴표
                            i++;
                            break;
                        }
                    } else {
                        current.append(qc);
                        i++;
                    }
                }
                // 따옴표 닫힌 후 콤마 또는 라인 끝
                if (i < len && line.charAt(i) == ',') {
                    fields.add(current.toString());
                    current = new StringBuilder();
                    i++;
                }
            } else if (ch == ',') {
                // UNQUOTED 필드 끝
                fields.add(current.toString());
                current = new StringBuilder();
                i++;
            } else {
                // UNQUOTED 필드 내용
                current.append(ch);
                i++;
            }
        }

        return fields.toArray(String[]::new);
    }
}
