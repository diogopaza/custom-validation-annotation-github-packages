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
`jakarta.validation-api` (which you will add in Step 2) is `provided`? What would
break for a Spring Boot consumer if `hibernate-validator` were `compile` in this
library?

**Answer:** `hibernate-validator` uses the `test` scope because it is only used in the
library's tests, so the `test` scope does not pull its dependencies into the consumer.
`jakarta.validation-api` is `provided` because our consumers (Spring) already have that
library. If `hibernate-validator` were `compile`, it would pull in dependencies that
Spring already has, so we could get version conflicts, since the consumer already has
those dependencies.


**Step 1 result — 10/10**

| Criterion | Score |
|---|---|
| Mandatory: `mvn test-compile` fails only on the missing `CPF`/`CpfConstraintValidator`, no POM warning | 5/5 |
| Contract: coordinates, compiler release, scopes, `groupId` | 2/2 |
| Clarity: lean `pom.xml`, no duplication or stale comments | 2/2 |
| Question: dependency scopes | 1/1 |

What was done:
- Wrote the `pom.xml` from scratch: coordinates `com.diogopaza.validation:cpf-validator:1.0.0`,
  `maven.compiler.release` 17, UTF-8 encoding, and the three test dependencies.
- Fixed the review points one by one: `junit-jupiter` instead of `junit-jupiter-api`
  (the tests need `junit-jupiter-params`), `test` scope instead of `compile` for
  `hibernate-validator` and `expressly`, and `org.hibernate.validator` as the
  current `groupId` (the old one only worked through a relocation).
- Found by reading the logs that Maven was running on Java 8 (`JAVA_HOME` pointed to it),
  which silently ignored `release 17`, and that `RELEASE` pseudo-versions pulled JUnit 6
  and made the build non-reproducible. Removed the duplicated `RELEASE` blocks.
- Created a `usejdk.bat` script to switch between JDK 8 (Play, Educabiz) and JDK 17
  per terminal, without touching the global `JAVA_HOME`.
- Answered the question: `hibernate-validator` is `test` so it is not passed on to
  consumers; `jakarta.validation-api` is `provided` because Spring already brings it;
  `compile` would cause version conflicts.

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

Question (1 pt): why does `@CPF` need `@Retention(RetentionPolicy.RUNTIME)`? What
would happen to the validation if it were `SOURCE` or `CLASS`, and who reads the
annotation at runtime?

**Answer:** `RUNTIME` makes the JVM keep `@CPF` in memory when it loads the class, so it
can be read through reflection. The one that reads it is the Bean Validation provider
(Hibernate Validator in the tests, also in Spring Boot): when `validator.validate(...)`
runs, it scans the fields with reflection, finds annotations marked with `@Constraint`,
and calls the `ConstraintValidator` declared in `validatedBy`. With `SOURCE`, the
compiler discards the annotation and it never reaches the `.class`. With `CLASS`, it is
written to the `.class` but the JVM does not expose it at runtime. In both cases nothing
breaks and no exception is thrown: the provider simply does not see `@CPF`, the
validator is never called, and every CPF is accepted. It is a silent failure, which is
why the `retentionRuntime` test exists.

Choosing a retention policy, by who reads the annotation:

| Retention | Where it exists | Who reads it | Examples |
|---|---|---|---|
| `SOURCE` | `.java` only | The compiler and annotation processors | `@Override`, `@SuppressWarnings`, Lombok's `@Getter` |
| `CLASS` | `.java` and `.class`, not loaded by the JVM | Bytecode tools that read the `.class` as a file | `@SuppressFBWarnings` (SpotBugs), `@Keep` (Android R8/ProGuard), JetBrains `@NotNull` |
| `RUNTIME` | `.class` and JVM memory | Running frameworks, through reflection | `@CPF`, `@Entity`, `@Autowired`, `@Test` |

`CLASS` is the default when `@Retention` is omitted, so forgetting it on `@CPF` would
compile fine and silently disable the validation.

**Step 2 result — 9.5/10**

| Criterion | Score |
|---|---|
| Mandatory: project compiles, 5 `EtapaAnotacao` tests green, `EtapaValidador` still red | 5/5 |
| Contract: package `com.diogopaza.validation`, test file and `Pessoa` untouched | 2/2 |
| Clarity: lean `pom.xml`, `@Override`, comments that match the code | 2/2 |
| Question: retention policies | 0.5/1 |

What was done:
- Wrote `@CPF` (`@Retention(RUNTIME)`, `@Target(FIELD)`, `@Constraint`, `message`,
  `groups`, `payload`) and the `CpfConstraintValidator` stub, and added
  `jakarta.validation-api` `3.0.2` as `provided`.
- Fixed the review points one by one: moved both classes from an `annotation` subpackage
  to the contract package, restored `@CPF` on `Pessoa`, and reverted the edits to the test
  file, including an `EtapaValidador` that had been commented out to make the build green.
  Red `EtapaValidador` tests are the expected state in this step.
- Removed the redundant `maven.compiler.source`/`target` (`release` already covers them).
- Traced the IntelliJ error "`@Override` is not allowed when implementing interface method"
  to a stale IDE import with language level `JDK_1_5`. The code was correct and Maven
  compiled it with `release 17`. Removed a leftover non-Maven `.iml` module and its `out/`
  folder.
- Question: correctly explained that `SOURCE` is discarded by the compiler and `CLASS`
  does not reach runtime. Who reads the annotation (the Bean Validation provider, through
  reflection) and the consequence (a silent failure, not an exception) were given in review.

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

**Step 3 result — 9/10**

| Criterion | Score |
|---|---|
| Mandatory: `mvn clean test` fully green (36/36: 30 `EtapaValidador`, 5 `EtapaAnotacao`, 1 own test) | 5/5 |
| Contract: contract test file untouched, own tests in a separate class | 2/2 |
| Clarity and design | 2/3 |

What was done:
- Wrote the check-digit algorithm by hand (weights 10→2 and 11→2, mod 11, remainder
  `< 2` becomes `0`) and the `DigitosVerificadoresCpf` record, which gets `equals` for
  free and makes the result easy to assert.
- Built `isValid` in the order null → format → normalize → rules, so the strict format
  regex blocks letters and partial masks before any `parseInt` runs (no
  `NumberFormatException`). The format/normalize helpers, the two regexes and the
  digit comparison were provided in review on request.
- Fixed the review points one by one: the `null` check moved before `formatoValido`
  (it threw `NullPointerException`), the inverted `!todosDigitosIguais` that rejected
  every valid CPF, and the digits read from the masked input instead of the normalized one.
- Removed the duplicated loops by extracting `calculaUmDigitoVerificador`, deleted the
  commented-out code, and moved the own test out of the contract file into
  `DigitosVerificadorCpfTest`.

Still open in clarity (-1):
- `calculaUmDigitoVerificador` uses a `null` `String` as a mode flag, with a ternary,
  an `if` and `int`↔`String` conversions. Passing the digits to weigh (9, then 10) plus
  the initial weight removes all three.
- 1-based loop with `charAt(i - 1)`, and `String.valueOf` + `parseInt` instead of
  `Character.getNumericValue`.
- `isValid` names (`s`, `constraintValidatorContext`, `novoCpf`), the all-equal check
  after the calculation instead of before it, and `if (...) return false; return true;`
  instead of a direct `return`.
- `DigitosVerificadorCpfTest` does not match the record name and has a single case
  (`123456789`, which exercises the `< 2` rule, is missing).

### Step 4 — Publish to GitHub Packages

Follow "Setup steps" below (token, `settings.xml`, `distributionManagement`).

Mandatory: `mvn verify` fully green **before** `mvn deploy`.

Done when: version `1.0.0` shows up in the repository's **Packages** tab on GitHub.

Question (1 pt): why does the token live in your user's `settings.xml` and not in the
project's `pom.xml`? And what does the `<id>` repeated in both files do?

**Answer:** The token lives in `settings.xml` because that file is never published to
GitHub, so its value stays protected. `settings.xml` also centralizes the credentials for
every external repository on the machine; otherwise the token would have to be repeated in
each project. In `settings.xml`, the `<id>` names a server and holds the username and the
token used to access GitHub Packages; in `pom.xml`, the same `<id>` in
`<distributionManagement>` points to that server. Credentials are also personal while the
pom is shared: each developer and the CI pipeline keep their own token in their own
`settings.xml`, and the pom stays the same for everyone.

**Step 4 result — 10/10**

| Criterion | Score |
|---|---|
| Mandatory: `mvn verify` green before `mvn deploy`, `1.0.0` in the Packages tab | 5/5 |
| Setup: token in an environment variable, same `<id>` in both files, `distributionManagement` as a direct child of `<project>` | 2/2 |
| Understanding: each tag (`id`, `name`, `url`) and the role of `settings.xml` explained | 2/2 |
| Question: token location and the `<id>` link | 1/1 |

What was done:
- Generated a classic PAT with only `write:packages` (least privilege), 90-day expiration,
  stored in the `GITHUB_TOKEN` user environment variable.
- Created `~/.m2/settings.xml` with a `github` server using `${env.GITHUB_TOKEN}`, so the
  token is not written in any file, and checked it with `mvn help:effective-settings`.
- Added `<distributionManagement>` to the pom; it was first placed inside `<dependencies>`
  and moved out before running.
- `mvn deploy` ran the full lifecycle (test, package, install, deploy) and uploaded the
  `.pom`, the `.jar` and `maven-metadata.xml`; `cpf-validator 1.0.0` shows up in Packages.
- Learned that the token is tied to the account, not to the package, and is separate from
  the Git credentials; the package inherits the public visibility of the repository, so any
  GitHub account with `read:packages` can download it, but only the owner can publish.

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

### Step 6 — A second library on Nexus (two registries, one consumer)

GitHub Packages is one registry option. Large companies usually run a repository manager
(Sonatype Nexus or JFrog Artifactory) that also proxies Maven Central. In this step you run
Nexus locally in Docker, publish a second library there, and make the Step 5 consumer
resolve one dependency from GitHub Packages and the other from Nexus.

The second library is `com.diogopaza.validation:cnpj-validator:1.0.0`: a `@CNPJ` annotation
and its `ConstraintValidator`, in its own repository, built with the same TDD flow as Steps 1–3
(contract tests are delivered when the step starts). Only the URL and the credentials change
between registries. The `settings.xml` server `id`, `distributionManagement`, versioning and
scopes work the same way.

**6.1 Nexus in Docker**
- Run `sonatype/nexus3` on port `8081` with a named volume for `/nexus-data`.
- Read the initial admin password from `/nexus-data/admin.password` inside the container,
  change it in the setup wizard, and disable anonymous access.
- Find the default repositories: `maven-releases` (hosted), `maven-snapshots` (hosted),
  `maven-central` (proxy) and `maven-public` (group).

**6.2 Publish `cnpj-validator` to Nexus**
- New `<server>` with id `nexus` in `~/.m2/settings.xml`.
- `<distributionManagement>` with a `<repository>` pointing to `maven-releases` and a
  `<snapshotRepository>` pointing to `maven-snapshots`.
- `mvn verify` fully green, then `mvn deploy`.

**6.3 Snapshots vs releases**
- Deploy `1.0.1-SNAPSHOT` twice and compare the file names in `maven-snapshots`.
- Deploy `1.0.0` again to `maven-releases` and read the error.

**6.4 One consumer, two registries**
- In the Step 5 consumer, declare both repositories (`github` and `nexus`) and depend on
  `cpf-validator` and `cnpj-validator`. Add an `Empresa` class with a `@CNPJ` field.
- Delete `~/.m2/repository/com/diogopaza` before building.

Mandatory tests:
- Nexus UI shows `cnpj-validator` `1.0.0` in `maven-releases`.
- The consumer log shows `cpf-validator` downloaded from `github` and `cnpj-validator`
  downloaded from `nexus`.
- In the consumer: `Pessoa` with an invalid CPF and `Empresa` with an invalid CNPJ each
  produce one violation; valid values produce none.
- Redeploying `1.0.1-SNAPSHOT` succeeds; redeploying `1.0.0` to `maven-releases` is rejected.
  Paste both outputs here and explain the difference.
- Stop the Nexus container, delete `~/.m2/repository/com/diogopaza` again and rebuild the
  consumer. Paste the error and explain why only `cnpj-validator` fails.

Theory (1 pt each):
1. What are hosted, proxy and group repositories in Nexus? Which one should a consumer
   point to, and why?
2. The consumer declares two `<repository>` entries. How does Maven decide where to look for
   each dependency, in what order, and what does it cost when an artifact is not in the first one?
3. In `settings.xml`, `<mirrorOf>*</mirrorOf>` vs `<mirrorOf>central</mirrorOf>` pointing to
   `maven-public`: what breaks in this project with `*`, and why?
4. Why does Nexus accept redeploying a `-SNAPSHOT` but reject a release? What is the timestamp
   in the snapshot file name for?
5. GitHub Packages vs Nexus: when would a company choose each one? Mention proxying Maven
   Central, offline builds, access control and cost.

Done when: all mandatory tests pass and the five answers are written here.

### Step 7 — A Spring Boot starter with `@AutoConfiguration`

Libraries like `spring-boot-starter-data-jpa` create beans by themselves: you add the
dependency and a `DataSource` shows up without a single `@Bean` in your code. In this step
you build that mechanism by hand, in a third library, and watch a bean appear in the
Step 5 consumer with nothing but a new `<dependency>`.

The library is `com.diogopaza.validation:cpf-formatter-spring-boot-starter:1.0.0`, in its own
repository, published to GitHub Packages like `cpf-validator`. It formats a CPF:
`formatar("52998224725")` returns `"529.982.247-25"`. The consumer adds the dependency and
nothing else: no `application.properties` entry, no `@Bean`, no `@ComponentScan`.

Use fictitious CPFs only (like the ones in the contract tests). The repository is public.

**The three pieces**
- `CpfFormatter`: a plain Java class with the formatting logic, no Spring annotations.
- `CpfFormatterAutoConfiguration`: annotated with `@AutoConfiguration`, declares the
  `CpfFormatter` `@Bean` with `@ConditionalOnMissingBean`.
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
  one line with the fully qualified name of the auto-configuration class.

`pom.xml`: depend on `spring-boot-autoconfigure` (not on a full starter), and keep the
library free of anything the consumer already brings. Decide the scope and explain it.

Mandatory tests, in the starter (with `ApplicationContextRunner`):
- with no user configuration, the context contains exactly one `CpfFormatter`.
- when the user declares their own `CpfFormatter` bean, the user's bean wins and the
  auto-configured one is not created.
- break it on purpose: remove the `.imports` entry, show that a consumer no longer gets the
  bean, paste the error and put the entry back.

Mandatory tests, unit:
- 11 digits are formatted with the mask.
- wrong length, letters, `null` and an input that already has the mask each get a defined
  behavior (exception or return value, your choice), and every case is tested.

Mandatory test, in the Step 5 consumer:
- a service receives `CpfFormatter` by constructor injection, with no bean declared in the
  consumer, and a `@SpringBootTest` proves it formats a CPF.

Theory (1 pt each):
1. How does Spring Boot find auto-configuration classes inside third-party jars? Why doesn't
   it just scan their packages?
2. What happens if the `.imports` file is missing or has a typo? Why is this failure silent?
3. Why is `@ConditionalOnMissingBean` essential in a library? What would happen to a consumer
   who needs a different `CpfFormatter` without it?
4. Why does `cpf-validator` (Bean Validation) not need any auto-configuration, while
   `CpfFormatter` does? What does that say about which libraries should depend on Spring?
5. `@AutoConfiguration` and the `@Conditional...` annotations are read at runtime. Which
   retention do they need, and who reads them? Connect it with the Step 2 answer about `@CPF`.

Done when: all mandatory tests pass, `1.0.0` shows up in the starter's Packages tab, and the
five answers are written here.

### Step 8 — Configure the starter from `application.properties`

Step 7 works with zero configuration. Real starters also let the consumer change their
behavior without writing code: `spring.datasource.url`, `server.port`, and so on. In this
step the consumer tunes `CpfFormatter` from `application.properties`, and Step 7 behavior
stays the default when nothing is configured.

New version: `cpf-formatter-spring-boot-starter:1.1.0` (a new feature that doesn't break
anyone, so a minor bump). Use fictitious CPFs only.

**The properties** (prefix `cpf.formatter`)

| Property | Type | Default | Effect |
|---|---|---|---|
| `cpf.formatter.enabled` | boolean | `true` | `false` means no `CpfFormatter` bean is created at all |
| `cpf.formatter.hide-digits` | boolean | `false` | `true` hides the first 3 and the last 2 digits: `"52998224725"` → `"***.982.247-**"` (logs, screens, LGPD) |

**The pieces**
- `CpfFormatterProperties`: a class annotated with `@ConfigurationProperties(prefix = "cpf.formatter")`,
  holding the two fields with their defaults. Decide between a JavaBean with getters/setters
  and a `record`, and explain the choice.
- `CpfFormatterAutoConfiguration`: enables the properties class
  (`@EnableConfigurationProperties`), uses `@ConditionalOnProperty` for `enabled` (think about
  `matchIfMissing`), and passes the properties to `CpfFormatter`.
- `CpfFormatter` stays a plain Java class with no Spring import. It receives what it needs
  through the constructor, not the properties class (think about why).
- `spring-boot-configuration-processor` in the `pom.xml`, so the IDE autocompletes
  `cpf.formatter.*` in the consumer. Decide the scope (`optional`? `provided`?) and explain it.

Mandatory tests, in the starter (with `ApplicationContextRunner.withPropertyValues`):
- no properties: same behavior as Step 7 (bean present, full digits).
- `cpf.formatter.enabled=false`: no `CpfFormatter` bean in the context.
- `cpf.formatter.hide-digits=true`: the bean returns the hidden format.
- an invalid value (`cpf.formatter.hide-digits=talvez`): the context fails to start. Paste
  the error and explain who rejected it.
- the user's own `CpfFormatter` bean still wins over the auto-configured one, even with
  properties set.

Mandatory tests, unit:
- hidden format for 11 digits, and the same input-validation cases from Step 7 with
  `hide-digits` on.

Mandatory tests, in the Step 5 consumer:
- an `application.properties` with `cpf.formatter.hide-digits=true` and a `@SpringBootTest`
  proving the injected bean hides the digits.
- a second test class overriding it with `@SpringBootTest(properties = "cpf.formatter.hide-digits=false")`.
  Explain which source wins and why.

Theory (1 pt each):
1. How does Spring Boot bind `cpf.formatter.hide-digits` (kebab-case) to a field named
   `hideDigits`? Would `CPF_FORMATTER_HIDEDIGITS` as an environment variable also work?
   What is this called?
2. Why does `CpfFormatter` receive plain values instead of `CpfFormatterProperties`? What would
   it cost to couple it to the properties class?
3. `@ConditionalOnProperty` vs. `@ConditionalOnMissingBean`: which one is evaluated against
   what? Can both be on the same `@Bean`, and in what order do they matter?
4. What does `spring-boot-configuration-processor` generate, where does that file end up inside
   the jar, and why doesn't the application need it at runtime?
5. Precedence: list from strongest to weakest a command-line argument, an environment
   variable, the consumer's `application.properties` and the default in the properties class.
   Why is this order useful in production (12-Factor, config in the environment)?

Done when: all mandatory tests pass, `1.1.0` shows up in the starter's Packages tab, the
consumer upgrades to it without changing any code, and the five answers are written here.

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

- [x] Step 1 — write `pom.xml`; `Pessoa` + POC tests fail only on the missing `CPF` (red) — graded 10/10
- [x] Step 2 — `@CPF` annotation, `EtapaAnotacao` tests green — graded 9.5/10
- [x] Step 3 — `CpfConstraintValidator`, `EtapaValidador` tests green — graded 9/10
- [x] PAT generated and `settings.xml` configured
- [x] Step 4 — published to GitHub Packages (`mvn deploy`) — graded 10/10
- [ ] Consumer project (`Pessoa`) created and depending on this library — due 2026-09-22
- [ ] Final exam (theory + practice) — after Step 5
- [ ] Step 6 — Nexus in Docker, `cnpj-validator` published there, consumer resolving from two registries
- [ ] Step 7 — `cpf-formatter-spring-boot-starter` with `@AutoConfiguration`, bean injected in the consumer with no configuration
- [ ] Step 8 — starter configurable from `application.properties` (`@ConfigurationProperties`), published as `1.1.0`

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
