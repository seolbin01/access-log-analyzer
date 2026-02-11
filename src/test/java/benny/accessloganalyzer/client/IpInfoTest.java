package benny.accessloganalyzer.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpInfoTest {

    @DisplayName("IpInfo는 country, region, city, org 필드를 가진다")
    @Test
    void hasExpectedFields() {
        IpInfo info = new IpInfo("KR", "Seoul", "Seoul", "KT Corporation");

        assertThat(info.country()).isEqualTo("KR");
        assertThat(info.region()).isEqualTo("Seoul");
        assertThat(info.city()).isEqualTo("Seoul");
        assertThat(info.org()).isEqualTo("KT Corporation");
    }

    @DisplayName("unknown()은 모든 필드가 UNKNOWN인 IpInfo를 반환한다")
    @Test
    void unknownReturnsAllFieldsUnknown() {
        IpInfo unknown = IpInfo.unknown();

        assertThat(unknown.country()).isEqualTo("UNKNOWN");
        assertThat(unknown.region()).isEqualTo("UNKNOWN");
        assertThat(unknown.city()).isEqualTo("UNKNOWN");
        assertThat(unknown.org()).isEqualTo("UNKNOWN");
    }
}
