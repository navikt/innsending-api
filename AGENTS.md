# Building and running

- Use the Java and Maven versions in `mise.toml`: run `mise install`, then use `mise exec --` for Maven commands. Tests need a running Docker-compatible container runtime. In a sandbox, do not start Colima; if the Docker socket is unavailable, ask the user to start Docker or Colima outside the sandbox.
- From the repository root, run `mise exec -- mvn clean install` to build all modules and run tests. On macOS, Kotlin compilation may fail with `Operation not permitted` while reading generated OpenAPI sources. `mise exec -- mvn install -Dkotlin.compiler.daemon=false` completed successfully with 438 tests, 2 skipped.
- To run the application with a working Docker socket, use `docker compose up --build`.

See `README.md` for GitHub Packages authentication and local setup details.
