# Three-person implementation and commit workflow

This plan keeps production files separated between the first two branches and
assigns integration/documentation to the third person. Each student uses their
own GitHub account and Git identity. Pull requests are integrated with
"Create a merge commit"; squash merges are not used, so every individual
commit stays visible in `main`'s history.

## 0. One-time team setup

Each student clones the repository, sets their own Git identity, and confirms
the toolchain before touching any code:

```bash
git clone https://github.com/jhonatanpenamora-png/Lab03_ARSW.git
cd Lab03_ARSW/lab-arsw-relic-rush-concurrency-deadlocks-main
git config user.name "STUDENT NAME"
git config user.email "GITHUB EMAIL"
java -version
mvn -version
mvn clean test
mvn -q -DskipTests package
```

Before changing code, the baseline output is captured as diagnostic evidence:

```bash
java -cp target/classes edu.eci.arsw.relicrush.app.LedgerRaceProbe 64 5000
java -cp target/classes edu.eci.arsw.relicrush.app.DeadlockProbe
```

## 1. Team member 1 - thread-safe ledger

### Branch

```bash
git switch main
git pull origin main
git switch -c feat/thread-safe-ledger
```

### Scope

Only `ForgeLedger.java` and `ForgeLedgerConcurrencyTest.java`. `total` becomes
an `AtomicInteger`, the event list becomes a `ConcurrentLinkedQueue<ForgeEvent>`,
null events are rejected, and `events()` returns an immutable copy.

### Commit

By team agreement this scope was registered as a single commit:

```bash
git add -- src/main/java/edu/eci/arsw/relicrush/concurrency/ForgeLedger.java
git add -- src/test/java/edu/eci/arsw/relicrush/ForgeLedgerConcurrencyTest.java
git commit -m "Corregir seguridad de concurrencia en ForgeLedger"
git push -u origin feat/thread-safe-ledger
```

PR 1 (`feat/thread-safe-ledger` -> `main`) does not touch `LockPair.java`, the
ADR, or `REPORT.md`.

## 2. Team member 2 - deadlock prevention and ADR

This work starts in parallel from `main` because it touches different files.

### Branch

```bash
git switch main
git pull origin main
git switch -c feat/prevencion-deadlock
```

### Commits (in order)

1. `LockPairTest.java` reproduces the deadlock with opposite station request
   order:
   ```bash
   git add -- src/test/java/edu/eci/arsw/relicrush/LockPairTest.java
   git commit -m "Agregar prueba para reproducir el deadlock"
   ```
2. `LockPair.java` is fixed to acquire distinct stations by ascending ID
   (`lower` before `higher`), reentrant for the same station object, and
   rejects duplicate IDs on different objects:
   ```bash
   git add -- src/main/java/edu/eci/arsw/relicrush/concurrency/LockPair.java
   git commit -m "Prevenir deadlock mediante orden global de estaciones"
   ```
3. `docs/ADR-001-deadlock-prevention.md` records the decision, the rejected
   alternatives, and the stress evidence:
   ```bash
   git add -- docs/ADR-001-deadlock-prevention.md
   git commit -m "Documentar decisión de prevención de deadlock"
   git push -u origin feat/prevencion-deadlock
   ```

PR 2 (`feat/prevencion-deadlock` -> `main`) is reviewed by team member 1 and
vice versa. Both PRs are merged into `main` only after each branch is green.

## 3. Team member 3 (Jhonatan Madero) - integration, evidence, and report

Starts only after PR 1 and PR 2 are merged into `main`.

### Step 1: integration branch

```bash
git switch main
git pull origin main
git switch -c docs/verificacion-integracion
```

### Step 2: integration test commit

`GameEngineInvariantTest.java` runs a full 25-round game, captures console
output, asserts there is no `invariant=BROKEN`, and asserts exactly 25
`invariant=OK` snapshots:

```bash
git add -- src/test/java/edu/eci/arsw/relicrush/GameEngineInvariantTest.java
git commit -m "Agregar prueba integrada de invariantes por ronda"
```

### Step 3: Maven and ignore-file commit

`pom.xml` already targeted Java 21 and JUnit 5, so no change was required
there. `.gitignore`, however, did not exclude `target/` or IDE metadata, and
`target/` build output had been committed by mistake since the initial commit.
Both were corrected together:

```bash
git add -- .gitignore
git commit -m "Configurar dependencias JUnit y archivos ignorados"
```

### Step 4: required stress evidence

```bash
mvn clean test
mvn -q -DskipTests package
java -cp target/classes edu.eci.arsw.relicrush.app.LedgerRaceProbe 64 5000
java -cp target/classes edu.eci.arsw.relicrush.app.DeadlockProbe
java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 8 6 50
java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 32 8 100
java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 128 8 100
```

The 128-player run was repeated three times, as required. Every run reported
0 `BROKEN` rounds. See `docs/REPORT.md`, section 5, for the captured output.

### Step 5: documentation commits

```bash
git add -- docs/REPORT.md
git commit -m "Completar reporte técnico y evidencias de estrés"
git add -- README.md docs/TEAM_WORKFLOW.md
git commit -m "Documentar ejecución y flujo de trabajo del equipo"
git push -u origin docs/verificacion-integracion
```

PR 3 (`docs/verificacion-integracion` -> `main`) is reviewed by team members 1
and 2, who confirm that their own design and evidence are represented
accurately in `docs/REPORT.md`.

## 4. Final submission checklist

After PR 3 is merged, one team member updates `main` and runs the full
verification sequence; the other two observe or repeat it on their own
machines:

```bash
git switch main
git pull origin main
mvn clean test
mvn -q -DskipTests package
java -cp target/classes edu.eci.arsw.relicrush.app.LedgerRaceProbe 64 5000
java -cp target/classes edu.eci.arsw.relicrush.app.DeadlockProbe
java -cp target/classes edu.eci.arsw.relicrush.app.InvariantProbe 128 8 100
git rev-parse HEAD
git status --short
```

The `git rev-parse HEAD` result is copied into `docs/REPORT.md`'s "Final
commit" field, committed as the last field update from team member 3's branch,
and `git status --short` must print nothing (clean working tree). The delivery
is the GitHub repository URL, not a ZIP file, unless the instructor requests an
archive.

## Pull request map

| PR | Base | Compare | Author | Reviewer(s) |
|---|---|---|---|---|
| PR 1 - Ledger | `main` | `feat/thread-safe-ledger` | Team member 1 | Team member 2 |
| PR 2 - Deadlock | `main` | `feat/prevencion-deadlock` | Team member 2 | Team member 1 |
| PR 3 - Integration | `main` | `docs/verificacion-integracion` | Jhonatan Madero (team member 3) | Team members 1 and 2 |

Every PR is integrated with "Create a merge commit" (never "Squash and merge")
so GitHub's contribution graph reflects each student's own commits.
