package benny.accessloganalyzer.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvLineParserTest {

    private final CsvLineParser parser = new CsvLineParser();

    @Nested
    @DisplayName("일반 필드 파싱")
    class UnquotedFields {

        @Test
        @DisplayName("콤마로 구분된 일반 필드를 파싱한다")
        void parsesSimpleCommaSeparatedFields() {
            String line = "a,b,c,d";

            String[] fields = parser.parse(line);

            assertThat(fields).containsExactly("a", "b", "c", "d");
        }

        @Test
        @DisplayName("빈 필드를 빈 문자열로 파싱한다")
        void parsesEmptyFields() {
            String line = "a,,c,";

            String[] fields = parser.parse(line);

            assertThat(fields).containsExactly("a", "", "c", "");
        }

        @Test
        @DisplayName("필드 앞뒤 공백을 유지한다")
        void preservesWhitespace() {
            String line = " a , b ,c";

            String[] fields = parser.parse(line);

            assertThat(fields).containsExactly(" a ", " b ", "c");
        }
    }

    @Nested
    @DisplayName("따옴표 필드 파싱")
    class QuotedFields {

        @Test
        @DisplayName("따옴표로 감싸진 필드 내부 콤마를 보존한다")
        void preservesCommaInsideQuotes() {
            String line = "\"1/29/2026, 5:44:10.000 AM\",121.158.115.86,GET";

            String[] fields = parser.parse(line);

            assertThat(fields).containsExactly("1/29/2026, 5:44:10.000 AM", "121.158.115.86", "GET");
        }

        @Test
        @DisplayName("따옴표 내부의 이스케이프된 따옴표를 처리한다")
        void handlesEscapedQuotes() {
            String line = "\"He said \"\"hello\"\"\",b";

            String[] fields = parser.parse(line);

            assertThat(fields).containsExactly("He said \"hello\"", "b");
        }

        @Test
        @DisplayName("빈 따옴표 필드를 빈 문자열로 파싱한다")
        void parsesEmptyQuotedField() {
            String line = "\"\",b,c";

            String[] fields = parser.parse(line);

            assertThat(fields).containsExactly("", "b", "c");
        }
    }

    @Nested
    @DisplayName("BOM 처리")
    class BomHandling {

        @Test
        @DisplayName("BOM 문자를 제거한다")
        void removesBom() {
            String line = "\uFEFFa,b,c";

            String[] fields = parser.parse(line);

            assertThat(fields).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("BOM이 없는 라인도 정상 파싱한다")
        void parsesWithoutBom() {
            String line = "a,b,c";

            String[] fields = parser.parse(line);

            assertThat(fields).containsExactly("a", "b", "c");
        }
    }

    @Nested
    @DisplayName("실제 CSV 라인 파싱")
    class RealCsvLines {

        @Test
        @DisplayName("실제 로그 라인을 12개 필드로 파싱한다")
        void parsesRealLogLine() {
            String line = "\"1/29/2026, 5:44:10.000 AM\",121.158.115.86,GET,/event/banner/mir2/popup,MyThreadedApp/1.0,200,HTTP/1.1,176,1138,0,TLSv1.2,/event/banner/mir2/popup";

            String[] fields = parser.parse(line);

            assertThat(fields).hasSize(12);
            assertThat(fields[0]).isEqualTo("1/29/2026, 5:44:10.000 AM");
            assertThat(fields[1]).isEqualTo("121.158.115.86");
            assertThat(fields[4]).isEqualTo("MyThreadedApp/1.0");
            assertThat(fields[10]).isEqualTo("TLSv1.2");
        }

        @Test
        @DisplayName("UserAgent에 콤마가 포함된 라인을 파싱한다")
        void parsesLineWithQuotedUserAgent() {
            String line = "\"1/29/2026, 5:44:10.000 AM\",112.144.4.88,GET,/assets/Dormancy-D5QIuaVf.css,\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0\",200,HTTP/1.1,2594,2653,0,TLSv1.3,/assets/Dormancy-D5QIuaVf.css";

            String[] fields = parser.parse(line);

            assertThat(fields).hasSize(12);
            assertThat(fields[0]).isEqualTo("1/29/2026, 5:44:10.000 AM");
            assertThat(fields[4]).startsWith("Mozilla/5.0");
            assertThat(fields[4]).contains("Win64; x64");
        }

        @Test
        @DisplayName("SslProtocol이 빈 값인 301 리다이렉트 라인을 파싱한다")
        void parsesLineWithEmptySslProtocol() {
            String line = "\"1/29/2026, 5:44:19.000 AM\",58.238.247.166,GET,/launcher/launcher,\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0\",301,HTTP/1.1,475,442,0,,/launcher/launcher";

            String[] fields = parser.parse(line);

            assertThat(fields).hasSize(12);
            assertThat(fields[5]).isEqualTo("301");
            assertThat(fields[10]).isEqualTo("");
        }
    }
}
