# IGV-X test datasets

Reproducible compatibility test suite uses real genomics files from multiple organisms with different
chromosome naming conventions. Data is NOT committed to git (large); this doc records how to obtain it.

## Local (Runtian's Arabidopsis data) — input-only, never modified

`/Users/runtianwu/Rdirectory/IGV-X/Ara_genome_annotate/`

| File | Description | Chromosome naming |
|---|---|---|
| `TAIR10_chr.fa` (+ `.fai`) | TAIR10 reference FASTA | `chr1..chr5`, `chrC`, `chrM` (lowercase) |
| `TAIR10.fa` (+ `.fai`) | Same-size TAIR10 FASTA (alternate) | (check headers) |
| `mikado_refine_version2_final_noAS_wCM_chr.gtf` | Gene models | `chr1..chrM` (lowercase) |
| `TAIR10_plus_araport11_nonoverlapping_and_TEs.gtf` | Gene models + TEs | (check) |
| `Default Arabidopsis trakcs.xml` | Session referencing TAIR10.fa | — |

## Public test files (to download)

Planned download set — diverse organisms, different naming conventions:

- **bigWig**: a TAIR10 methylation bigWig with `Chr1..ChrM` headers (the NPE trigger); a human ENCODE bigWig (`chr1..chrY`);
  one with RefSeq accessions (`NC_*`).
- **bigBed**: e.g. `test/data/bb/*.bb` already in repo (`chr21.refseq.bb`, `cytoBandMapped.bb`, etc.).
- **BAM/CRAM**: human + plant examples.
- **VCF**: human + plant examples.
- **GFF/GTF**: from Araport/Ensembl Plants.
- **Sessions**: the repo has `test/sessions/100_bigwigs.xml` (large-session stress test).

See `tests/` (the reproducible compatibility suite) for the exact manifest and downloader.

## Committed fixture manifest (test/data/bb)

Synthetic fixtures generated with pyBigWig (Python 3.13; `validate=False` for addEntries) and committed
for regression tests. Rich multi-interval data (>=200 intervals/chrom) is required so pyBigWig writes
usable zoom reduction levels; sparse fixtures make `zoomLevelForScale` return null at real scales.

| Fixture | Chromosome names | Purpose |
|---|---|---|
| `tair10_chr_case.bigWig` | `Chr1..ChrM` (capital C/M) | Original NPE repro: genome exposes `chr1`/`NC_*` |
| `tair10_refseq.bigWig` | `NC_003070.9` .. `NC_037304.1` | RefSeq-accession file vs canonical genome names |
| `tair10_organellar_case.bigWig` | `Chr1..Chr5`, `ChrC`, `ChrM` | Organellar case-insensitive resolution |
| `GCF_000009045.1_ASM904v1.ncbiGene.bb` | `NC_000964.3` (B. subtilis) | Real-world RefSeq bigBed (from repo) |
| `GCA_009914755.4.chromAlias.bb` | `CP068254.1` etc. | Real chromAlias bigBed (from repo) |
| `chr21.refseq.bb` | `chr21` | Direct-match fixture (from repo) |

Regeneration scripts: `/tmp/make_fixtures.py` (kept out of git; regenerable).
