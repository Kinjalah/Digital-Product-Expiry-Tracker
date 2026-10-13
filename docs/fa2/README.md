# FA2 execution

1. Open the repository root in VS Code. Use Java 17 and Node 22. Windows uses `backend\\mvnw.cmd`.
2. In `frontend`, run `npm ci` and `npm run build`.
3. For development, start the demo backend with `bash mvnw spring-boot:run -Dspring-boot.run.profiles=demo` from `backend`, then `npm run dev` from `frontend`.
4. For the packaged application, copy `frontend/dist` contents to `backend/src/main/resources/static`, run `bash mvnw verify`, and start `java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo`.
5. Open http://localhost:8081 for the packaged app, or http://localhost:5173 for development. Register a new demo account.
6. Push a feature branch or open a PR to trigger `.github/workflows/fa2-ci-cd.yml`.
7. Inspect both jobs and download quality, application and deployment evidence artifacts. Record real test totals and coverage from the run summary.
8. Use `agile-plan.md` as the focused FA2 backlog and DoD. Check/update Jira through the actual board before capturing Jira evidence.

## Evidence checklist

- Jira backlog with 3–5 focused stories and sprint goal
- Story acceptance criteria and DoD
- VS Code workflow/source and successful terminal output
- GitHub PR with changed files and automatic checks
- Actions run with build, test, quality, expiry gate and deployment stages
- Actual test and coverage reports
- Running app screenshots: login, upcoming expiry warning, add/edit/delete
- Packaged release artifact and deployed smoke report

## Video structure, 110 seconds

0–10: both names and project goal. 10–28: Jira stories, sprint and DoD. 28–45: VS Code workflow and commit/PR trigger. 45–77: actual Actions stages, expiry gate, test results and coverage. 77–101: deployed app and add-item demonstration. 101–110: artifact, smoke result and both members' contribution.

Record with your own voices. Do not use text-to-speech. The notice requires one 1–2 minute video submitted to Google Classroom. A PPT is supporting material.
