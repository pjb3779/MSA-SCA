FROM ubuntu:22.04

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
      curl tar gzip git \
      openjdk-17-jdk \
      maven gradle \
      bash python3 ca-certificates && \
    rm -rf /var/lib/apt/lists/*

ARG CODEQL_VERSION=2.18.3
ENV CODEQL_HOME=/opt/codeql-home

#codeql-bundle 설치 (qlpacks 포함)
RUN mkdir -p "${CODEQL_HOME}" && \
    curl -L "https://github.com/github/codeql-action/releases/download/codeql-bundle-v${CODEQL_VERSION}/codeql-bundle-linux64.tar.gz" \
      -o /tmp/codeql-bundle.tar.gz && \
    tar -xzf /tmp/codeql-bundle.tar.gz -C "${CODEQL_HOME}" && \
    rm /tmp/codeql-bundle.tar.gz

ENV PATH="${CODEQL_HOME}/codeql:${CODEQL_HOME}/codeql/codeql:${PATH}"

RUN codeql --version && codeql resolve qlpacks | head -n 50

WORKDIR /work