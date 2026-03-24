#!/usr/bin/env python3
# Agent가 생성한 옵션 YAML이 MScan 계약 스키마(v1)를 만족하는지 검증한다.
# - 형식 오류/필수 키 누락/파일 경로 불일치 시 즉시 실패
# - 성공 시에만 MScan 실행 단계로 넘어간다.
import argparse
import os
import sys

import yaml


def fail(message: str, code: int = 21) -> None:
    # 검증 실패는 stderr + 종료코드로 명확히 반환한다.
    print(f"[mscan] invalid agent options: {message}", file=sys.stderr)
    raise SystemExit(code)


def ensure_str(node: dict, key: str) -> str:
    # 필수 문자열 필드 공통 검증
    value = node.get(key)
    if not isinstance(value, str) or not value.strip():
        fail(f"'{key}' must be a non-empty string")
    return value.strip()


def normalize_path(source_root: str, raw_path: str) -> str:
    # 컨테이너 경로(/work/src/...)를 현재 실행 환경의 경로로 정규화한다.
    if raw_path.startswith("/work/src/"):
        rel = raw_path[len("/work/src/"):]
        return os.path.normpath(os.path.join(source_root, rel))
    if raw_path.startswith("/work/src"):
        rel = raw_path[len("/work/src"):]
        return os.path.normpath(os.path.join(source_root, rel.lstrip("/")))
    return os.path.normpath(raw_path)


def main() -> None:
    # --file: Agent가 만든 옵션 파일
    # --source-root: /work/src로 마운트된 소스 루트 경로
    parser = argparse.ArgumentParser(description="Validate MSASCA agent options schema")
    parser.add_argument("--file", required=True, help="agent options yaml path")
    parser.add_argument("--source-root", default="/work/src", help="mounted source root path")
    args = parser.parse_args()

    if not os.path.isfile(args.file):
        fail(f"file not found: {args.file}", code=22)

    try:
        with open(args.file, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}
    except Exception as e:
        fail(f"cannot parse yaml: {e}", code=21)

    if not isinstance(data, dict):
        fail("root must be a mapping object")

    # 루트 키는 schemaVersion/agent만 허용한다(엄격 모드).
    allowed_root = {"schemaVersion", "agent"}
    unknown_root = set(data.keys()) - allowed_root
    if unknown_root:
        fail(f"unknown root field(s): {sorted(unknown_root)}")

    schema = data.get("schemaVersion")
    if schema != "v1":
        fail(f"'schemaVersion' must be 'v1' but got: {schema!r}")

    agent = data.get("agent")
    if not isinstance(agent, dict):
        fail("'agent' must be an object")

    # agent 하위 키도 계약된 필드만 허용한다.
    allowed_agent = {"sanitizerRegistry", "gatewayEntries"}
    unknown_agent = set(agent.keys()) - allowed_agent
    if unknown_agent:
        fail(f"unknown agent field(s): {sorted(unknown_agent)}")

    sanitizer_registry = ensure_str(agent, "sanitizerRegistry")
    gateway_entries = ensure_str(agent, "gatewayEntries")

    sanitizer_registry_host = normalize_path(args.source_root, sanitizer_registry)
    gateway_entries_host = normalize_path(args.source_root, gateway_entries)

    # 실제 파일 존재 여부까지 확인해야 런타임 실패를 앞단에서 차단할 수 있다.
    if not os.path.isfile(sanitizer_registry_host):
        fail(f"sanitizerRegistry file not found: {sanitizer_registry} -> {sanitizer_registry_host}", code=22)
    if not os.path.isfile(gateway_entries_host):
        fail(f"gatewayEntries file not found: {gateway_entries} -> {gateway_entries_host}", code=22)

    print("[mscan] agent options validation passed")
    print(f"[debug] sanitizerRegistry={sanitizer_registry}")
    print(f"[debug] gatewayEntries={gateway_entries}")


if __name__ == "__main__":
    main()
