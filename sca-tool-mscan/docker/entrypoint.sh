#!/usr/bin/env bash
set -euo pipefail
# -e: 명령 실패 시 즉시 종료
# -u: 정의되지 않은 변수 사용 시 에러
# -o pipefail: 파이프라인 중간 실패도 전체 실패로 처리

# ---------------------------
# 기본 인자 변수
# ---------------------------
NAME=""
JAR_PATH=""
KEYWORDS=""
TARGET_PATH=""
OPTIONS_FILE=""
REUSE="false"
GATEWAY_YAML=""
OUT=""
APP_JVM_ARGS="${APP_JVM_ARGS:-}"

# Gradle 관련 기본값
MSCAN_ROOT="/opt/mscan/mscan-src"
SCAN_DIR="${MSCAN_ROOT}/gateway_entry_scan"
GRADLE_INIT_SCRIPT="/opt/mscan/gradle-init.gradle"

# Dockerfile에서 만들어 둔 캐시를 런타임에서도 그대로 쓰도록 고정
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/opt/mscan/.gradle}"

# 오프라인 실행 기본값: true
# 필요하면 컨테이너 실행 시 MSCAN_GRADLE_OFFLINE=false 로 끌 수 있음
MSCAN_GRADLE_OFFLINE="${MSCAN_GRADLE_OFFLINE:-true}"

# ---------------------------
# CLI 인자 파싱
# ---------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --name|-n) NAME="$2"; shift 2;;
    --jar-path|-j) JAR_PATH="$2"; shift 2;;
    --classpath-keywords|-k) KEYWORDS="$2"; shift 2;;
    --target-path|-t) TARGET_PATH="$2"; shift 2;;
    --options-file|-o) OPTIONS_FILE="$2"; shift 2;;
    --reuse|-r) REUSE="true"; shift 1;;
    --gateway-yaml) GATEWAY_YAML="$2"; shift 2;;
    --out) OUT="$2"; shift 2;;
    --app-jvm-args) APP_JVM_ARGS="$2"; shift 2;;
    *) echo "Unknown arg: $1" >&2; exit 2;;
  esac
done

# ---------------------------
# 필수 인자 검증
# ---------------------------
if [[ -z "$NAME" || -z "$JAR_PATH" || -z "$KEYWORDS" || -z "$GATEWAY_YAML" || -z "$OUT" ]]; then
  echo "Usage: mscan --name <project> --jar-path <path> --classpath-keywords <kw> --gateway-yaml <file> --out <file>" >&2
  exit 2
fi

# ---------------------------
# OpenAI API 키 확인
# gateway entry 추출용
# ---------------------------
if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo "[mscan] OPENAI_API_KEY is required for entry extraction (dev mode)" >&2
  exit 4
fi

# ---------------------------
# 기본값 보정
# ---------------------------
if [[ -z "$TARGET_PATH" ]]; then
  TARGET_PATH="/tmp/${NAME}"
fi

if [[ -z "$OPTIONS_FILE" ]]; then
  OPTIONS_FILE="${MSCAN_ROOT}/src/main/resources/options.yml"
fi

echo "[debug] MSCAN_ROOT=$MSCAN_ROOT"
echo "[debug] SCAN_DIR=$SCAN_DIR"
echo "[debug] TARGET_PATH=$TARGET_PATH"
echo "[debug] OPTIONS_FILE=$OPTIONS_FILE"
echo "[debug] GRADLE_USER_HOME=$GRADLE_USER_HOME"
echo "[debug] MSCAN_GRADLE_OFFLINE=$MSCAN_GRADLE_OFFLINE"

# ---------------------------
# 필수 디렉터리/파일 확인
# ---------------------------
if [[ ! -d "${MSCAN_ROOT}/java-benchmarks" ]]; then
  echo "[mscan] java-benchmarks not found: ${MSCAN_ROOT}/java-benchmarks" >&2
  exit 6
fi

if [[ ! -f "$GRADLE_INIT_SCRIPT" ]]; then
  echo "[mscan] gradle init script not found: $GRADLE_INIT_SCRIPT" >&2
  exit 8
fi

echo "[debug] java-benchmarks exists"
ls -la "${MSCAN_ROOT}"
ls -la "${MSCAN_ROOT}/java-benchmarks"

# ---------------------------
# 작업 디렉터리 준비
# ---------------------------
mkdir -p "${SCAN_DIR}/input" "${SCAN_DIR}/output"
mkdir -p "${MSCAN_ROOT}/src/main/resources/entry"
mkdir -p "$(dirname "$OUT")"
mkdir -p "$TARGET_PATH"
mkdir -p "$GRADLE_USER_HOME"

# gateway 입력 YAML 복사
cp "$GATEWAY_YAML" "${SCAN_DIR}/input/${NAME}.yaml"

# ---------------------------
# 1단계: gateway entry rule 생성
# ---------------------------
pushd "$SCAN_DIR" >/dev/null
python main.py
popd >/dev/null

RULE_JSON="${SCAN_DIR}/output/${NAME}.json"
if [[ ! -f "$RULE_JSON" ]]; then
  echo "[mscan] entry rule not generated: $RULE_JSON" >&2
  exit 5
fi

# 생성된 rule을 MScan resource 위치로 복사
cp "$RULE_JSON" "${MSCAN_ROOT}/src/main/resources/entry/${NAME}.json"

# ---------------------------
# 2단계: Gradle 실행 인자 조립
# ---------------------------
GRADLE_ARGS="--name ${NAME} --jar-path ${JAR_PATH} --classpath-keywords ${KEYWORDS} --target-path ${TARGET_PATH} --options-file ${OPTIONS_FILE}"

if [[ "$REUSE" == "true" ]]; then
  GRADLE_ARGS="${GRADLE_ARGS} --reuse"
fi

# Gradle 공통 옵션
GRADLE_COMMON_ARGS=(--no-daemon -I "$GRADLE_INIT_SCRIPT")

# 캐시가 준비되어 있다는 전제 하에 오프라인 우선 실행
if [[ "$MSCAN_GRADLE_OFFLINE" == "true" ]]; then
  GRADLE_COMMON_ARGS+=(--offline)
fi

# ---------------------------
# 3단계: 실제 MScan 실행
# 주의: 여기서 gradlew run 이 수행되므로
# buildSrc / plugin / dependency 확인이 다시 일어날 수 있음
# 그래서 init script + 동일 GRADLE_USER_HOME + offline 을 강제
# ---------------------------
pushd "$MSCAN_ROOT" >/dev/null

echo "[debug] pwd=$(pwd)"
echo "[debug] checking working-dir java-benchmarks..."
if [[ ! -d "./java-benchmarks" ]]; then
  echo "[mscan] java-benchmarks not visible from working dir: $(pwd)/java-benchmarks" >&2
  popd >/dev/null
  exit 7
fi

echo "[debug] gradle command: ./gradlew ${GRADLE_COMMON_ARGS[*]} run --args=${GRADLE_ARGS}"

if [[ -n "$APP_JVM_ARGS" ]]; then
  ./gradlew "${GRADLE_COMMON_ARGS[@]}" run --args="$GRADLE_ARGS" -PappJvmArgs="$APP_JVM_ARGS"
else
  ./gradlew "${GRADLE_COMMON_ARGS[@]}" run --args="$GRADLE_ARGS"
fi

popd >/dev/null

# ---------------------------
# 4단계: 결과 파일 회수
# ---------------------------
CAND1="${TARGET_PATH}/output/microservice-taint-flows.txt"
CAND2="${MSCAN_ROOT}/output/microservice-taint-flows.txt"

if [[ -f "$CAND1" ]]; then
  cp "$CAND1" "$OUT"
elif [[ -f "$CAND2" ]]; then
  cp "$CAND2" "$OUT"
else
  : > "$OUT"
fi

echo "[mscan] done. report=$OUT"