# DSE ERP 9.0.84

## Release scope — automated UAT/PROD server deployment

- Added a release-tag GitHub Actions server artifact so UAT and PROD use one exact tested server JAR.
- Added automatic Oracle UAT deployment after the release build/package gates pass.
- Added strict SHA-256 verification before every server switch.
- Added validated PostgreSQL pre-upgrade backup before deployment.
- Added versioned Oracle release directories and `current` symlink switching compatible with the live `/srv/dse-erp/<env>` layout.
- Added automatic binary rollback and rollback health verification if the new server does not start or fails runtime health checks.
- Added public HTTPS health verification after UAT deployment.
- Added a separate manually triggered `Deploy PROD` workflow that downloads the exact GitHub Release server artifact; it never rebuilds a different production JAR.
- Added a PROD gate requiring the requested release version to already be healthy in UAT.
- Added GitHub Environment setup guidance, including a protected `production` environment for GitHub approval before PROD deployment.
- Kept database credentials on the Oracle host; GitHub never receives the PostgreSQL password.
- Explicitly retained the safety rule that Shared Client does not automatically fall back to a stale LOCAL database during an outage.

No ERP business workflow, database schema, sidebar structure, or visual design change is included in this release.
