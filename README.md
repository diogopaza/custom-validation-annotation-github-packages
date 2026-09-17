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

- [ ] Library code (`@CPF` + `ConstraintValidator`) — due 2026-09-19
- [ ] Unit tests for the validator — due 2026-09-19
- [ ] PAT generated and `settings.xml` configured — due 2026-09-20
- [ ] Published to GitHub Packages (`mvn deploy`) — due 2026-09-20
- [ ] Consumer project (`Pessoa`) created and depending on this library — due 2026-09-22
