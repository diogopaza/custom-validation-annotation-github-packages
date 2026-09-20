# custom-validation-annotation-github-packages

Practice project to learn how to build a small reusable Java library and
distribute it the way companies actually share internal libraries between
teams: as a real remote Maven dependency, authenticated with a token,
instead of copy-pasting code between repositories.

## What this repository is

This repo **is the library itself**: a custom Bean Validation annotation,
`@CPF`, with its own `ConstraintValidator<CPF, String>` implementation,
packaged as a standalone `.jar` and published to **GitHub Packages**
(GitHub's own Maven registry).

A separate demo project (a `Pessoa` entity with a `cpf` field) will consume
this library as a plain Maven dependency — it contains **zero validation
code** of its own, only a `pom.xml` entry pointing here.

## Why GitHub Packages instead of JitPack

JitPack is how most small open-source libraries get distributed: zero
authentication, builds straight from a public GitHub tag. It's simpler, but
it doesn't reflect how most companies share **internal, non-open-source**
libraries.

GitHub Packages requires a **Personal Access Token (PAT)** to resolve a
dependency — even for a public package — which mirrors the real corporate
pattern: a team publishes an internal library, other teams authenticate
with a token to pull it. That authentication step is the actual skill being
practiced here.

## Library contents

```
com.diogopaza.validation
├── CPF.java                     // the annotation (@Target, @Retention, @Constraint)
└── CpfConstraintValidator.java  // implements ConstraintValidator<CPF, String>
```

Usage in a consumer project, once the dependency is added:

```java
public class Pessoa {

    @CPF
    private String cpf;
}
```

## Step-by-step (TDD)

The tests are the acceptance criteria. Each step lists what is **mandatory**:
the step is only done when those tests are green. All tests live in
`src/test/java/com/diogopaza/validation/PessoaCpfValidationTest.java`,
grouped by step in `@Nested` classes. `Pessoa` is a test-scope fixture
(it is never shipped in the jar).

Contract the tests assume:
- annotation `com.diogopaza.validation.CPF`, validator `com.diogopaza.validation.CpfConstraintValidator`
- accepted formats: exactly 11 digits, or the mask `###.###.###-##` (no trimming)
- `null` is valid (Bean Validation convention, use `@NotNull` to forbid it); `""` and blank strings are invalid
- default message: `CPF inválido`

### Step 1 — `pom.xml` + `Pessoa` + POC tests (red)

Delivered: `Pessoa` and the test class. You write the `pom.xml`, with only
what this step needs:

- Coordinates: `groupId` `com.diogopaza.validation`, `artifactId` `cpf-validator`,
  `version` `1.0.0` (the same ones the consumer will use in Step 5).
- Properties: `maven.compiler.release` = `17` and `project.build.sourceEncoding` = `UTF-8`
  (the tests contain accented characters).
- Test dependencies (`<scope>test</scope>`):
  - `org.junit.jupiter:junit-jupiter` `5.10.2` — runs the tests
  - `org.hibernate.validator:hibernate-validator` `8.0.1.Final` — the Bean Validation
    implementation that reads `@CPF` at runtime (it also brings `jakarta.validation-api`
    transitively, so the tests compile)
  - `org.glassfish.expressly:expressly` `5.0.0` — Expression Language implementation
    that Hibernate Validator requires outside a Spring container

No surefire plugin is needed: Maven 3.9.x already ships a version that runs JUnit 5.

Done when: `mvn test-compile` fails **only** because `CPF` and
`CpfConstraintValidator` do not exist yet, and Maven prints no POM `WARNING`.
That failure is the starting point.

Question (1 pt): why is `hibernate-validator` a `test` dependency while
`jakarta.validation-api` is `provided`? What would break for a Spring Boot
consumer if `hibernate-validator` were `compile` in this library?

**Answer:**


### Step 2 — The `@CPF` annotation (you write it)

Write `CPF` with `message`, `groups`, `payload`, the right `@Target`/`@Retention`
and `@Constraint(validatedBy = CpfConstraintValidator.class)`. Create
`CpfConstraintValidator` as a stub (`return true`) so it compiles.

`pom.xml` for this step: add `jakarta.validation:jakarta.validation-api` `3.0.2`
with `<scope>provided</scope>`. Now that `src/main` uses `jakarta.validation`, the
library needs the API at compile time; `provided` keeps it out of the consumers'
dependency tree, since Spring Boot's validation starter already brings it.

Mandatory tests (`EtapaAnotacao`), all green:
- `@Retention(RUNTIME)`
- `@Target` includes `FIELD`
- `@Constraint` points to `CpfConstraintValidator`
- `message()` defaults to `CPF inválido`
- `groups()` and `payload()` exist with empty defaults

Done when: the project compiles and the 5 `EtapaAnotacao` tests pass. The
`EtapaValidador` tests are expected to still fail (the stub accepts everything).

### Step 3 — The validator logic (you write it)

Implement `CpfConstraintValidator` (`ConstraintValidator<CPF, String>`).

Mandatory tests (`EtapaValidador`), all green:
- accepts valid CPFs, with and without mask
- rejects wrong check digits
- rejects all-equal digits (`111.111.111-11` passes the check-digit math, so this rule must be explicit)
- rejects wrong length and wrong format, including partial masks and surrounding spaces
- rejects non-numeric input **without throwing** (no `NumberFormatException`)
- `null` is valid
- the violation points to the `cpf` field with the default message
- a custom `message` on the annotation is honored

Done when: `mvn test` is fully green.

### Step 4 — Publish to GitHub Packages

Follow "Setup steps" below (token, `settings.xml`, `distributionManagement`).

Mandatory: `mvn verify` fully green **before** `mvn deploy`.

Done when: version `1.0.0` shows up in the repository's **Packages** tab on GitHub.

### Step 5 — Consume it from another project

New Spring Boot 3 / Java 17 project with `spring-boot-starter-validation`, its own
copy of `Pessoa`, and the dependency from "Setup steps" step 5 — no validation
code of its own.

Before building, delete `~/.m2/repository/com/diogopaza/validation`:
`mvn deploy` also installs the jar locally, which would hide a broken remote
resolution.

Mandatory tests, in the consumer project:
- the build downloads the artifact from GitHub Packages (see the "Downloading from" line in the log)
- `Pessoa` with an invalid CPF produces a violation; with a valid CPF, none

Done when: both pass without any validator class in the consumer.

## Setup steps (manual — run these yourself)

Token handling stays in your hands; these are documented here as a
checklist, not automated.

### 1. Generate a Personal Access Token (PAT)

GitHub → Settings → Developer settings → Personal access tokens →
Tokens (classic) → Generate new token, with scopes:
- `read:packages` (to consume)
- `write:packages` (to publish from this repo)

### 2. Configure `~/.m2/settings.xml`

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>diogopaza</username>
      <password>YOUR_PAT_HERE</password>
    </server>
  </servers>
</settings>
```

The `<id>` here (`github`) must match the `<id>` used in this library's
`pom.xml` `<distributionManagement>` and in the consumer project's
`<repositories>` block.

### 3. Add `<distributionManagement>` to this library's `pom.xml`

```xml
<distributionManagement>
  <repository>
    <id>github</id>
    <name>GitHub Packages</name>
    <url>https://maven.pkg.github.com/diogopaza/custom-validation-annotation-github-packages</url>
  </repository>
</distributionManagement>
```

### 4. Publish

```bash
mvn deploy
```

### 5. Consume from the other project

In the `Pessoa` demo project's `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/diogopaza/custom-validation-annotation-github-packages</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.diogopaza.validation</groupId>
  <artifactId>cpf-validator</artifactId>
  <version>1.0.0</version>
</dependency>
```

The same PAT/`settings.xml` setup from step 2 is required to resolve the
dependency, even though the package is public.

## Timeline

Small side exercise, scoped to wrap up before the Part 1 (Inheritance/
Strategy/Factory) deadline of 2026-09-24, so it doesn't eat into that
curriculum's remaining time.

| Step | Target date |
|---|---|
| Library code (`@CPF` + `ConstraintValidator`) + unit tests | 2026-09-19 |
| PAT generated, `settings.xml` configured, published via `mvn deploy` | 2026-09-20 |
| Consumer project (`Pessoa`) created and validated against the published dependency | 2026-09-22 |

## Status

- [ ] Step 1 — write `pom.xml`; `Pessoa` + POC tests fail only on the missing `CPF` (red)
- [ ] Step 2 — `@CPF` annotation, `EtapaAnotacao` tests green — due 2026-09-19
- [ ] Step 3 — `CpfConstraintValidator`, `EtapaValidador` tests green — due 2026-09-19
- [ ] PAT generated and `settings.xml` configured — due 2026-09-20
- [ ] Published to GitHub Packages (`mvn deploy`) — due 2026-09-20
- [ ] Consumer project (`Pessoa`) created and depending on this library — due 2026-09-22
- [ ] Final exam (theory + practice) — after Step 5

## Final exam

Do this after Step 5. Answer in this README, under each question (English
preferred, for practice). Grading: theory 5 pts + practice 5 pts, 0-10.

### Theory (5 pts — 0.5 each)

1. Explain `groupId`, `artifactId` and `version`. Where does each one show up in
   the path of the jar under `~/.m2/repository` and in the registry? Is `groupId`
   the same thing as the Java package?
2. Semantic Versioning: when do you bump PATCH, MINOR and MAJOR? Give one example
   of each for this library.
3. The registry already has `1.1.0`, and a project pins `1.0.0`. What happens on a
   fresh clone by a new developer? What can actually make that developer's build fail?
4. Why can't you redeploy the same release version? What is a `-SNAPSHOT` and when
   is it the right choice?
5. Compare the scopes `compile`, `provided` and `test`. Why is `jakarta.validation-api`
   `provided` and `hibernate-validator` `test` here? What breaks for a Spring Boot
   consumer if `hibernate-validator` were `compile` in the library?
6. `mvn deploy` also installs the jar into `~/.m2`. Why does Step 5 ask you to delete
   that folder before building the consumer?
7. Why is `null` valid in a Bean Validation constraint, and how do you reject it?
8. Why must "all digits equal" be an explicit rule in the CPF validator?
9. Why does GitHub Packages require a token even for a public package? Which scopes
   did you use and why? Why must the token never be committed?
10. The Docker Hub analogy (image = artifact, `docker push` = `mvn deploy`): where does
    it stop being accurate?

### Practice (5 pts — 1 each)

1. **Release 1.1.0 with TDD.** Add `boolean allowMask() default true` to `@CPF`: when
   `false`, only 11 raw digits are accepted. Write the failing tests first, then the code,
   bump the version to `1.1.0`, run `mvn verify`, then `mvn deploy`.
   Done when: `1.1.0` appears in the Packages tab and `1.0.0` is still there.
2. **Two versions side by side.** The consumer stays on `1.0.0` and builds fine after
   `1.1.0` is published. Then upgrade the consumer to `1.1.0` and use `allowMask = false`.
   Done when: both builds are green and the diff of the consumer's `pom.xml` is one line.
3. **Break it on purpose (version).** Run `mvn deploy` again with the same `1.1.0`.
   Done when: the error message is pasted here and explained in your own words.
4. **Break it on purpose (access).** Use a wrong or missing token in `settings.xml` and
   build the consumer. Done when: the `401` log is pasted here and you explain how it
   differs from the error in task 3.
5. **Clean-machine proof.** Delete `~/.m2/repository/com/diogopaza` and rebuild the
   consumer, adding `@NotNull` next to `@CPF` on `Pessoa`. Done when: the log shows the
   jar downloaded from GitHub Packages and a test proves `null` is now rejected.
