# IGV-X Documentation

IGV-X is a customized fork of the Integrative Genomics Viewer
(https://github.com/igvteam/IGV), built for robustness, modern macOS UX,
and safe upstream tracking. This page is the entry point to the IGV-X
project docs.

## Project docs

- **[architecture.md](architecture.md)** — fork base, branch model, vendored toolchain, module map, feature→module status.
- **[chromosome-resolution.md](chromosome-resolution.md)** — the chromosome-name resolution layer: problem, design, per-file-type paths, the bigWig NPE fix, verification.
- **[sessions.md](sessions.md)** — session-file design: relative paths, `.igvx.json` companion, backward compatibility.
- **[tests.md](tests.md)** — test layers, regression suite, how to run, real-file verification.
- **[test-files-manifest.md](test-files-manifest.md)** — reproducible public test-file manifest (URLs, sizes, chromosome names).
- **[upstream-update.md](upstream-update.md)** — the mandatory safe upstream merge workflow.
- **[release.md](release.md)** — macOS packaging, versioning, signing/notarization, checksums, release checklist.
- **[limitations.md](limitations.md)** — honest current limitations.
- **[fork-diff.md](fork-diff.md)** — machine-readable patch list vs upstream.
- **[whats-different.md](whats-different.md)** — user-facing "what's different in IGV-X?".
- **[build.md](build.md)** — building and running from source.
- **[test-datasets.md](test-datasets.md)** — how test datasets are used.

## User docs (upstream)

For normal IGV usage documentation, visit [igv.org](https://igv.org/doc/desktop/).

## Web example pages (upstream, static)

* [IGV Desktop Links](web/StaticLinkExamples.html)
* [Track hub example - dynamic links](web/TrackHubsDynamic.html)
* [Track hub example - static links](web/TrackHubsStatic.html)
