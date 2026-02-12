# Access Log Analyzer

CSV 접속 로그 파일을 업로드하면 통계 분석과 IP 지리정보 조회를 수행하는 Spring Boot REST API

### 주요 기능

- CSV 접속 로그 파일 업로드 및 비동기 분석
- 상태코드 비율, Top N 경로/상태코드/IP 등 통계 집계
- ipinfo API를 통한 Top IP 지리정보(국가, 지역, 도시, ISP) 조회
- 분석 상태 실시간 조회 (QUEUED → IN_PROGRESS → COMPLETED/FAILED)

## 목차

- [기술 스택](#기술-스택)
- [실행 가이드](#실행-가이드)
- [API 문서](#api-문서)
- [설계 요약](#설계-요약)
- [이번 과제에서 가장 중요하다고 판단한 기능](#이번-과제에서-가장-중요하다고-판단한-기능)
- [특히 신경 쓴 부분](#특히-신경-쓴-부분)
- [실 서비스 운영 시 개선 포인트](#실-서비스-운영-시-개선-포인트)

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | ![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white) |
| Framework | ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?logo=springboot&logoColor=white) |
| Build | ![Gradle](https://img.shields.io/badge/Gradle-9.3.0-02303A?logo=gradle&logoColor=white) |
| Cache | ![Caffeine](https://img.shields.io/badge/Caffeine-8B6914) |
| API Docs | ![Swagger](https://img.shields.io/badge/Swagger-85EA2D?logo=swagger&logoColor=black) |
| Test | ![JUnit5](https://img.shields.io/badge/JUnit%205-25A162?logo=junit5&logoColor=white) ![AssertJ](https://img.shields.io/badge/AssertJ-DB5855) ![MockMvc](https://img.shields.io/badge/MockMvc-6DB33F) |

## 실행 가이드

### 1. 저장소 클론

```bash
git clone https://github.com/seolbin01/access-log-analyzer.git
cd access-log-analyzer
```

### 2. 환경변수 설정

```bash
# Linux/Mac
export IPINFO_TOKEN=your_token_here

# Windows PowerShell
$env:IPINFO_TOKEN="your_token_here"
``` 

> 토큰 없이도 ipinfo API의 제한된 무료 호출이 가능하여 기본 동작에는 문제없습니다.
> 무료 토큰 발급은 [ipinfo.io](https://ipinfo.io)에서 가능합니다.

### 3. 빌드

```bash
./gradlew clean build
```

### 4. 실행

```bash
./gradlew bootRun
```

- API: `http://localhost:28080`
- Swagger UI: `http://localhost:28080/swagger-ui.html`

### 5. 테스트

```bash
./gradlew test
```

## API 문서

### 엔드포인트 목록

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/analysis` | 로그 파일 업로드 및 분석 요청 |
| `GET` | `/analysis/{analysisId}` | 분석 결과 조회 (`?top=N` 지원, 기본값 10) |

> Swagger UI: `http://localhost:28080/swagger-ui.html`

### POST /analysis

```bash
curl -X POST http://localhost:28080/analysis \
  -F "file=@access_log.csv"
```

**응답 (202 Accepted)**

```json
{
  "analysisId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "QUEUED",
  "queuePosition": 1
}
```

### GET /analysis/{analysisId}

```bash
curl http://localhost:28080/analysis/a1b2c3d4-e5f6-7890-abcd-ef1234567890?top=5
```

**상태별 응답**

| 상태 | 주요 필드 |
|------|----------|
| `QUEUED` | `analysisId`, `status`, `queuePosition` |
| `IN_PROGRESS` | `analysisId`, `status` |
| `COMPLETED` | 전체 분석 결과 (아래 예시) |
| `FAILED` | `analysisId`, `status`, `errorMessage` |

**COMPLETED 응답 예시**

```json
{
  "analysisId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "COMPLETED",
  "analyzedAt": "2026-02-12T15:30:00",
  "totalRequests": 198500,
  "totalLines": 200000,
  "statusGroupRatios": {
    "2xx": 75.3,
    "3xx": 5.1,
    "4xx": 15.2,
    "5xx": 4.4
  },
  "topPaths": [
    { "path": "/api/users", "count": 15200, "percentage": 7.7 }
  ],
  "topStatusCodes": [
    { "statusCode": "200", "count": 120000, "percentage": 60.5 }
  ],
  "topIps": [
    {
      "ip": "203.0.113.50",
      "count": 3200,
      "percentage": 1.6,
      "country": "KR",
      "region": "Seoul",
      "city": "Seoul",
      "org": "AS1234 Example ISP"
    }
  ],
  "errorInfo": {
    "errorCount": 1500,
    "errorSamples": [
      "Line 42: field count mismatch (expected 12, got 8)"
    ]
  }
}
```

## 설계 요약

### 주요 설계 결정

| 영역 | 선택 | 이유 |
|------|------|------|
| CSV 파서 | RFC 4180 상태머신 직접 구현 | 라이브러리 사용 금지 요구사항 + 오류 라인 스킵을 위한 도메인 특화 제어 |
| 캐시 | Caffeine (TTL 1시간, 최대 10,000건) | ipinfo API 호출 최소화 + 동일 IP 중복 조회 방지 |
| 비동기 처리 | ThreadPoolExecutor (core 2, max 4, queue 10) | 대용량 파일 업로드 시 즉시 응답, 백그라운드 분석 |
| 저장소 | ConcurrentHashMap | RDB/Redis 사용 금지 제약 + volatile 쓰기 순서로 스레드 안전성 확보 |
| 집계 방식 | Consumer 콜백 단일 패스 | 중간 리스트 없이 파싱과 집계를 통합하여 메모리 83% 절감 |

### 패키지 구조

```
accessloganalyzer
├── controller/        # REST API 엔드포인트
├── service/           # 비동기 분석, 집계, 상태 관리
├── parser/            # RFC 4180 CSV 상태머신 파서
├── client/            # ipinfo API 연동 (캐시, 재시도, fallback)
├── dto/               # 요청/응답 DTO
├── model/             # 도메인 모델 (AccessLogEntry, AnalysisEntry 등)
└── global/
    ├── config/        # 비동기 스레드풀, ipinfo 설정, Swagger
    └── exception/     # 글로벌 예외 처리, 에러 응답 통일
```

### 데이터 흐름

```
POST /analysis → 임시파일 저장 → 비동기 큐 제출 → 202 Accepted 즉시 반환
                                       ↓
                  백그라운드: 스트리밍 파싱 → 단일 패스 집계 → 결과 저장
                                                                ↓
GET /analysis/{id} → 상태 확인 → COMPLETED면 Top-N 추출 + ipinfo 조회 → 응답
```

---

## 이번 과제에서 가장 중요하다고 판단한 기능

### 로그 파싱 + 통계 집계 파이프라인

이 과제의 핵심은 CSV 파일을 **정확하게 파싱**하고 **신뢰할 수 있는 통계**를 산출하는 것입니다. 파싱과 집계를 분리할 수 없는 하나의 파이프라인으로 보고, 다음과 같이 구현했습니다.

### RFC 4180 호환 상태머신 CSV 파서

라이브러리 사용 금지 요구사항에 따라 상태머신(QUOTED/UNQUOTED) 기반 파서를 직접 구현했습니다. 따옴표 내부의 쉼표, 줄바꿈, 이스케이프된 따옴표를 정확하게 처리합니다.

### Consumer 콜백 기반 단일 패스 처리

파싱된 각 로그 엔트리를 `Consumer<AccessLogEntry>` 콜백으로 즉시 집계에 반영합니다. 중간 리스트를 생성하지 않아 **메모리 사용량을 83% 절감**했습니다.

```
CSV 스트림 → [상태머신 파서] → Consumer 콜백 → [HashMap 집계]
                                    ↓
                            statusCodeCounts.merge()
                            statusGroupCounts.merge()
                            pathCounts.merge()
                            ipCounts.merge()
```

### 부분 성공(Partial Success) 전략

오류가 있는 라인을 스킵하고 나머지를 계속 처리합니다. 오류 수와 샘플(최대 10건)을 응답에 포함하여 데이터 품질을 투명하게 제공합니다.

## 특히 신경 쓴 부분

### 메모리 효율성

50MB/200K 라인 파일을 처리할 때 OOM 없이 안정적으로 동작해야 합니다.

- **임시파일 저장**: `MultipartFile.transferTo(Path)` — 업로드 파일을 힙에 올리지 않음
- **BufferedReader 스트리밍**: 라인 단위 읽기로 전체 파일을 메모리에 적재하지 않음
- **Consumer 콜백 단일 패스**: 중간 `List<AccessLogEntry>` 제거

| 지표 | 개선 전 | 개선 후 | 개선율 |
|------|---------|---------|--------|
| 메모리 사용량 | 138.89MB | 23.09MB | **-83%** |
| 실행 시간 | 526ms | 362ms | **-31%** |
| GC 횟수 | 11회 | 5회 | **-55%** |
| GC 소요 시간 | 63ms | 6ms | **-90%** |

> 200,000 라인 CSV 기준 벤치마크 (3회 반복 평균)

### 장애 허용(Graceful Degradation)

외부 의존성 실패가 전체 분석을 중단시키지 않도록 설계했습니다.

- **CSV 파싱 오류**: 해당 라인만 스킵, 에러 샘플 수집 후 계속 처리
- **ipinfo API 실패**: 최대 2회 재시도 → 실패 시 `"UNKNOWN"` fallback 반환
- **ipinfo 캐시**: Caffeine 캐시(TTL 1시간, 최대 10,000건)로 중복 조회 방지

### 설정 분리

ipinfo 토큰, 타임아웃, 재시도 횟수, 캐시 설정, 스레드풀 크기 등 모든 외부 의존 값을 `application.yml` + 환경변수로 분리하여 코드 변경 없이 운영 조정이 가능합니다.

### 비동기 처리와 스레드 안전성

과제에서 기본 동기 처리도 허용했지만, 대용량 파일의 실제 처리 시간을 고려하여 비동기 큐 기반 처리를 구현했습니다.

- 분석 요청 즉시 `202 Accepted` 반환 → 백그라운드 스레드에서 처리
- `volatile` 필드 쓰기 순서 보장: `result` 먼저 쓴 후 `status`를 COMPLETED로 변경하여, 읽는 쪽에서 status가 COMPLETED면 result가 반드시 보이도록 함
- `ConcurrentHashMap`으로 분석 결과 관리

## 실 서비스 운영 시 개선 포인트

- **분석 결과 영속화**: `ConcurrentHashMap` → RDB 전환으로 서버 재시작 시 데이터 유실 방지
- **분석 결과 만료/정리**: 완료된 분석 결과의 TTL 설정 및 정리 메커니즘 추가 (현재 OOM 위험)
- **재시도 backoff**: ipinfo 재시도 시 지수 백오프 딜레이 적용 (현재 즉시 재시도)
- **파일 저장소**: S3 등 외부 스토리지 활용으로 재분석 가능하도록 구성
- **모니터링/메트릭**: 분석 소요 시간, 캐시 적중률, API 응답률 등 운영 지표 수집
- **구조화 로깅 고도화**: 현재 SLF4J 텍스트 로그 → JSON 포맷 전환(Logback JSON encoder)으로 ELK 등 로그 수집 파이프라인과 연동 가능하도록 개선


