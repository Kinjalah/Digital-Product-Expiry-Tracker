# FA2: Digital Product Expiry Tracker (ExpiryLens)

Assessment: 14 October 2026. Two students per group. Kinjal Heda and the second group member must both participate in the original-voice 1–2 minute video.

## Focused FA2 backlog, verified in Jira

All five stories have the label `fa2-demo`. These are existing product stories plus a new CI/CD release story, not duplicate product implementation work.

| Story | User outcome | Actual Jira points | Current status |
|---|---|---:|---|
| DET-9 | Log in to manage expiry records | 3 | Done |
| DET-11 | Add an item with valid expiry details | 5 | Done |
| DET-13 | Edit or delete an expiry record | 5 | Done |
| DET-16 | Identify upcoming expiries | 2 | Done |
| DET-39 | Verify each change and deploy a demo release | 5 | To Do; GitHub write access blocked |

The focused demonstration covers 20 points across historical and planned sprints. This total is not sprint velocity.

## Sprint plan, verified and updated in Jira

DET Sprint 4, ID 136, remains future/planned with the existing 5–19 October 2026 dates. DET-39 is assigned with 5 points. Its goal now describes build, static analysis, regression tests, expiry-safety gate, artifact publication, temporary deployment and genuine evidence. The release story remains open until an actual GitHub Actions run succeeds and both voices are recorded. Historical product stories remain in Sprint 1.

Definition of Done is stored in DET-39. Boundary acceptance criteria have been saved in DET-16. Both students must participate in the original-voice 1–2 minute submission. Partner identity has not been confirmed.

## Acceptance criteria for DET-16

- Given an item expired yesterday, when the upcoming list is requested, then it is excluded and the full list reports negative days remaining.
- Given an item expires today, when the list is requested, then it is included with zero days remaining.
- Given an item expires in 7 days, then the seven-day window includes it.
- Given an item expires in 8 days, then the seven-day window excludes it.
- Upcoming items appear in ascending order of days remaining.

## Acceptance criteria for DET-11

- A nonblank item name and supported valid calendar date save successfully.
- An impossible date such as 31/02/2027 returns HTTP 400 and is not saved.
- The leap day 29/02/2028 is accepted.

## Definition of Done

- Acceptance criteria demonstrated by automated tests or smoke checks.
- Frontend ESLint and production build pass.
- Backend tests and expiry gate pass with zero failures.
- JaCoCo reports actual coverage without a fabricated target or result.
- A versioned JAR containing the frontend is published as an artifact.
- The same artifact starts in the temporary demo environment and smoke checks pass.
- Genuine screenshots and run/commit links are retained.
- Both members can explain their work in the original-voice video.

## Deployment and scope

The demo deployment is an isolated GitHub Actions runner with an in-memory H2 database. It ends when the job ends. The JAR can also run locally with `--spring.profiles.active=demo`. Data resets on restart. OCR and real email delivery need separate external configuration and are outside this FA2 demonstration. Existing login is demonstration-level authentication and is not a production authorization system.
