# Contract

`scripts/invoke_scan.sh` is the only execution entrypoint for this skill.

## Supported inputs

- One positional directory path: treat as MyBatis repository scan.
- One positional `.sql` path: treat as SQL script scan.
- `--repo-path <dir>`
- `--sql-file <file>`
- `--inline-sql <sql>`
- stdin with SQL text when no path input is given.

## Output

The script prints a single JSON object to stdout:

```json
{
  "ok": true,
  "mode": "repo",
  "input": "/abs/path",
  "json_report_path": "/abs/path/to/report.json",
  "markdown_report_path": "/abs/path/to/report.md",
  "summary": {
    "total_files": 12,
    "total_statements": 86,
    "total_violations": 5,
    "error_count": 2,
    "warning_count": 3,
    "info_count": 0,
    "limit_reached": false
  },
  "notices": []
}
```

## Behavior

- Builds `backend` with `mvn -q -DskipTests package` when the fat jar is missing or stale.
- Runs the fat jar in CLI mode with:
  - `--spring.main.web-application-type=none`
  - `--sql-audit.cli.enabled=true`
  - one input switch
  - `--sql-audit.cli.json-out`
  - optional `--sql-audit.cli.markdown-out`
- Writes reports under `output/sql-audit/<timestamp>/`.
- For inline SQL, writes a temp `.sql` file under `tmp/sql-audit/inline/`, then removes it after the scan.

## Exit behavior

- Exit `0`: scan completed.
- Non-zero: build or scan failure. stderr contains the reason.
