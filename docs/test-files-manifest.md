# IGV-X Test-File Download Manifest

Reproducible manifest of **public, directly downloadable** genomics files for exercising the IGV-X chromosome-resolution layer with **divergent chromosome naming conventions** (the NPE trigger: e.g. `chrC`/`chrM` with capital C/M vs digit chromosomes, `chloroplast`/`mitochondria` word names, and non-chromosome spike-in contigs).

- All URLs verified **2026-08-10** with `curl -sIL` (HEAD, following redirects) plus small range-GET header probes. Nothing was downloaded; no large binaries were written.
- All URLs require **no authentication** and do not redirect to login.
- Format evidence: bigWig magic `0x888FFC26` and chromosome names were read **remotely from file headers** with `pyBigWig` (no download); BAM/VCF/GFF3 headers were read by decompressing the first gzip/bgzip member of a small byte-range GET.

## Naming-convention matrix (verified from file headers)

| File | Verified chromosome names |
|---|---|
| TAIR10 WGBS bigWigs (GSE151616) | `chr1..chr5` (lowercase `chr`), **`chrC`**, **`chrM`** (capital C/M) |
| Leaf bigWigs (GSE151616) | same + spike-in contigs `lamada`, `pUC19` |
| ENCODE ENCFF367AHL (GRCh38) | `chr1..chr22`, `chrX`, `chrY` (+ `_random` contigs) |
| ENA BAM `Bur_0_bur_PII.bam` | **`Chr1..Chr5`** (capital Chr), `chloroplast`, `mitochondria` |
| Ensembl Plants VCF | numeric `1..5` (+ `Mt`/`Pt`) |
| Ensembl Plants GFF3 | numeric `1..5`, `Mt`, `Pt` |

---

## A. TAIR10 (Arabidopsis thaliana) WGBS methylation bigWigs — the NPE trigger

Source: NCBI GEO **GSE151616** (ems-seq paper: *Efficient and accurate determination of genome-wide DNA methylation patterns in Arabidopsis thaliana with enzymatic methyl sequencing*). `Genome_build: tair10` (per GSM records). 58 samples / 170 bigWig files in the series; the six below are two tissues × three sequence contexts (CG/CHG/CHH). No checksums are published by GEO for these files; integrity can be checked with `bigWigInfo`/pyBigWig (see Verification).

| # | Purpose | URL (direct https) | Content-Length | Content-Type | Checksum |
|---|---|---|---|---|---|
| 1 | Flower WGBS, CG methylation | `https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586697/suppl/GSM4586697_Flower-4-50ng-12PCR-WGBS_CG.bw` | 19,715,073 | not advertised by server; bigWig magic verified | none published |
| 2 | Flower WGBS, CHG methylation | `https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586697/suppl/GSM4586697_Flower-4-50ng-12PCR-WGBS_CHG.bw` | 20,365,737 | not advertised; bigWig magic verified | none published |
| 3 | Flower WGBS, CHH methylation | `https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586697/suppl/GSM4586697_Flower-4-50ng-12PCR-WGBS_CHH.bw` | 90,484,847 | not advertised; bigWig magic verified | none published |
| 4 | Leaf WGBS, CG methylation | `https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586706/suppl/GSM4586706_Leaf_5r1_CG.bw` | 20,736,489 | not advertised; bigWig magic verified | none published |
| 5 | Leaf WGBS, CHG methylation | `https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586706/suppl/GSM4586706_Leaf_5r1_CHG.bw` | 19,380,371 | not advertised; bigWig magic verified | none published |
| 6 | Leaf WGBS, CHH methylation | `https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586706/suppl/GSM4586706_Leaf_5r1_CHH.bw` | 94,004,674 | not advertised; bigWig magic verified | none published |

Verification evidence (2026-08-10):
- HEAD on all six: `HTTP/1.1 200 OK`, `Accept-Ranges: bytes`, `Last-Modified: 2019-06-27 / 2020-05-27`, exact Content-Lengths as above.
- Range GET bytes 0–63 of file #1: `26 fc 8f 88 04 00 ...` = bigWig magic `0x888FFC26`, version 4.
- `pyBigWig.open(url).chroms()` (remote header read, no download):
  - #1: `chr1 30427671, chr2 19698289, chr3 23459830, chr4 18585056, chr5 26975502, chrC 154478, chrM 366924` — **the NPE trigger pattern: `chrC`/`chrM` capitalised while digit chromosomes are lowercase.**
  - #4: same 7 plus spike-ins `lamada 48502`, `pUC19 2686` (great negative-contig test).

## B. Human ENCODE bigWig (chr1..chrY)

Source: ENCODE files API (`https://www.encodeproject.org/search/?type=File&file_format=bigWig&assembly=GRCh38&output_type=signal+of+all+reads&status=released&sort=file_size:asc&format=json`).

| Purpose | URL (direct https) | Content-Length | Content-Type | Checksum |
|---|---|---|---|---|
| GRCh38 CTCF-style signal track (experiment ENCSR534TGM, released) | `https://www.encodeproject.org/files/ENCFF367AHL/@@download/ENCFF367AHL.bigWig` | 4,765,472 | `binary/octet-stream` (S3 object); ENCODE API `file_format=bigWig` | md5 `ccc0b52fbafaf36428b600edb9ff37ac` (from files API) |

Verification evidence:
- URL returns `HTTP/2 307` redirect to `https://encode-public.s3.amazonaws.com/2021/12/31/b8af4772-b1d6-45f2-98db-faeb83373c2e/ENCFF367AHL.bigWig?<signed query>`; final object `HTTP 200`, `Content-Length: 4765472`, `Content-Type: binary/octet-stream`. (HEAD on the signed URL can return 403; a `GET`/range `GET` with `-L` returns `206` — normal for this host.) The unsigned S3 path also serves `200`.
- Range GET via redirect: `HTTP 206`, magic `0x888FFC26` (real bigWig).
- `pyBigWig` remote header: `chr1..chr22`, `chrX` (156,040,895), `chrY` (57,227,415) — 61 contigs total incl. `_random` contigs. chr1..chrY naming confirmed.

## C. Plant BAM with real reads (Arabidopsis thaliana, TAIR10-aligned)

Source: ENA project **ERP000565** (Mott et al., *Multiple reference genomes and transcriptomes of Arabidopsis thaliana*, Nature 2011; 19-genomes / MAGIC founders). Submitted BAM, aligned to TAIR9/TAIR10 reference FASTA.

| Purpose | URL (direct https) | Content-Length | Content-Type | Checksum |
|---|---|---|---|---|
| Bur-0 whole-genome reads aligned to TAIR10 (real reads; IGV-browseable with the .bai below) | `https://ftp.sra.ebi.ac.uk/vol1/run/ERR031/ERR031531/Bur_0_bur_PII.bam` | 2,139,385,657 | not advertised by server | md5 `629891ecac02bb32f7e16d63c63bb243` (ENA `submitted_md5`) |

> **2026-08-10 download note:** the `ftp.sra.ebi.ac.uk` endpoint is flaky for this large file (connection resets around 125 MiB). An alternate mirror that serves the same bytes is `https://ftp.ebi.ac.uk/vol1/run/ERR031/ERR031531/Bur_0_bur_PII.bam` (HTTP 200, `Accept-Ranges: bytes`, same Content-Length 2,139,385,657). Use `curl -L --fail --retry 10 --retry-all-errors -o` from the mirror. **Never resume (`-C -`) a partial whose writer was killed mid-write** — the resume appended full content over a dirty partial in testing and produced a corrupt file (md5 mismatch); always verify md5 after download.

Optional index (same basename, must sit next to the BAM): `http://mtweb.cs.ucl.ac.uk/mus/www/19genomes/tair10.BAM/Bur_0_bur_PII.bam.bai` — HEAD `HTTP 200`, 356,712 bytes. (This host's https uses a self-signed certificate; use http for the .bai or generate locally with `samtools index`.)

Verification evidence:
- HEAD: `HTTP/1.1 200 OK`, `Accept-Ranges: bytes`, `Content-Length: 2139385657`, `Last-Modified: 2018-11-18`.
- Range GET bytes 0–262143: BGZF magic `1f 8b 08 04`; decompressed BAM header contains `@SQ` lines:
  `SN:Chr1 LN:30427671 ... SN:Chr5 LN:26975502, SN:chloroplast LN:154478, SN:mitochondria LN:366924` (AS/SP tag `tair9_chr.fas`). Real reads (Bur-0 libraries).
- Sibling BAMs for other accessions/phase-I libraries are in the same ENA directory (see working notes).

## D. Plant VCF (Arabidopsis thaliana, incl. 1001 Genomes variants)

Source: Ensembl Plants variation (release 63 / Ensembl 116).

| Purpose | URL (direct https) | Content-Length | Content-Type | Checksum |
|---|---|---|---|---|
| TAIR10 variant VCF (bgzip; includes 1001 Genomes Project variants — INFO flag `The 1001 Genomes Project_2016`) | `https://ftp.ebi.ac.uk/ensemblgenomes/pub/plants/release-63/variation/vcf/arabidopsis_thaliana/arabidopsis_thaliana.vcf.gz` | 100,264,455 | `application/x-gzip` | none published (Ensembl CHECKSUMS carries sizes only) |

Verification evidence:
- HEAD: `HTTP/1.1 200 OK`, `Accept-Ranges: bytes`, `Content-Length: 100264455`, `Content-Type: application/x-gzip`, `Last-Modified: 2026-03-06`.
- First bgzip member decompressed: `##fileformat=VCFv4.1`, `##source=ensembl;version=116`, reference = TAIR10; data lines use **numeric** CHROM ids (verified first records: `1	55`, `1	56`, ...).

## E. Plant GFF3 / GTF (Ensembl Plants, TAIR10)

Source: Ensembl Plants (release 63).

| Purpose | URL (direct https) | Content-Length | Content-Type | Checksum |
|---|---|---|---|---|
| TAIR10 gene annotation GFF3 (bgzip) | `https://ftp.ebi.ac.uk/ensemblgenomes/pub/plants/release-63/gff3/arabidopsis_thaliana/Arabidopsis_thaliana.TAIR10.63.gff3.gz` | 9,571,450 | `application/x-gzip` | none published (Ensembl CHECKSUMS carries sizes only) |

Verification evidence:
- HEAD: `HTTP/1.1 200 OK`, `Accept-Ranges: bytes`, `Content-Length: 9571450`, `Content-Type: application/x-gzip`, `Last-Modified: 2026-04-06`.
- First gzip member decompressed: `##gff-version 3`; `##sequence-region 1..5` (30427671..26975502), `Mt 366924`, `Pt 154478`; `#!genome-build TAIR10` (`GCA_000001735.1`). Note: Ensembl seqids are **numeric `1..5` + `Mt`/`Pt`**, not `Chr1..Chr5`/`ChrC`/`ChrM` — itself a useful divergent convention.

---

## Download commands (curl, no auth)

```bash
mkdir -p test-data && cd test-data

# A. TAIR10 WGBS bigWigs (6 files)
curl -L -O https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586697/suppl/GSM4586697_Flower-4-50ng-12PCR-WGBS_CG.bw
curl -L -O https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586697/suppl/GSM4586697_Flower-4-50ng-12PCR-WGBS_CHG.bw
curl -L -O https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586697/suppl/GSM4586697_Flower-4-50ng-12PCR-WGBS_CHH.bw
curl -L -O https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586706/suppl/GSM4586706_Leaf_5r1_CG.bw
curl -L -O https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586706/suppl/GSM4586706_Leaf_5r1_CHG.bw
curl -L -O https://ftp.ncbi.nlm.nih.gov/geo/samples/GSM4586nnn/GSM4586706/suppl/GSM4586706_Leaf_5r1_CHH.bw

# B. Human ENCODE bigWig (4.7 MB)
curl -L -O https://www.encodeproject.org/files/ENCFF367AHL/@@download/ENCFF367AHL.bigWig

# C. Plant BAM (2.1 GB) + optional index
curl -L -O https://ftp.sra.ebi.ac.uk/vol1/run/ERR031/ERR031531/Bur_0_bur_PII.bam
curl -L -O http://mtweb.cs.ucl.ac.uk/mus/www/19genomes/tair10.BAM/Bur_0_bur_PII.bam.bai   # optional

# D. Plant VCF (100 MB)
curl -L -O https://ftp.ebi.ac.uk/ensemblgenomes/pub/plants/release-63/variation/vcf/arabidopsis_thaliana/arabidopsis_thaliana.vcf.gz

# E. Plant GFF3 (9.6 MB)
curl -L -O https://ftp.ebi.ac.uk/ensemblgenomes/pub/plants/release-63/gff3/arabidopsis_thaliana/Arabidopsis_thaliana.TAIR10.63.gff3.gz
```

## Verification (after download)

### bigWig (A, B)
```bash
# header + chromosome names (UCSC kentUtils bigWigInfo, or pyBigWig):
bigWigInfo GSM4586697_Flower-4-50ng-12PCR-WGBS_CG.bw | head -30
python3 -c "import pyBigWig; bw=pyBigWig.open('GSM4586697_Flower-4-50ng-12PCR-WGBS_CG.bw'); print(bw.chroms())"
# expected (A): chr1..chr5, chrC, chrM (leaf adds lamada, pUC19); (B): chr1..chr22, chrX, chrY
# file type: `xxd -l 4 file.bw` -> 26fc8f88
```

### BAM (C)
```bash
samtools quickcheck Bur_0_bur_PII.bam && echo OK
samtools flagstat Bur_0_bur_PII.bam          # read counts / mapped %
samtools view -H Bur_0_bur_PII.bam | grep '^@SQ'   # expected: Chr1..Chr5, chloroplast, mitochondria
samtools view -c Bur_0_bur_PII.bam Chr1:100000-200000   # sanity region count
md5sum Bur_0_bur_PII.bam   # expect 629891ecac02bb32f7e16d63c63bb243
```

### VCF (D)
```bash
gzip -t arabidopsis_thaliana.vcf.gz && echo OK      # bgzip integrity
# tabix needs a .tbi/.csi index; if you want to index it:
#   (bgzip-compressed already) tabix -p vcf arabidopsis_thaliana.vcf.gz
bcftools view -h arabidopsis_thaliana.vcf.gz | head      # header: VCFv4.1, TAIR10
bcftools query -f '%CHROM\t%POS\n' arabidopsis_thaliana.vcf.gz | head   # numeric 1..5 chroms
```

### GFF3 (E)
```bash
gzip -t Arabidopsis_thaliana.TAIR10.63.gff3.gz && echo OK
zgrep '^##sequence-region' Arabidopsis_thaliana.TAIR10.63.gff3.gz   # 1..5, Mt, Pt
# if an indexed GFF is needed: bgzip -c file > file.gz && tabix -p gff file.gz
```

## Notes
- All URLs verified 2026-08-10; sizes/content-types recorded from live HEAD responses. GEO does not send `Content-Type` for `.bw`; format is proven by magic bytes + remote header parse.
- Sibling files for easy expansion: GSE151616 filelist (`https://ftp.ncbi.nlm.nih.gov/geo/series/GSE151nnn/GSE151616/suppl/filelist.txt`) lists 170 bigWigs (flower/leaf, EM vs WGBS, PCR-cycle titrations) plus H3K9me2/H3 ChIP bigWigs (GSM4818168/69); the ENA directory of section C also contains phase-I and other-accession BAMs.
- Working notes: `/tmp/igvx_manifest_work.md` (rejected candidates and full verification evidence).
