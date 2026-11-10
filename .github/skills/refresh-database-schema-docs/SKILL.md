---
name: refresh-database-schema-docs
description: Use when adding or reviewing Flyway migrations, editing or reviewing persisted enums or converters, changing or reviewing persistence models or repositories, updating or reviewing views, triggers or functions, or changing or reviewing audit configuration. Verify that new migrations include the required documentation updates in the same change.
---

# Maintain the database documentation

## Sources

Review the kinds of sources that show what changed and how the application uses the database:

- Database migrations define the physical schema and its evolution. Read them in migration order.
- Persistence models, repositories, persisted enums, and converters show how the application stores and reads data.
- Database and storage code shows how the application uses PostgreSQL and Cloud Storage.
- Application and deployment configuration shows how the application connects to the database, runs migrations, and configures Cloud SQL and auditing.
- The [Google Cloud pgAudit guide](https://docs.cloud.google.com/sql/docs/postgres/pg-audit) describes the audit log format and search fields. Effective extension, role settings, and Data Access logging must be checked in the running environment.

## Workflow

1. Identify which database or audit sources the change affects. Read `docs/database-schema.md` and `docs/auditlogging.md` before editing.
2. Determine the current schema from the ordered migrations. Do not describe a column or constraint as current if a later migration removes it.
3. Update `docs/database-schema.md` with a short overview of the current tables and their relationships. Keep the diagram and table reference focused on facts that help readers understand the schema.
4. Include stored enum values or database objects such as views, triggers, and functions when the change affects them. Keep implementation details out of the overview unless readers need them to understand the schema.
5. Update the complete migration-script list at the bottom of the schema document in numeric version order. Include data migrations and keep descriptions short. Keep application rules out of the overview.
6. When the audit configuration changes, update `docs/auditlogging.md`. Check flags, activation requirements, role exclusions, access, search guidance, and troubleshooting. Do not treat deployment configuration as proof of the running environment's state.
7. Check relative links and Mermaid rendering. Verify affected audit searches in an available environment with known activity from a non-exempt database user. Do not change data, access, or audit configuration to test the documentation without approval.
8. State in the pull request what you checked, the environment and date of any environment verification, and any limitations. If the documentation is unaffected, explain why. Every new migration requires an update to the migration-script list.
9. During review, compare the change with its base branch to identify new Flyway migrations. Verify that `docs/database-schema.md` is updated in the same change, lists every new script, and reflects its effects on the schema. A migration-list entry alone is not enough when the diagram, columns, relationships, or other schema descriptions also change. Report missing or incomplete documentation updates as review findings.

## Checklist

- [ ] The diagram and table reference show the current schema and render on GitHub.
- [ ] The overview matches the migrations and relevant persistence code.
- [ ] Relevant persisted enum values or database objects are covered without adding unrelated implementation details.
- [ ] The migration-script list contains every current script in numeric version order.
- [ ] Every new Flyway migration has documentation updates in the same change, covering both its list entry and any effects on the schema description.
- [ ] Audit guidance reflects the configuration and distinguishes desired setup from verified runtime state.
- [ ] Affected audit searches were verified in an environment, or the missing verification is stated.
- [ ] The documentation contains no personal data, secrets, or actual log entries.
