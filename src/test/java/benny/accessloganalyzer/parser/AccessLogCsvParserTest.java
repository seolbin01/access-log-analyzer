package benny.accessloganalyzer.parser;

import benny.accessloganalyzer.model.AccessLogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogCsvParserTest {

    private static final String HEADER = "TimeGenerated [UTC],ClientIp,HttpMethod,RequestUri,UserAgent,HttpStatus,HttpVersion,ReceivedBytes,SentBytes,ClientResponseTime,SslProtocol,OriginalRequestUriWithArgs";

    private static final String VALID_LINE = "\"1/29/2026, 5:44:10.000 AM\",121.158.115.86,GET,/event/banner/mir2/popup,MyThreadedApp/1.0,200,HTTP/1.1,176,1138,0,TLSv1.2,/event/banner/mir2/popup";

    private final AccessLogCsvParser parser = new AccessLogCsvParser();

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("정상 파싱")
    class SuccessfulParsing {

        @Test
        @DisplayName("유효한 CSV를 파싱하여 엔트리 리스트를 반환한다")
        void parsesValidCsv() {
            String csv = HEADER + "\n" + VALID_LINE;

            List<AccessLogEntry> captured = new ArrayList<>();
            ParseResult result = parser.parse(toInputStream(csv), captured::add);

            assertThat(result.successCount()).isEqualTo(1);
            assertThat(captured).hasSize(1);
            assertThat(result.totalLines()).isEqualTo(1);
            assertThat(result.errorCount()).isZero();
        }

        @Test
        @DisplayName("12개 필드가 올바른 타입으로 매핑된다")
        void mapsFieldsCorrectly() {
            String csv = HEADER + "\n" + VALID_LINE;

            List<AccessLogEntry> captured = new ArrayList<>();
            parser.parse(toInputStream(csv), captured::add);
            AccessLogEntry entry = captured.getFirst();

            assertThat(entry.timestamp()).isEqualTo("1/29/2026, 5:44:10.000 AM");
            assertThat(entry.clientIp()).isEqualTo("121.158.115.86");
            assertThat(entry.httpMethod()).isEqualTo("GET");
            assertThat(entry.requestUri()).isEqualTo("/event/banner/mir2/popup");
            assertThat(entry.userAgent()).isEqualTo("MyThreadedApp/1.0");
            assertThat(entry.httpStatus()).isEqualTo(200);
            assertThat(entry.httpVersion()).isEqualTo("HTTP/1.1");
            assertThat(entry.receivedBytes()).isEqualTo(176L);
            assertThat(entry.sentBytes()).isEqualTo(1138L);
            assertThat(entry.clientResponseTime()).isEqualTo(0.0);
            assertThat(entry.sslProtocol()).isEqualTo("TLSv1.2");
            assertThat(entry.originalRequestUriWithArgs()).isEqualTo("/event/banner/mir2/popup");
        }

        @Test
        @DisplayName("따옴표로 감싸진 UserAgent 필드를 올바르게 파싱한다")
        void parsesQuotedUserAgent() {
            String line = "\"1/29/2026, 5:44:10.000 AM\",112.144.4.88,GET,/assets/test.css,\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\",200,HTTP/1.1,2594,2653,0,TLSv1.3,/assets/test.css";
            String csv = HEADER + "\n" + line;

            List<AccessLogEntry> captured = new ArrayList<>();
            parser.parse(toInputStream(csv), captured::add);
            AccessLogEntry entry = captured.getFirst();

            assertThat(entry.userAgent()).isEqualTo("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        }

        @Test
        @DisplayName("SslProtocol이 빈 값인 라인을 파싱한다")
        void parsesLineWithEmptySslProtocol() {
            String line = "\"1/29/2026, 5:44:19.000 AM\",58.238.247.166,GET,/launcher/launcher,\"Mozilla/5.0 (Windows NT 10.0; Win64; x64)\",301,HTTP/1.1,475,442,0,,/launcher/launcher";
            String csv = HEADER + "\n" + line;

            List<AccessLogEntry> captured = new ArrayList<>();
            parser.parse(toInputStream(csv), captured::add);
            AccessLogEntry entry = captured.getFirst();

            assertThat(entry.httpStatus()).isEqualTo(301);
            assertThat(entry.sslProtocol()).isEmpty();
        }

        @Test
        @DisplayName("여러 데이터 라인을 파싱한다")
        void parsesMultipleLines() {
            String csv = HEADER + "\n" + VALID_LINE + "\n" + VALID_LINE + "\n" + VALID_LINE;

            ParseResult result = parser.parse(toInputStream(csv), entry -> {});

            assertThat(result.successCount()).isEqualTo(3);
            assertThat(result.totalLines()).isEqualTo(3);
            assertThat(result.errorCount()).isZero();
        }

        @Test
        @DisplayName("BOM이 포함된 CSV를 파싱한다")
        void parsesCsvWithBom() {
            String csv = "\uFEFF" + HEADER + "\n" + VALID_LINE;

            ParseResult result = parser.parse(toInputStream(csv), entry -> {});

            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.errorCount()).isZero();
        }

        @Test
        @DisplayName("clientResponseTime 소수점 값을 파싱한다")
        void parsesDecimalResponseTime() {
            String line = "\"1/29/2026, 5:44:10.000 AM\",112.144.4.88,POST,/api/test,Agent/1.0,200,HTTP/1.1,3227,999,0.001,TLSv1.3,/api/test";
            String csv = HEADER + "\n" + line;

            List<AccessLogEntry> captured = new ArrayList<>();
            parser.parse(toInputStream(csv), captured::add);

            assertThat(captured.getFirst().clientResponseTime()).isEqualTo(0.001);
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("헤더만 있는 CSV는 빈 결과를 반환한다")
        void returnsEmptyForHeaderOnly() {
            ParseResult result = parser.parse(toInputStream(HEADER), entry -> {});

            assertThat(result.successCount()).isZero();
            assertThat(result.totalLines()).isZero();
            assertThat(result.errorCount()).isZero();
        }

        @Test
        @DisplayName("빈 InputStream은 빈 결과를 반환한다")
        void returnsEmptyForEmptyInput() {
            ParseResult result = parser.parse(toInputStream(""), entry -> {});

            assertThat(result.successCount()).isZero();
            assertThat(result.totalLines()).isZero();
            assertThat(result.errorCount()).isZero();
        }
    }

    @Nested
    @DisplayName("에러 처리")
    class ErrorHandling {

        @Test
        @DisplayName("필드 수가 12개가 아닌 라인은 스킵하고 에러 수집한다")
        void skipsLineWithWrongFieldCount() {
            String badLine = "a,b,c";
            String csv = HEADER + "\n" + badLine + "\n" + VALID_LINE;

            ParseResult result = parser.parse(toInputStream(csv), entry -> {});

            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.totalLines()).isEqualTo(2);
            assertThat(result.errorCount()).isEqualTo(1);
            assertThat(result.errorSamples()).containsExactly(badLine);
        }

        @Test
        @DisplayName("숫자 변환 실패 라인은 스킵하고 에러 수집한다")
        void skipsLineWithInvalidNumber() {
            String badLine = "\"1/29/2026, 5:44:10.000 AM\",121.158.115.86,GET,/test,Agent,notANumber,HTTP/1.1,176,1138,0,TLSv1.2,/test";
            String csv = HEADER + "\n" + badLine + "\n" + VALID_LINE;

            ParseResult result = parser.parse(toInputStream(csv), entry -> {});

            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.totalLines()).isEqualTo(2);
            assertThat(result.errorCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("빈 라인은 에러 카운트에 포함하지 않고 스킵한다")
        void skipsBlankLinesWithoutCounting() {
            String csv = HEADER + "\n\n" + VALID_LINE + "\n\n";

            ParseResult result = parser.parse(toInputStream(csv), entry -> {});

            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.totalLines()).isEqualTo(1);
            assertThat(result.errorCount()).isZero();
        }

        @Test
        @DisplayName("에러 샘플은 최대 10개까지만 수집한다")
        void limitsErrorSamplesToTen() {
            String badLines = IntStream.range(0, 15)
                    .mapToObj(i -> "bad,line," + i)
                    .collect(Collectors.joining("\n"));
            String csv = HEADER + "\n" + badLines;

            ParseResult result = parser.parse(toInputStream(csv), entry -> {});

            assertThat(result.errorCount()).isEqualTo(15);
            assertThat(result.errorSamples()).hasSize(10);
        }
    }
}
