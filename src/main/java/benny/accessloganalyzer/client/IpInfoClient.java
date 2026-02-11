package benny.accessloganalyzer.client;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class IpInfoClient {

    private final RestClient restClient;
    private final Cache<String, IpInfo> cache;
    private final String token;
    private final int maxRetries;

    public IpInfoClient(
            @Qualifier("ipInfoRestClient") RestClient restClient,
            @Qualifier("ipInfoCache") Cache<String, IpInfo> cache,
            @Value("${ipinfo.token:}") String token,
            @Value("${ipinfo.retry.max-attempts:2}") int maxRetries) {
        this.restClient = restClient;
        this.cache = cache;
        this.token = token;
        this.maxRetries = maxRetries;
    }

    public IpInfo lookup(String ip) {
        IpInfo cached = cache.getIfPresent(ip);
        if (cached != null) {
            log.debug("캐시 히트: {}", ip);
            return cached;
        }

        int attempts = 0;
        int totalAttempts = 1 + maxRetries;

        while (attempts < totalAttempts) {
            try {
                IpInfo info = restClient.get()
                        .uri("/{ip}?token={token}", ip, token)
                        .retrieve()
                        .body(IpInfo.class);

                if (info != null) {
                    cache.put(ip, info);
                    return info;
                }
            } catch (Exception e) {
                attempts++;
                if (attempts < totalAttempts) {
                    log.warn("ipinfo API 호출 실패 (시도 {}/{}): {} - {}", attempts, totalAttempts, ip, e.getMessage());
                } else {
                    log.error("ipinfo API 최대 재시도 초과: {} - {}", ip, e.getMessage());
                }
                continue;
            }
            break;
        }

        return IpInfo.unknown();
    }

    public Map<String, IpInfo> lookupTopIps(Map<String, Long> ipCounts, int topN) {
        Map<String, IpInfo> result = new LinkedHashMap<>();

        ipCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .forEach(entry -> result.put(entry.getKey(), lookup(entry.getKey())));

        return result;
    }
}
