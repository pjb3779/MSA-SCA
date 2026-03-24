# Autobuild 빌드 에러 카탈로그

Maven/Gradle 등 빌드 과정에서 발생한 에러를 한곳에 모아 관리한다.  
CodeQL autobuild, Docker 빌드 등 공통으로 사용한다.  
각 에러 유형마다 **재시도 횟수**, **폴백 전략** 등을 매핑하며, 코드는 `sca-core`의 `AutobuildErrorCatalog.java`에서 관리한다.

---

## 공통 설정

| 항목 | 값 | 설명 |
|------|-----|------|
| `RETRY_INTERVAL_MS` | 5000 | 재시도 간격 (ms) |

---

## 에러 목록

### 1. 네트워크 연결 실패

| 항목 | 내용 |
|------|------|
| **ID** | `NETWORK_CONNECTION_FAILURE` |
| **처리** | 재시도 3회 |
| **패턴** | `Could not transfer artifact`, `Connection refused`, `Connection timed out`, `Unknown host`, `Received fatal alert` |

**에러 예시:**
```
Could not transfer artifact org.apache.maven.plugins:maven-compiler-plugin:jar:3.8.1
from/to central (https://repo.maven.apache.org/maven2): Connection timed out
```

**원인:** Maven/Gradle이 의존성 다운로드 시 네트워크 일시 장애.

**처리 동작:** 동일 명령을 최대 3회 재시도 (간격 5초).

---

### 2. Parent POM 해석 실패 (Maven Multi-Module)

| 항목 | 내용 |
|------|------|
| **ID** | `PARENT_POM_UNRESOLVABLE` |
| **처리** | 폴백 `USE_PROJECT_ROOT` |
| **패턴** | `parent.relativePath`, `Non-resolvable parent POM`, `points at wrong local POM` |

**에러 예시:**
```
[FATAL] Non-resolvable parent POM for org.springframework.cloud:spring-cloud-skipper-server:3.0.0-SNAPSHOT: 
Could not find artifact org.springframework.cloud:spring-cloud-dataflow-parent:pom:3.0.0-SNAPSHOT 
and 'parent.relativePath' points at wrong local POM @ line 10, column 10
```

**원인:** 모듈 단독 경로에서 autobuild가 실행되면 parent POM을 찾지 못함.

**처리 동작:**

1. **parent POM 검색·install**: `ParentPomFinder`로 실패한 pom.xml에서 parent artifactId 추출, 소스 트리에서 해당 pom.xml 검색. 찾으면 `mvn install -f <parent경로>` 실행.
2. **root POM install**: `mvn -f /src/pom.xml install -N -DskipTests` 실행. 이후 CodeQL run과 동일 `workDir/maven-repo` 볼륨 공유.
3. **프로젝트 루트 빌드**: `mvn -f /src/pom.xml clean package -pl <modulePath> -am` 실행.

**루트도 실패 시 (예: spring-cloud-skipper 등 상위 parent 미포함):** `--build-mode=none`으로 빌드 없이 DB 생성 (CodeQL 2.16.5+). `--source-root`를 모듈 경로, 프로젝트 루트 순으로 시도하여 "did not detect any code" 완화.

**Spring 부모 POM (spring-cloud-dataflow-build 등) resolve:** CodeQL Docker 실행 시 `workDir/maven-repo/settings.xml`을 자동 생성. Maven Central + spring-releases + spring-milestones + spring-snapshots 포함. `/root/.m2` 마운트 시 Maven이 사용.

---

### 3. (추가 예정)

새로 마주친 에러는 위 형식으로 항목을 추가한다.

---

### 3. 소스 코드 미검출 (CodeQL exitCode=32)

| 항목 | 내용 |
|------|------|
| **ID** | `CODEQL_NO_SOURCE_DETECTED` |
| **처리** | 사전 스킵 (실패로 간주하지 않음) |
| **패턴** | `CodeQL did not detect any code written in languages supported` |

**에러 예시:**
```
CodeQL did not detect any code written in languages supported by this CodeQL distribution ...
```

**원인:** starter/bom/empty module처럼 모듈 루트 아래에 Java/Kotlin 소스 파일이 없는 경우.

**처리 동작:**

1. CodeQL 실행 전 모듈 경로를 스캔해 `*.java`, `*.kt` 존재 여부를 확인.
2. 소스가 없으면 해당 모듈 CodeQL은 스킵하고 `codeql-skip.log` artifact 기록.
3. 파이프라인은 중단하지 않고 다음 모듈 진행.

---

## 참고: 현재까지 마주친 에러 요약

| # | 에러 | 처리 |
|---|------|------|
| 1 | 네트워크 연결 실패 | 재시도 3회 (간격 5초) |
| 2 | Parent POM 해석 실패 | USE_PROJECT_ROOT 폴백 → 루트도 실패 시 --build-mode=none |
| 3 | CodeQL 소스 코드 미검출 | 사전 스캔 후 스킵 (`codeql-skip.log`) |

---

## CodeQL DB 생성 실패 시 처리

parent POM 등 해결 불가 오류로 CodeQL DB 생성이 실패하면:
- 해당 모듈의 `tool_run`에 `codeql-db-failure.log` artifact로 실패 사유 기록
- 파이프라인은 중단하지 않고 **다음 모듈로 진행** (부분 실패 허용)
- `tool_run` 상태는 FAILED로 기록됨

---

## 사용 방법

1. **새 에러 추가 시**
   - 이 문서에 에러 항목 추가
   - `AutobuildErrorCatalog.CATALOG`에 `Entry` 추가 (retryCount, fallback 지정)
   - 새 `FallbackStrategy`가 필요하면 enum 확장 + 어댑터에 로직 추가

2. **처리 유형**
   - **재시도**: `retryCount` > 0, `fallback` = null → 동일 명령 재시도
   - **폴백**: `fallback` 지정 → 해당 전략 적용 (예: 프로젝트 루트 빌드)
