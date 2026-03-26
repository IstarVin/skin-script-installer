# Project Guidelines

## Build And Test Gate

- After every code edit, always run both commands in this order:
  - ./gradlew :app:assembleDebug
  - ./gradlew test
- If either command fails, read the errors, fix the issues, and rerun both commands.
- Repeat the fix and rerun cycle until both commands pass, or until blocked by an external constraint that cannot be resolved in-repo.
- Do not skip this gate for small changes.

## Reporting

- When blocked, clearly report:
  - Which command failed
  - The main error
  - What was attempted
  - What is needed next to unblock
