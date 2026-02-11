package benny.accessloganalyzer.global.config;

import benny.accessloganalyzer.client.IpInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class IpInfoConfig {

    @Bean
    public RestClient ipInfoRestClient(
            @Value("${ipinfo.base-url:https://ipinfo.io}") String baseUrl,
            @Value("${ipinfo.timeout:5000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Bean
    public Cache<String, IpInfo> ipInfoCache(
            @Value("${ipinfo.cache.max-size:10000}") long maxSize,
            @Value("${ipinfo.cache.ttl-seconds:3600}") long ttlSeconds) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .build();
    }
}
