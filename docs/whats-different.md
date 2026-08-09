# What's different in IGV-X?

IGV-X is a customized fork of the [Integrative Genomics Viewer (IGV)](https://github.com/igvteam/IGV)
built for robust genome-browser work in the lab. It stays compatible with standard IGV sessions and
standard genomics file formats, and coexists with an existing IGV install (separate app name, bundle ID,
preferences, caches, and logs).

## Highlights (as they land)

### Chromosome-name resolution (top priority)
- Robust, alias-aware resolution of chromosome names across file types: `Chr1` / `chr1` / `CHR1` / `1` /
  RefSeq accessions (`NC_003070.9`) / organellar names (`ChrC`, `ChrM`), case-insensitive where appropriate,
  driven by genome alias info — never a global lowercase conversion.
- **Fixed**: upstream IGV 2.19.5 crashes with a NullPointerException when a bigWig track's chromosome names
  (e.g. `Chr1..ChrM`) don't exactly match the genome's canonical names (e.g. `NC_003070.9`/`chr1` on tair10).
  IGV-X resolves the mapping centrally and never crashes on a missing mapping.

_(More features documented as they land — large sessions, relative paths, bookmarks, trackpad navigation,
multi-track selection, export, diagnostics, packaging.)_
