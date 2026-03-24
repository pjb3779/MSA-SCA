FROM python:3.11-slim-bookworm

# 중국 환경 대응: USTC mirror + 재시도
RUN set -eux; \
    sed -i 's|deb.debian.org|mirrors.ustc.edu.cn|g' /etc/apt/sources.list.d/debian.sources; \
    sed -i 's|security.debian.org|mirrors.ustc.edu.cn/debian-security|g' /etc/apt/sources.list.d/debian.sources || true; \
    apt-get update; \
    for i in 1 2 3; do \
      apt-get install -y --no-install-recommends --fix-missing \
        openjdk-17-jdk-headless \
        ca-certificates-java \
        git \
        ca-certificates \
        bash && break; \
      echo "apt install failed, retrying ($i)..."; \
      sleep 10; \
      apt-get update; \
    done; \
    rm -rf /var/lib/apt/lists/*

# Java 환경 변수
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

WORKDIR /opt/mscan/mscan-src

# MScan 소스
COPY vendor/MScan /opt/mscan/mscan-src

# java-benchmarks 확인
RUN test -d /opt/mscan/mscan-src/java-benchmarks \
    || (echo "ERROR: java-benchmarks missing in image" && exit 1)

# python 의존성
RUN pip install --no-cache-dir -r /opt/mscan/mscan-src/gateway_entry_scan/requirements.txt

# dev용 파일 덮어쓰기
COPY docker/llm.py /opt/mscan/mscan-src/gateway_entry_scan/config/llm.py
COPY docker/main.py /opt/mscan/mscan-src/gateway_entry_scan/main.py
COPY docker/validate_agent_options.py /opt/mscan/validate_agent_options.py

# entrypoint
COPY docker/entrypoint.sh /opt/mscan/entrypoint.sh

# Windows CRLF → LF 변환 + 실행권한
RUN find /opt/mscan -type f \( -name "*.sh" -o -name "gradlew" \) -exec sed -i 's/\r$//' {} \;
RUN find /opt/mscan -type f \( -name "*.sh" -o -name "gradlew" \) -exec chmod +x {} \;

# Java security 파일 존재 확인 (빌드 단계에서 실패하도록)
RUN test -f /etc/java-17-openjdk/security/java.security

#Gradle dependenct 미리 해결(중국이라 우회)
# Gradle 캐시를 이미지에 남기기(런타임에서도 재사용)
ENV GRADLE_USER_HOME=/opt/mscan/.gradle
RUN mkdir -p "$GRADLE_USER_HOME"

# 1) Plugin Portal URL override (핵심)
#    plugins.gradle.org/m2 -> Aliyun gradle-plugin mirror
RUN set -eux; \
  cat > "$GRADLE_USER_HOME/gradle.properties" <<'EOF'
systemProp.org.gradle.internal.plugins.portal.url=https://maven.aliyun.com/repository/gradle-plugin
systemProp.https.protocols=TLSv1.2,TLSv1.3
org.gradle.daemon=false
EOF

# 2) repositories 미러 우선(선택이지만 안정적)
RUN set -eux; \
  cat > /opt/mscan/gradle-init.gradle <<'EOF'
allprojects {
  repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
  }
}
settingsEvaluated { s ->
  s.pluginManagement.repositories {
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    gradlePluginPortal()
    mavenCentral()
  }
}
EOF

# 3) Gradle dependency 미리 해결(실패를 숨기지 말고 여기서 확실히 성공시켜야 런타임이 안정적)
RUN cd /opt/mscan/mscan-src && \
    ./gradlew --no-daemon -I /opt/mscan/gradle-init.gradle build -x test

ENTRYPOINT ["/opt/mscan/entrypoint.sh"]