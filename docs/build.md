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

`createDist` produces a runnable distribution. For the full macOS app
bundle, DMG, and ZIP release artifacts use the reproducible release script:

```bash
./scripts/package/build_release.sh -v 2.19.5-igvx.1
```

See `docs/release.md` (release contract) and `scripts/package/README.md`
(script usage).

## Running from source

Fastest iteration loop for a dev install (replaces the running app's jar):

```bash
./gradlew jar
# then replace /Applications/IGV-X.app/Contents/Java/lib/igv.jar
# with build/libs/igv.jar and relaunch
```

## Notes

- `gradle.properties` is overridden externally for production builds (version, vendor, JDK bundles, signing).
- Project-local data (`Ara_genome_annotate/`) and toolchains (`tools/`) are gitignored — the repo is source-only.
- The Gradle mac-app zip task has an input-tracking quirk (stale `WithJava`
  zip reported UP-TO-DATE, plus `build/IGV-MacApp-dist` accumulates app
  bundles from other `-Pversion` builds). The release script deletes both
  before building; do the same when building manually.
