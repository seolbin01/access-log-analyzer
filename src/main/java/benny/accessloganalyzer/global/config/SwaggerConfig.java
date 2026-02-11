package benny.accessloganalyzer.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(info = @Info(
        title = "Access Log Analyzer API",
        description = "접속 로그(CSV) 파일을 업로드하면 파싱하여 요약 통계를 제공하는 API",
        version = "1.0.0"
))
@Configuration
public class SwaggerConfig {
}
