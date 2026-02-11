package benny.accessloganalyzer.client;

public record IpInfo(
        String country,
        String region,
        String city,
        String org
) {

    public static IpInfo unknown() {
        return new IpInfo("UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN");
    }
}
