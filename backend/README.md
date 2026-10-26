# Open Link Learn Backend

This folder contains the Spring Boot 3.5 API and PostgreSQL persistence layer.
For product scope, full-stack setup, and security requirements, see the root
[README](../README.md).

## Run locally

Start PostgreSQL from the repository root, then run the backend:

```
docker compose up -d db
mvn -f backend/pom.xml spring-boot:run
```

The API listens on `http://localhost:8080`. Flyway applies database migrations
on startup. Swagger UI is at `/swagger-ui/index.html`, the OpenAPI document is
at `/v3/api-docs`, and readiness is at `/actuator/health/readiness`.

## Project structure

Sources follow the standard Maven layout (`src/main/java`, `src/test/java`).
Code is grouped by feature, and each feature is split into Clean Architecture
layers:

```
com.openlinklearn
├── common/              shared API error handling
├── config/              cross-cutting Spring configuration
└── <feature>/           content, dashboard, profile, push, session
    ├── api/             REST controllers
    ├── application/     use-case services
    │   └── dto/         request and response models
    ├── domain/          entities, enums, repositories, ports, domain exceptions
    └── infrastructure/  external adapters, scheduled workers, technical configuration
```

Dependencies point inward: `domain` depends on no other layer, `application`
never uses `api` or `infrastructure`, and nothing outside `api` depends on it.
`ArchitectureTest` enforces these rules during `mvn verify`.

## Test

```
mvn -B -ntp -f backend/pom.xml verify
```

Integration tests use Testcontainers with a disposable PostgreSQL 17 container;
Docker must be running. Tests use isolated schemas rather than the local
application database. Add a Flyway migration for schema changes; do not edit a
migration that has already been applied to a shared environment.

## Configuration and security

Use the root `.env.example` for local configuration. Never commit secrets or
real VAPID private keys. The API has no authentication in V1, so keep it on
localhost or behind trusted private-network controls. See the root README before
making the service reachable from other devices.