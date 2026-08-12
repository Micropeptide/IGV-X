# IGV-X

> **A customized version of IGV built specifically for
> [micropeptide](https://github.com/micropeptide)'s research** —
> Arabidopsis epigenetics, whole-genome bisulfite sequencing (WGBS) methylation
> analysis, and large multi-track sessions. Designed around those workflows
> while remaining fully compatible with standard IGV.

**IGV-X** is a robust, modern fork of the [Integrative Genomics Viewer](https://github.com/igvteam/IGV)
— a desktop genome visualization tool for Mac, Windows, and Linux — customized for
large epigenomics sessions.

- **Chromosome-name resolution that never crashes**: fixes the stock IGV 2.19.5
  bigWig NullPointerException on TAIR10 WGBS sessions (hundreds of methylation
  bigWigs with `Chr1..ChrM` vs RefSeq aliases); case-insensitive, alias-aware
  resolution across bigWig/bigBed/BAM/BED/VCF using genome alias info — never
  global lowercase conversion, never renaming user files.
- **Large-session performance** for ~900 bigWig sessions: bounded loading
  threads, async loads, honest progress, viewport culling, caching.
- **Relative session paths** by default, optional `.igvx.json` companion,
  bookmarks & highlights persisted in the session, cancelable session loading.
- **High-quality PNG/SVG/PDF export**, multi-track selection, configurable
  default quantitative ranges, trackpad navigation.
- **Organize tracks by genotype** → CG/CHG/CHH with editable remembered rules.
- **Diagnostics & error recovery**: Diagnose Track/Session, exception
  dedup/rate-limiting, batch command listener (port 60151).
- **In-app updates** from this repository's GitHub releases.
- Modern macOS app (native menu bar, fullscreen, Retina, accessibility),
  bundled JDK, own bundle ID and prefs (`~/igvx`) — safe to run alongside
  stock IGV.

**Downloads & releases:** see [Releases](https://github.com/Micropeptide/IGV-X/releases).
**Full diff vs stock IGV:** [`docs/changes-vs-igv.md`](docs/changes-vs-igv.md) ·
[`docs/whats-different.md`](docs/whats-different.md) · [`docs/fork-diff.md`](docs/fork-diff.md)

The rest of this README is the upstream IGV documentation (fork base: `2.19.X`).

---

# igv

![Build Status](https://github.com/igvteam/igv/actions/workflows/gradle.yml/badge.svg)
![GitHub issues](https://img.shields.io/github/issues/igvteam/igv)
![GitHub closed issues](https://img.shields.io/github/issues-closed/igvteam/igv)
![](https://img.shields.io/npm/l/igv.svg)

Integrative Genomics Viewer - desktop genome visualization tool for Mac, Windows, and Linux.

### Building

These instructions are meant for developers interested in working on the IGV code. For normal use,
we recommend the pre-built releases available
at [http://software.broadinstitute.org/software/igv/download](http://software.broadinstitute.org/software/igv/download).

Builds are executed from the IGV project directory. Files will be created in the 'build' subdirectory.

IGV requires **Java 21** to build and run. Later versions of Java should work but we build and test on **Java 21**.

NOTE: If on a Windows platform use ```./gradlew.bat``` in the instructions below

#### Folder structure and build targets

The IGV bundles ship with embedded JREs from AdoptOpenJDK.

* Install Gradle for your platform. See https://gradle.org/ for details.

* Use ```./gradlew createDist``` to build a distribution directory (found in ```build/IGV-dist```) containing
  the igv.jar and its required runtime third-party dependencies as well as helper scripts for launching.

    * Launch IGV with `igv.sh` or `igv_hidpi.sh` on Linux, `igv.command` on Mac, and `igv.bat` on Windows.

    * To run igvtools from the command line use the script `igvtools` on Linux and Mac, or igvtools.bat
      on Windows. See the instructions in igvtools_readme.txt in that directory.

    * The launcher scripts expect this folder structure in order to run IGV.

* Use ```./gradlew test``` to run the test suite. See 'src/test/README.txt' for more information about running
  the tests.

* See this [README](https://raw.githubusercontent.com/igvteam/igv/master/scripts/readme.txt) for tips about using the
  IGV launcher scripts.

* This dashboard describes [project structure and dependencies](https://sourcespy.com/github/igvteamigv/).

Note that Gradle creates a number of other subdirectories in 'build'. These can be safely ignored.

#### Amazon Web Services support

Public data files hosted in Amazon S3 buckets can be loaded into IGV
using [https endpoints](https://docs.aws.amazon.com/AmazonS3/latest/dev/UsingBucket.html).

Authenticated access using s3:// urls is supported by either (1) enabling OAuth access with Cognito using the UMCCR
contributed AWS configuration option, or (2) setting AWS credentials and region information as described
[here]( https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html) and
[here](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-region-selection.html).

For more details on using Cognito for OAuth access, see
the [UMCCR documentation on the backend](https://umccr.org/blog/igv-amazon-backend-setup/)
and [frontend for a provisioning URL step by step guide](https://umccr.org/blog/igv-amazon-frontend-setup/).


 
