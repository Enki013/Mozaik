# Architecture

This project now follows a small Clean Architecture layout in Go while keeping
the same SQLite database and localhost web experience.

## Layers

- `internal/application/`
  - Use cases and source-specific business rules.
  - Goodreads, Letterboxd, and FatSecret sync/import/query logic live here.
- `internal/infrastructure/sqlite/`
  - SQLite connection and schema setup.
  - This is the only layer that owns database initialization details.
- `internal/integrations/fatsecret/`
  - External API/client mechanics.
  - FatSecret OAuth signing and HTTP request helpers live here.
- `internal/presentation/http/`
  - HTTP handler, JSON API responses, HTML rendering, and form parsing.
- `internal/config/`
  - Runtime configuration, `.env` loading, paths, ports, and API constants.
- `internal/shared/`
  - Small parsing/conversion helpers that are not source-specific.
- `cmd/personal-records/`
  - Executable entry point.

## Dependency Direction

Presentation depends on application use cases.
Application depends on infrastructure and integrations.
Infrastructure and integrations do not depend on presentation.

The current app runs with:

```bash
go run ./cmd/personal-records
```

The repository is Go-only.
