package benny.accessloganalyzer.global.config;

import benny.accessloganalyzer.client.IpInfo;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class IpInfoConfigTest {

    private final IpInfoConfig config = new IpInfoConfig();

    @DisplayName("ipInfoRestClient 빈은 RestClient 인스턴스를 반환한다")
    @Test
    void createsRestClient() {
        RestClient restClient = config.ipInfoRestClient("https://ipinfo.io", 5000);

        assertThat(restClient).isNotNull();
    }

    @DisplayName("ipInfoCache 빈은 Caffeine Cache 인스턴스를 반환한다")
    @Test
    void createsCaffeineCache() {
        Cache<String, IpInfo> cache = config.ipInfoCache(10000, 3600);

        assertThat(cache).isNotNull();
        cache.put("test", new IpInfo("KR", "Seoul", "Seoul", "KT"));
        assertThat(cache.getIfPresent("test")).isNotNull();
    }
}
