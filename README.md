# SCA Backend (Multi-module Skeleton) — buaa.msasca.sca

- Spring Boot 3.3.x (apps only)
- Java 17
- Lombok enabled in all modules
- JPA + PostgreSQL
- Ports & Adapters style
- Storage intentionally left open: `StoragePort` + `NoopStorageAdapter` placeholder

## Run Postgres
```bash
docker compose up -d
```

## Run API (8080)
```bash
./gradlew :sca-app-api:bootRun
```

## Run Worker (8081)
```bash
./gradlew :sca-app-worker:bootRun
```

## Try
```bash
# create project
curl -X POST localhost:8080/api/v1/projects \
  -H 'Content-Type: application/json' \
  -d '{"name":"demo"}'

# request analysis run (worker polls every 5s)
curl -X POST localhost:8080/api/v1/runs \
  -H 'Content-Type: application/json' \
  -d '{"projectId":"<PROJECT_ID>"}'

# check run status
curl localhost:8080/api/v1/runs/<RUN_ID>
```
