package benny.accessloganalyzer.model;

public record AccessLogEntry(
        String timestamp,
        String clientIp,
        String httpMethod,
        String requestUri,
        String userAgent,
        int httpStatus,
        String httpVersion,
        long receivedBytes,
        long sentBytes,
        double clientResponseTime,
        String sslProtocol,
        String originalRequestUriWithArgs
) {
}
