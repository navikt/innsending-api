# Building and running

- Use the Java and Maven versions in `mise.toml`: run `mise install`, then use `mise exec --` for Maven commands. Tests need a running Docker-compatible container runtime. In a sandbox, do not start Colima; if the Docker socket is unavailable, ask the user to start Docker or Colima outside the sandbox.
- From the repository root, run `mise exec -- mvn clean install` to build all modules and run tests. On macOS, Kotlin compilation may fail with `Operation not permitted` while reading generated OpenAPI sources. `mise exec -- mvn install -Dkotlin.compiler.daemon=false` completed successfully with 438 tests, 2 skipped.
- To run the application with a working Docker socket, use `docker compose up --build`.

See `README.md` for GitHub Packages authentication and local setup details.

# Documentation

- Add only essential project information to `README.md`, such as setup, operation, or changes that require users to act.
- Do not add trivial documentation of individual application features or implementation details merely because code changed.
- When changing Flyway migrations, persistence models, repositories, persisted enum values, converters, views, triggers, or functions, use `.github/skills/refresh-database-schema-docs/SKILL.md`. Assess and update `docs/database-schema.md` in the same change, including its diagram, table reference, and migration list. If the documentation is unaffected, explain why in the pull request.
- When changing audit flags, activation requirements, application-user exemptions, or log destinations, use the same skill and assess and update `docs/auditlogging.md` in the same change. Verify affected searches in an available environment. If verification is blocked, record that limitation rather than claiming the searches are verified.
