---
name: innsending-api-testing
description: Write, change, and review tests in innsending-api. Use when adding or updating Kotlin tests, or reviewing code changes that include tests, especially REST API behaviour, submission and notification side effects, persistence, and concurrency.
---

# Testing innsending-api

Use these principles when writing tests and when reviewing code changes that include tests. Read the changed behaviour and nearby tests first. Existing tests provide fixtures and infrastructure, not an exemption from these principles.

## Choose the test boundary

- Start with black-box REST tests for API-visible behaviour. Exercise the real controller, service, and persistence path. Mock external boundaries, not internal services or repositories.
- Test utilities and mappings directly with unit tests. Use focused service tests with mocked dependencies for behaviour that does not need the HTTP stack.
- Use repository tests for database-specific behaviour, including queries, constraints, locks, and transactions. Direct database setup and assertions are natural at this boundary. Use the real test database rather than mocking database semantics.
- In REST tests, read changed state through REST GET endpoints instead of repository reads or database assertions. A direct database assertion is an exception: explain what cannot be observed through REST and why the assertion belongs in this test.

## REST test workflow

1. Create the application through REST where practical. Reuse request builders and file fixtures. For states that REST cannot create practically, use existing setup helpers or database builders, keeping setup separate from the action under test.
2. Perform the request under test. Assert the expected HTTP status and meaningful response body fields, including error codes for rejected requests. For a response without a body, assert its absence and any relevant headers.
3. Fetch the affected application or attachments through REST and assert the changed state. For rejected mutations, check that the observable state remains correct.
4. Verify relevant outgoing calls to soknadsmottaker and the notification publisher. Assert payloads and required call counts, including the absence of unwanted or duplicate side effects. Match this test's submission identifier, not unrelated background activity.

Cover the changed success path and relevant rejection or failure paths. For submission changes, check receipt contents, attachment state, and downstream payloads as applicable. Do not accept HTTP 500 as the expected outcome of a normal conflict. Assert the intended API contract instead of preserving an accidental server error.

## Reuse the repository's tools

Paths below are relative to `innsender/src/test/kotlin/no/nav/soknad/innsending/`.

- `ApplicationTest.kt` supplies the Spring Boot random-port test context, `test` profile, `WebTestClient`, WireMock, and MockOAuth2Server.
- `utils/ApiWebClient.kt` provides `createSoknad`, `utfyltSoknad`, `getSoknadSendinn`, upload and submission helpers, plus response assertions. Use `assertHttpStatus` when the exact status matters, and inspect the body. Use `webTestClient` or `utils/RestTestClient.kt` for requests the helpers do not cover.
- Reuse `utils/builders/`, `utils/TokenGenerator.kt`, and resources under `innsender/src/test/resources/`. Use synthetic data only. Do not print personal data, tokens, or secrets in test output or failure messages.
- `rest/InnsendingApiIntegrationTest.kt` shows REST-driven submission flows and captured soknadsmottaker payloads. `rest/fyllut/FyllutRestApiTest.kt` shows notification payload assertions at `PublisherInterface`.
- `service/unittest/` contains focused MockK tests. `service/RepositoryUtilsTest.kt` demonstrates database-backed state-transition checks. Reuse JUnit and Kotlin assertions already used nearby rather than adding another framework.

Keep application state and captured calls isolated per test. Prefer the external client or publisher boundary when verifying effects, rather than spying on internal orchestration.

Some external clients also implement `HealthRequestInterface` and have qualifiers required by `HealthCheck`. Preserve those bean contracts when replacing them, for example by mocking the concrete client class. Replacing only the business interface can prevent the test context from starting.

For existing database-backed tests that construct `InnsendingService` directly, `utils/TransactionalSubmissionService.kt` applies its transaction annotations while keeping archival synchronous. REST tests use the application bean. Wrapping an entire test in a transaction is not a substitute for production commit boundaries and can hide stale persistence-context state after bulk updates.

## Asynchronous and concurrent behaviour

- Use bounded waits, such as MockK `verify(timeout = ...)` for outgoing calls or bounded polling of REST GET results. Do not use fixed sleeps. Match `innsendingsId` or the payload's `innsendingId` to this test's application.
- Await completion of work started by the test. A negative verification before asynchronous work finishes does not prove that no call will arrive.
- Make concurrency tests deterministic with explicit coordination and bounded waits. Cover both relevant operation orderings, such as update before submission and submission before update. Launching two coroutines alone does not prove either ordering.
- Assert response statuses and bodies, the final REST-visible state, and external side effects. Avoid assertions about internal call order or repository invocation counts. Put lock and transaction semantics in database-backed tests; do not force internal-layer mocks into REST tests to control scheduling.

## Run focused tests

Run from the repository root. Follow `AGENTS.md`: run `mise install` and use the Java and Maven versions in `mise.toml`. Database-backed tests need a working Docker-compatible runtime. If its socket is unavailable in a sandbox, ask the user to start Docker or Colima outside the sandbox. Do not start Colima there.

The root reactor contains `external-api`, `api`, and `innsender`. Tests live in `innsender` and use Maven Surefire. These selectors refer to existing test classes:

```sh
# Focused unit test
mise exec -- mvn -pl innsender -am test -Dtest=NotificationServiceUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Dkotlin.compiler.daemon=false

# REST submission integration tests
mise exec -- mvn -pl innsender -am test -Dtest=InnsendingApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dkotlin.compiler.daemon=false

# Related REST test classes
mise exec -- mvn -pl innsender -am test -Dtest=FyllutRestApiTest,SoknadRestApiTest -Dsurefire.failIfNoSpecifiedTests=false -Dkotlin.compiler.daemon=false
```

`-am` builds required reactor modules. `-Dsurefire.failIfNoSpecifiedTests=false` lets those modules proceed without the selected tests; check `innsender/target/surefire-reports/` to confirm that the intended tests actually ran. Disabling the Kotlin compiler daemon addresses the macOS generated-source permission issue documented in `AGENTS.md`.

Before delivery, run the full build required by `AGENTS.md` (`mise exec -- mvn clean install`, adding `-Dkotlin.compiler.daemon=false` when needed). Report commands run, failures, and tests not run. When reviewing, distinguish a missing behavioural assertion from a stylistic preference, and identify untested outcomes without broadening the change into unrelated test cleanup.
