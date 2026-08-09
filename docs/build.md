# Building IGV-X

## Prerequisites

- **JDK 21** (IGV requires Java 21). System Java on this machine is 11, so a JDK 21 is vendored
  under `tools/jdk-21.0.12+8/` (Temurin, arm64).
- **Gradle** — via the wrapper (`./gradlew`); no system Gradle needed.

## One-time setup

```bash
cd /Users/runtianwu/Rdirectory/IGV-X
export JAVA_HOME=/Users/runtianwu/Rdirectory/IGV-X/tools/jdk-21.0.12+8/Contents/Home
export GRADLE_USER_HOME=/Users/runtianwu/Rdirectory/IGV-X/.gradle-home   # keep Gradle cache inside the project
```

## Build

```bash
./gradlew compileJava        # compile main sources only (fast)
./gradlew test               # run the test suite
./gradlew createDist         # build/IGV-dist: igv.jar + launcher scripts
```

`createDist` produces a runnable distribution. For a macOS .app bundle use `jpackage` from the JDK
(see `docs/package.md` when it lands).

## Notes

- `gradle.properties` is overridden externally for production builds (version, vendor, JDK bundles, signing).
- Project-local data (`Ara_genome_annotate/`) and toolchains (`tools/`) are gitignored — the repo is source-only.
