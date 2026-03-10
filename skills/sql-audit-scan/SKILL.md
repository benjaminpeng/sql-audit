---
name: "sql-audit-scan"
description: "Use when the task is to statically audit SQL, scan MyBatis mapper directories, review .sql change scripts, or check pasted SQL against this repo's OpenGauss-focused SQL audit rules."
metadata:
  short-description: "Run local SQL audit scans for MyBatis and .sql inputs"
---

# SQL Audit Scan

Use this skill when the user wants this repository's SQL static audit capability, not a live database check.

## When to use

- Scan a local MyBatis project directory for SQL issues.
- Review a local `.sql` change script.
- Check pasted SQL text against the built-in rules.

## Workflow

1. Use `scripts/invoke_scan.sh`.
2. Pass one local directory path, one local `.sql` file path, or SQL text via stdin / `--inline-sql`.
3. Read the JSON printed by the script and summarize:
   - mode
   - input
   - violations by severity
   - report file paths
   - notices

## Invocation

Examples:

```bash
skills/sql-audit-scan/scripts/invoke_scan.sh /abs/path/to/repo
skills/sql-audit-scan/scripts/invoke_scan.sh /abs/path/to/change.sql
printf 'SELECT * FROM demo_user;' | skills/sql-audit-scan/scripts/invoke_scan.sh
```

## Guardrails

- This skill is local-only. Do not describe it as a database online audit.
- Prefer directory input for MyBatis mapper scans and `.sql` input for DDL / change scripts.
- If the user pasted SQL, send it through stdin instead of shell interpolation when practical.
- Return a short summary first. Include report paths so the user can inspect JSON or Markdown.
- If Java or Maven is missing, surface the script error directly.

## References

- Invocation contract: `references/contract.md`
- Default built-in rules: `references/default-rules.md`
