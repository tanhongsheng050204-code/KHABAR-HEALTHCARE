# Khabar database migrations

The `pilot` Spring profile is the only profile that enables Flyway. It runs migrations and then starts Hibernate with `ddl-auto: validate`, so an unexpected schema mismatch stops startup rather than being silently patched. The `local` and public `demo` profiles retain their pre-existing schema behavior; do not point the pilot profile at the demo database.

## Current migration sequence

- `V1__initial_clinical_schema.sql` creates the current baseline schema before receipt-time tracking was added to readings.
- `V2__record_reading_receive_time.sql` adds `reading.received_at`, backfills existing rows from `measured_at`, then makes the new field required. This is an approximation for legacy rows; readings written after V2 store the server receipt time separately from the measurement time.
- `V4__follow_up_cases_and_clinic_settings.sql` adds follow-up cases and their append-only history (one open case per patient, enforced by a unique key that is cleared on closure, and a check that a closed case has a reason), clinic settings, the weekly rota, and the clinic activity log. It creates tables only; no existing data changes.
- `V3__clinic_staff_grants.sql` adds audited clinic-scoped doctor, nurse, and clinic-administrator grants; adds staff invitations; expands role checks; and backfills existing clinic doctors. The historical grantor is unknown and is kept null.

H2 PostgreSQL-mode tests cover empty-database creation, migration idempotency, V2's legacy-row backfill, V3's doctor grant backfill, and Hibernate validation under the `pilot` profile. A separate conditional smoke test is configured to run against PostgreSQL 16 in GitHub Actions. H2 success alone is not proof of PostgreSQL compatibility; run and retain the hosted result before pilot database use.

The hosted verification workflow rehearses a PostgreSQL 16 custom-format backup and restore using disposable CI databases. Run [36106952266](https://github.com/tanhongsheng050204-code/KHABAR-HEALTHCARE/actions/runs/36106952266) passed on 25 Sep 2026: it checked a marker present at backup time, wrote a second marker after the dump, restored into a separate database, confirmed the pre-dump marker and Flyway history were present, and confirmed the later marker was absent. This demonstrates the dump's recovery point on synthetic CI data; it does not rehearse a production backup, a failed/forward migration recovery, or Vercel deployment rollback.

## Database handling

For an empty pilot database, the first start with `SPRING_PROFILES_ACTIVE=pilot` applies V1 to V4. Back up the database before enabling a new migration in any non-empty environment. Review its SQL and expected data transformation, record the backup, then run the migration and confirm Flyway history plus the application schema validation result. V3 preserves access for pre-existing clinic doctors; it does not infer nurse or administrator membership.

For an existing non-empty database, automatic baseline-on-migrate is deliberately disabled. Do not enable it globally. An operator must first compare the schema with V1, confirm the database is compatible with that baseline, take and verify a backup, then explicitly baseline it at version 1 using the organization's approved Flyway tooling. Startup can then apply V2 and V3. If any relevant table or column differs, create a reviewed forward migration rather than forcing the baseline.

V2 and V3 are forward-only. Dropping `received_at` discards provenance for new readings; dropping staff grants can alter access history and block authorized clinic staff. Prefer a forward fix for application rollback; use database backup/restore only under an approved recovery procedure that accounts for writes since the backup. A rollback/restore rehearsal against PostgreSQL remains required before pilot.

## Verification

```powershell
cd services/api
.\mvnw.cmd -q '-Dtest=FlywayMigrationTest,PilotSchemaSmokeTest' test
```

The full test suite includes these H2 tests. `PostgresPilotSchemaSmokeTest` is skipped unless `-Dkhabar.postgres-smoke=true` is set and `KHABAR_POSTGRES_TEST_URL`, `KHABAR_POSTGRES_TEST_USERNAME`, and `KHABAR_POSTGRES_TEST_PASSWORD` point to an isolated, disposable PostgreSQL database. The CI workflow configures that separate smoke run against a PostgreSQL 16 service.
