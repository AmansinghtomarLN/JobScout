# JobScout (Spring Boot backend)

Config-driven multi-company job discovery. An Excel workbook (local file or Google Drive) defines companies,
search profiles, search fields, extraction selectors and pagination; one generic Playwright engine executes it.

Stack: Java 21, Spring Boot 3.3, Playwright for Java, Apache POI, Google Drive API, PostgreSQL + Flyway.

## Run locally
```bash
docker compose up -d db
mvn spring-boot:run            # first run downloads Chromium via Playwright
# or everything in Docker:
docker compose up --build
```
Sample workbook: `config/jobscout-config.xlsx` (regenerate with `python scripts/generate_sample_workbook.py`).

Google Drive source: share the file with a service account and set
`WORKBOOK_SOURCE=drive DRIVE_FILE_ID=... GOOGLE_CREDENTIALS_PATH=/path/key.json`.

## Typical flow
```bash
curl -X POST localhost:8080/api/configurations/sync        # import + validate workbook
curl -X POST localhost:8080/api/runs -H 'Content-Type: application/json' \
     -d '{"companyIds":["AUTODESK"],"dryRun":true}'         # validation-only
curl -X POST localhost:8080/api/runs -H 'Content-Type: application/json' \
     -d '{"companyIds":["AUTODESK"],"maxPages":2}'          # real run
curl localhost:8080/api/runs/1                              # progress
curl 'localhost:8080/api/jobs?company=AUTODESK&postedWithinDays=7'
curl -o jobs.xlsx 'localhost:8080/api/reports/jobs/export?company=AUTODESK'
```

## Workbook sheets
`Companies`, `SearchProfiles`, `SearchFields`, `ExtractionFields`, `Pagination`.
Placeholders `${keyword}`, `${location}`, `${posted_days}` work in `career_url` and `input_value`.
Extraction selector `self` = the result card itself. ROLE selectors: `role[:name]` (e.g. `button:Search`).

## Behaviour
- One task per company, bounded worker pool, per-domain semaphore, retries with exponential backoff + jitter.
- Stops (no bypass) on robots.txt disallow or CAPTCHA/access-denied pages (`ROBOTS_DISALLOWED`, `BOT_PROTECTION`).
- Dates: raw text, parsed date and status (PARSED/ESTIMATED/UNKNOWN/FAILED) stored; known dates outside the window are rejected, unknown ones are kept and flagged.
- Dedup: `company + external id` -> `company + canonical URL` -> `company + title + location`.
- Each run references the config snapshot (id + checksum) it used. Parsed snapshots are held in memory, so retry-with-same-snapshot needs the app to have been up since that sync.

## Adapters, scheduling, alerts
- `adapter_key` GREENHOUSE / LEVER: uses the platform's public job-board API (put the board URL in `career_url`; no selectors needed). Keyword and location are filtered client-side. Any other value falls back to the generic Playwright engine. Add a platform by implementing `JobSource`.
- Scheduler: `SCHEDULE_ENABLED=true` (cron `SCHEDULE_CRON`, default 07:00 Asia/Kolkata) syncs the workbook and runs all enabled companies. Run a single instance.
- Slack digest of new jobs after each run: set `SLACK_WEBHOOK_URL`.

## Not included yet
React UI, Eightfold/Workday adapters (internal endpoints - check site terms first), distributed scheduler lock, SSE/WebSocket updates, roles.
