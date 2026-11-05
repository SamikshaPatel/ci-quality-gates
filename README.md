# CI Quality Gates

**A CI pipeline whose quality gates are a written, enforced contract.** QA defines what a green build means, the pipeline enforces it from one config file, and every failure says what broke, who owns it and what to do next.

This is a portfolio project. The test suites are small on purpose: 29 tests. The design, the decisions and the failure handling are the point.

## What green means

```mermaid
flowchart TD
    PR[PR opened] --> B[Evaluator tests<br/>≥80% line coverage]
    B --> S{Smoke<br/>100%, no retries}
    S -- fail --> SX[Hard stop: PR blocked<br/>tagged ENV or CODE]
    S -- pass --> K{Contract<br/>responses match schemas}
    K -- fail --> KX[PR blocked<br/>a consumer would break]
    K -- pass --> C{Critical tier<br/>100%, no retry credit}
    C -- fail --> CX[PR blocked<br/>never quarantined]
    C -- pass --> N{Non-critical tier<br/>≥80% pass, ≤20% flaky}
    N -- flake breach --> Q[Quarantine flaky tests<br/>capped, Jira ticket each]
    Q -- cap hit --> QX[PR blocked]
    Q -- rerun --> N
    N -- pass --> M[Merge allowed]
```

| Gate | Pass rule | Retries | On failure |
|---|---|---|---|
| Smoke | 100% | 0 | Hard stop, later gates skipped, failure tagged ENV or CODE |
| Contract | 100%, every response matches its JSON schema | 0 | PR blocked, later gates skipped |
| Critical tier | 100%, a pass on retry still fails | 1, to detect flakiness | PR blocked, never quarantined |
| Non-critical tier | ≥ 80% pass and ≤ 20% flaky | 1 | Flake breach: quarantine, ticket, rerun. Otherwise the PR is blocked |

Beside these, the **Security** workflow runs CodeQL and dependency review on every PR, and the evaluator must keep 80% line coverage before any test gate runs. On a pull request, **impact analysis** runs only the gates the change can affect (a docs-only PR runs no test gates; an API-only PR skips UI regression), and new or changed tests must pass 3 runs in a row. The full rules, with owners and runbooks, are in **[CI_CONTRACT.md](CI_CONTRACT.md)**. Every number lives in **[quality-gates.yml](quality-gates.yml)**.

## Proof

| Claim | Where to check |
|---|---|
| The contract is written down | [CI_CONTRACT.md](CI_CONTRACT.md) |
| Thresholds live in config, not in workflows | [quality-gates.yml](quality-gates.yml), read by [the gate evaluator](gate-evaluator/src/main/java/io/github/samikshapatel/qg/gate/GateEvaluator.java) |
| Every branch of the contract is tested | [GateEvaluatorTest](gate-evaluator/src/test/java/io/github/samikshapatel/qg/gate/GateEvaluatorTest.java) |
| Each failure path can be shown on demand | Actions → **Quality gates** → **Run workflow** → pick a scenario |
| Decisions are reasoned | [docs/adr/](docs/adr) |
| Every failure has a runbook | [docs/runbooks/](docs/runbooks) |
| Releases are promoted, not rebuilt, with an approval | Actions → **Promote**, and the repo's **Deployments** |
| Every run, newest first, with trends | [Run history site](https://samikshapatel.github.io/ci-quality-gates/) |
| Frozen results of each scenario | [docs/evidence/](docs/evidence) |
| The contract is not tied to one CI tool | [Jenkinsfile](Jenkinsfile) calls the same scripts |

## Demo scenarios

Go to **Actions → Quality gates → Run workflow** and choose one; each takes a few minutes. To see a scenario on a pull request instead, with the run report as a PR comment, put `[demo:<scenario>]` in the PR title, for example `[demo:flake-breach] Show quarantine on a PR`. Demo PRs are evidence and are never merged.

| Scenario | What happens | Expected result |
|---|---|---|
| `none` | Normal run | All three gates pass |
| `env-down` | The API container is stopped | Smoke fails, **ENV**, owner DevOps |
| `smoke-code` | A smoke test expects the wrong status | Smoke fails, **CODE**, owner PR author |
| `critical-flake` | A critical test fails once, then passes | Critical fails: no retry credit |
| `flake-breach` | 3 non-critical tests fail once, then pass | Flake rate 21.4% leads to quarantine, a Jira ticket each, and a rerun that passes |
| `quarantine-cap` | 6 non-critical tests fail once, then pass | Quarantine refused at the cap of 5, PR blocked |

The scenarios use [deliberate fault tests](docs/adr/0005-deliberate-fault-tests.md). They are deterministic, and excluded from every normal run.

## The run report

Every run writes one report: in the job summary, as a sticky PR comment, as `gate-results/run-report.md`, and on the [run-history site](https://samikshapatel.github.io/ci-quality-gates/). It says what happened, what the pipeline did, and who acts next. On a breach, `gate-results/quality-gate-failure.txt` carries the same facts for scripts.

| Failure | Pipeline action | Owner |
|---|---|---|
| Smoke, ENV | Build failed, PR blocked | DevOps |
| Smoke, CODE | Build failed, PR blocked | PR author |
| Critical tier | Build failed, PR blocked | PR author, QA reviews |
| Non-critical flake breach | Quarantine, Jira ticket, rerun | QA, with the test's author |
| Quarantine cap hit | Build failed, PR blocked | QA lead |

## Systems under test

| System | Layer | Where it runs | Why |
|---|---|---|---|
| [Restful-Booker](https://github.com/mwinteringham/restful-booker) | API | Docker, inside CI | The public instance resets every 10 minutes; a private copy is an environment the gates control |
| [SauceDemo](https://www.saucedemo.com) | UI | Public site | A static site built for automation practice, with no shared data |

## Stack

Java 21 · Maven · RestAssured · Playwright for Java · JUnit 5 · GitHub Actions · Jenkins · Jira Cloud · Docker Compose · Allure results

```
quality-gates.yml       every threshold, cap, pattern and owner
CI_CONTRACT.md          what green means, who acts on what
quarantine.json         current quarantine, written by the gate
test-support/           quarantine skipping, tier tags, deterministic fault markers
gate-evaluator/         reads Surefire XML, applies the gates, writes the run report, opens Jira tickets
api-tests/              13 RestAssured tests + deliberate fault tests
ui-tests/               12 Playwright tests
scripts/                run-gate.sh, run-regression.sh (shared by GitHub Actions and Jenkins), publish-history.sh
site/                   the run-history site (index.html, run.html), published to gh-pages
.github/workflows/      pr.yml (gates + scenarios), nightly.yml (quarantine review), release.yml (evidence pack)
docs/                   test plan, ADRs, runbooks, evidence
```

## Workflows

- **Quality gates** (`pr.yml`) runs on every PR and from the Run workflow button. Smoke runs first, then the critical tier, then the non-critical tier. It posts the run report on the PR and uploads evidence.
- **Nightly** (`nightly.yml`) runs the full gates on `main`, then reviews quarantine: quarantined tests run and build a green streak. A test is released after 7 clean nights, and one older than 14 days fails the run. Changes to `quarantine.json` arrive as a review PR, because `main` is protected even from the bot.
- **Run history** (GitHub Pages, `gh-pages` branch): every run publishes its report to `runs/<date-time>_run<id>/`. The site lists all runs newest first, with filters for trigger, real or demo run, and result, plus trend charts of non-critical pass and flake rates and pipeline duration against the 10-minute budget. Demo runs are excluded from the trends. This plays the role a results server such as ReportPortal or Allure TestOps plays in larger teams.
- **Promote** (`promote.yml`) runs on every merge to `main`: build once, then QA → Staging → Production, each verifying the artifact's SHA-256. QA and Staging run the full gates; Staging opens a Jira change record; Production waits for a required reviewer, then runs a post-deployment smoke check ([ADR-0011](docs/adr/0011-promotion-with-approvals.md)).
- **Release** (`release.yml`) runs on a `v*` tag. It runs the gates on the tagged commit and publishes a GitHub release with an evidence pack attached.

## Setup

1. **Branch protection:** Settings → Rules → Rulesets → a branch ruleset on the default branch, active, with no bypass: restrict deletions, require a pull request (0 approvals for a solo repo, 1 or more in a team), require the **Smoke → Critical → Non-critical** status check with branches up to date, and block force pushes.
2. **Security checks:** turn on **Settings → Code security → Dependency graph** (dependency review needs it). In `protect-main`, under **Require status checks to pass**, add **Dependency review**; then tick **Require code scanning results**, add **CodeQL**, and set security alerts to **High or higher** and alerts to **Errors**. On the same settings page, turn on **Secret scanning** and **Push protection**.
3. **Secrets and variables:** Settings → Secrets and variables → Actions. Only credentials are secrets. The Jira site and project key are plain variables, because GitHub masks every secret value in logs and reports, which would turn ticket links into `***-1`.

   | Name | Kind | Purpose | Required |
   |---|---|---|---|
   | `QUALITY_BOT_TOKEN` | Repository secret | Fine-grained token (Contents and Pull requests: read and write) so the bot's quarantine commits start a new run and nightly can open review PRs | Yes, once `main` is protected |
   | `JIRA_EMAIL`, `JIRA_API_TOKEN` | Repository secret | Quarantine tickets | No: without them, tickets are skipped and the report says so |
   | `JIRA_BASE_URL`, `JIRA_PROJECT_KEY` | Repository variable | Jira site and project for tickets | With the two secrets above |
   | `RB_USERNAME`, `RB_PASSWORD` | Restful-Booker auth | No: falls back to the public demo defaults |
   | `SAUCE_USERNAME`, `SAUCE_PASSWORD` | SauceDemo login | No: falls back to the public demo defaults |

4. **Jenkins (optional):** create the four Jira credentials named in the `Jenkinsfile`, and point a Pipeline job at this repo.

## Run locally

```bash
docker compose -f docker-compose.ci.yml up -d --build --wait
mvn -B install -pl test-support,gate-evaluator -am      # build + evaluator unit tests
mvn -B test-compile -pl api-tests,ui-tests
mvn -B -q -pl ui-tests exec:java@install-browsers         # Chromium for Playwright
mkdir -p gate-results
scripts/run-gate.sh smoke && scripts/run-gate.sh critical && scripts/run-regression.sh
java -jar gate-evaluator/target/gate-evaluator.jar report
DEMO_SCENARIO=flake-breach scripts/run-regression.sh       # try a scenario
```
