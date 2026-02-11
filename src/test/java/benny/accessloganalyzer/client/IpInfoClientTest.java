package benny.accessloganalyzer.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IpInfoClientTest {

    private MockRestServiceServer mockServer;
    private IpInfoClient client;
    private Cache<String, IpInfo> cache;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ipinfo.io");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        cache = Caffeine.newBuilder().maximumSize(100).build();
        client = new IpInfoClient(builder.build(), cache, "test-token", 2);
    }

    @DisplayName("정상 응답 시 IpInfo를 반환한다")
    @Test
    void lookupSuccess() {
        mockServer.expect(requestTo("https://ipinfo.io/1.1.1.1?token=test-token"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                            "ip": "1.1.1.1",
                            "country": "AU",
                            "region": "New South Wales",
                            "city": "Sydney",
                            "org": "AS13335 Cloudflare, Inc."
                        }
                        """, MediaType.APPLICATION_JSON));

        IpInfo result = client.lookup("1.1.1.1");

        assertThat(result.country()).isEqualTo("AU");
        assertThat(result.region()).isEqualTo("New South Wales");
        assertThat(result.city()).isEqualTo("Sydney");
        assertThat(result.org()).isEqualTo("AS13335 Cloudflare, Inc.");
        mockServer.verify();
    }

    @DisplayName("캐시에 있는 IP는 API를 호출하지 않는다")
    @Test
    void cacheHitSkipsApiCall() {
        IpInfo cached = new IpInfo("KR", "Seoul", "Seoul", "KT");
        cache.put("2.2.2.2", cached);

        IpInfo result = client.lookup("2.2.2.2");

        assertThat(result).isEqualTo(cached);
        // mockServer에 아무 기대값을 설정하지 않았으므로, API 호출 시 실패함
        mockServer.verify();
    }

    @DisplayName("첫 번째 실패 후 재시도하여 성공하면 결과를 반환한다")
    @Test
    void retryAfterFailure() {
        mockServer.expect(requestTo("https://ipinfo.io/3.3.3.3?token=test-token"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://ipinfo.io/3.3.3.3?token=test-token"))
                .andRespond(withSuccess("""
                        {
                            "country": "US",
                            "region": "California",
                            "city": "Los Angeles",
                            "org": "AS15169 Google LLC"
                        }
                        """, MediaType.APPLICATION_JSON));

        IpInfo result = client.lookup("3.3.3.3");

        assertThat(result.country()).isEqualTo("US");
        assertThat(result.region()).isEqualTo("California");
        mockServer.verify();
    }

    @DisplayName("최대 재시도 횟수 초과 시 UNKNOWN을 반환한다")
    @Test
    void returnsUnknownAfterMaxRetries() {
        // 초기 시도 + 재시도 2회 = 총 3번 실패
        mockServer.expect(requestTo("https://ipinfo.io/4.4.4.4?token=test-token"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://ipinfo.io/4.4.4.4?token=test-token"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://ipinfo.io/4.4.4.4?token=test-token"))
                .andRespond(withServerError());

        IpInfo result = client.lookup("4.4.4.4");

        assertThat(result).isEqualTo(IpInfo.unknown());
        mockServer.verify();
    }

    @DisplayName("성공한 결과는 캐시에 저장된다")
    @Test
    void successfulLookupIsCached() {
        mockServer.expect(requestTo("https://ipinfo.io/5.5.5.5?token=test-token"))
                .andRespond(withSuccess("""
                        {
                            "country": "JP",
                            "region": "Tokyo",
                            "city": "Tokyo",
                            "org": "AS2914 NTT"
                        }
                        """, MediaType.APPLICATION_JSON));

        client.lookup("5.5.5.5");

        IpInfo cached = cache.getIfPresent("5.5.5.5");
        assertThat(cached).isNotNull();
        assertThat(cached.country()).isEqualTo("JP");
        mockServer.verify();
    }
}
