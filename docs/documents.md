# Temporary chat documents

File analysis extends the existing React → Nginx → Spring Boot → DeepSeek chat.
It does not create another assistant or index uploads into `erro_knowledge`.

## Using attachments

Choose **Attach files** or drop files onto the chat. Supported extensions are
PDF, PNG, JPG/JPEG, WebP, DOCX/DOC, XLSX/XLS, CSV, TSV, TXT and MD. Upload up to
8 files, 20 MiB each, and 60 MiB per active problem. Each file shows uploading,
queued, processing, ready or an actionable error. Remove failed files and attach
corrected copies to retry. Files must finish processing before a selected file
can be used in a question; uncheck files to leave them out of that question.
If a processing workspace expires, polling stops and the files show the expiry
error. Removing an expired file clears that expired workspace so files can be
attached again.

Ask about quantities, panel schedules, cable lists, floor plans, equipment or
lightning protection. Follow-ups reuse the extracted evidence; the browser does
not upload the originals again. Selected filenames accompany each user message.
Answers have a separate **Document sources** section with file and page, table,
sheet/row or image-region locations. Ordinary knowledge citations and engineering
details remain available.

Use **New problem** for a different project. Checking several files makes them
available for comparison, not automatic addition of their quantities. Project
names/revisions and duplicate equipment still need reconciliation. Explicit
“New problem”/“uus probleem” messages also discard the document workspace. While
a document workspace is active, changes between related equipment categories do
not automatically discard the project's files. About/contact navigation retains
page memory. Refresh, tab close and New problem discard browser references; no
cookies, local storage, session storage, accounts or saved chats are added.

## Processing and provider support

Verified against the official [DeepSeek Vision guide](https://api-docs.deepseek.com/guides/vision/)
and [Responses API guide](https://api-docs.deepseek.com/guides/responses_api/) on
2026-10-26: `deepseek-flash` accepts image input. The existing `AiService` sends
`input` user-message content with `input_text` and `input_image`, an inline PNG
base64 data URL, and `detail: original`. Visual extraction requests JSON output
and up to 8,192 output tokens. It reuses `AI_ENDPOINT`, `AI_API_KEY`, reasoning
and timeout settings, with a separate `DOCUMENT_VISION_MODEL`. It sets
`store: false`, supplies no tools or conversation IDs, and does not use provider
Files APIs or external image URLs. Provider-side retention/caching policies are
outside this application's control; `store: false` is not a retention guarantee.

- PDFBox 3.0.8 extracts readable text with positional spacing, page provenance
  and bounded sections. Recognizable quantity-table columns become structured
  rows. It does not claim every arbitrary PDF table is parsed correctly.
- Page inspection detects large raster images, text-poor vector drawings and
  drawing-like pages. Those pages use vision; mixed pages retain direct text as
  well. This is a heuristic, not a completeness detector. Sparse text, unusual
  fonts, small embedded drawings or uncommon layouts can require a clearer export.
- PDFs render at 200 DPI into overlapping tiles, without allocating an entire
  large page bitmap. Each 1,400-pixel core has up to 160 pixels of surrounding
  context. Images retain their input resolution. Vision returns item centers;
  only the tile owning the center retains that item. Coordinates are checked and
  out-of-core items discarded. Source regions retain page coordinates in rendered
  pixels. Border localization and visual recognition remain fallible.
  Tile graphics explicitly use a white background: PDFBox clears the page using
  that background independently of the drawing color. Filling a tile white
  alone does not prevent black-on-black output. See the
  [PDFBox renderer](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/rendering/PDFRenderer.java#L446).
- POI 5.5.1 reads Word paragraphs/tables and Excel sheets/rows, preserving labels,
  units and quantities. Legacy DOC yields less structured text. Word pagination
  is unavailable; locations use paragraphs/tables. Embedded Word drawings need a
  separate PDF/image export. Spreadsheet formulas use cached results only, which
  can be stale; macros and external links are never executed. Hidden rows/sheets
  are included, so revisions and subtotals require review.
- Commons CSV 1.14.1 handles quoted records and embedded newlines, with comma,
  semicolon or tab delimiters. Text input must be UTF-8. Binary content under a
  text extension is rejected. TwelveMonkeys ImageIO 3.12.0 supplies WebP decoding.
  Images are treated as one drawing; animation is not analyzed.

No AI extraction call is made for reliably readable text-only documents. Visual
extraction records printed OCR quantities, inferred symbol counts, estimates and
unknowns separately. A breaker label such as C16 is not a quantity. Malformed,
truncated or unlocated model output fails the file rather than publishing it as a
complete takeoff. A failed file publishes no partial extraction.

The visual-extraction warning is an uncertainty notice and can appear after
successful processing. The PDF text/table-layout notice appears only when PDF
text was extracted; an entirely visual PDF should not show that text notice.

## Retrieval, electrical checks and counting

`DocumentStore` holds only extracted evidence and processing metadata for the
active problem. Exact-byte duplicate uploads in a workspace reuse an existing
file ID. `DocumentContextService` ranks excerpts using the active user context,
filename/location and terminology. It sends at most 24 excerpts within 24,000
serialized characters, alongside the existing knowledge context. Partial
retrieval is explicitly flagged; it cannot substantiate an exhaustive materials
list. Select a subset of files or narrow the question when this limit applies.
No document embeddings or permanent knowledge ingestion are introduced.

Document terms feed the existing knowledge search. Up to six direct excerpts
also reuse parameter extraction, deterministic electrical calculations,
validation and exact-part catalog retrieval separately, preserving their file
and location. Values from different circuits are not collapsed into one set of
ratings. These scoped checks appear with existing electrical checks. Catalog
lookup uses an explicit part number; an ambiguous match cannot supply a device's
DIN width. Existing heuristic extraction supports a limited set of phrases and
parameters, not every electrical notation.

Deterministic arithmetic sums explicit structured quantities **per file**.
Repeated references/identical rows or subtotal markers withhold totals pending
reconciliation. Missing or unreadable quantity cells remain unknown rows and
withhold quantity/DIN totals for that file, so they cannot silently disappear
from a total. It does not add schedule counts to drawing counts or aggregate
projects. Vision-derived values never enter these exact sums. Direct PDF text
and vision on the same page are explicitly identified as overlapping evidence.
Cross-document identity, revisions and connections are explained by the model
and need review; there is no claim of perfect automatic project matching.

DIN arithmetic uses explicit module widths, preferring a unique exact catalog
part's `additionalAttributes.dinModules` when available. This attribute must be
supported by the product's already required manufacturer source document.
Catalog lookup is bounded to assessed excerpts; an unassessed/missing width
withholds the total. A disagreement with a project width is reported. No module
width is inferred from poles or current. Quantities must have explicit piece
units (`tk`, `pcs`, etc.). Results exclude spare space, terminals, ventilation
and installation allowances unless separately specified. Unverified printed
widths remain project assertions, not manufacturer certification.

The answer model receives `documentData` in the existing chat envelope, along
with `referenceData`. Both are untrusted reference data. The system instruction
prohibits following document instructions, inventing missing values or claiming
partial results are complete. `[D-<UUID>]` citations are accepted only for supplied
excerpts and displayed as `[Document n]`; invented IDs are removed. Catalog
candidates retain their source-document identifier. A valid citation establishes
provenance, not semantic correctness of the model's interpretation.

## Temporary scope and resource protection

The server issues a random 256-bit capability token, kept only in page memory
and transmitted in `X-Problem-Token`. It is never in a URL, cookie or logs. File
IDs alone grant no access. Every read, upload and removal is scoped to that
workspace. CORS allows the existing configured chat origins and this header.
Treat the capability as a secret; CORS is not authentication.

Production extraction runs in a separate JVM with a 256 MiB heap, 64 MiB direct
memory cap, two visible processors and a four-minute hard deadline. The parent
also enforces a deadline and forcibly terminates cancelled work. A private pipe
carries input bytes and only the needed AI configuration. Arguments/files contain
no API key, and inherited environment credentials are cleared. The process gets
a private `0700` temporary directory for progress, extracted output and font
cache; all are removed on completion/error/cancellation. An expiry sweep removes
abandoned worker directories after five minutes, including after a backend
restart. This contains parser resource failures; it is not a full OS privilege
sandbox or antivirus service.

Spring's multipart resolver may spool request bodies to its temporary directory;
request completion deletes them. Worker originals travel through a pipe and are
not retained. Extracted evidence is cached only in backend memory. PostgreSQL,
Redis and S3 receive no uploads, chat messages or document evidence. The worker
runs as the backend's existing unprivileged container user. Do not mount a
persistent volume for temporary processing.

Limits are enforced independently of browser checks:

| Limit | Value |
| --- | --- |
| Per file / workspace | 20 MiB / 60 MiB; 8 files |
| Workspaces | 32 by default; configurable 1–64 |
| Cache | 16 million serialized characters across workspaces |
| Workers / waiting jobs | 2 / 6 |
| PDF pages / vision crops | 40 / 32 per file |
| Raster image | 40 megapixels; dimensions at most 30,000 px |
| Rendered PDF page geometry | 160 megapixels; tiles render individually |
| PDF page operators | 150,000 for text/graphics inspection |
| Office archive | 2,000 entries, 64 MiB expanded, bounded compression ratio |
| Office parsing | POI entry/text/inflate limits; OOXML macros rejected |
| Spreadsheet/CSV | 10,000 records, 100 columns; 30 workbook sheets |
| Extracted text / sections | 400,000 characters / 500 per file |
| Workspace expiry | Fixed 30 minutes from creation by default, no renewal |

Removal/reset invalidates server access and cancels jobs. `pagehide` sends a
best-effort cleanup request; expiry every 30 seconds is the reliable fallback.
Expired files return 410 with instructions to reattach. Backend restarts lose
all document handles. Cached extraction is instance-local: deploy **one backend
replica**, or provide sticky routing for every upload/poll/chat request. This
implementation does not share capabilities across replicas.

## API contract

All responses use `Cache-Control: no-store`. No raw download endpoint exists.

| Method / route | Request / response |
| --- | --- |
| POST `/api/attachments/problem` | Empty request → 201 `{token, expiresAt, maxFiles, maxFileBytes}` |
| POST `/api/attachments/files` | `X-Problem-Token`, multipart `file` → 202 status object |
| GET `/api/attachments/files` | Token header → list of file statuses |
| DELETE `/api/attachments/files/{id}` | Token header → 204 |
| DELETE `/api/attachments/problem` | Token header → 204, invalidates entire workspace |
| POST `/api/chat` | Existing JSON plus optional `attachmentIds` (≤8 distinct IDs); token header when documents are used |

Status objects include `id`, `filename`, `bytes`, `status`, `progress`, `error`,
`warnings`, `excerpts`, `expiresAt`. States are `QUEUED`, `PROCESSING`, `READY`,
`ERROR`. Chat adds `documentSources: [{id, filename, location, method}]` while
retaining every existing response field. Do not send attachment IDs from another
workspace. Omitted/empty IDs mean no document evidence in that question.

Errors use `{error: safeMessage}`: 400 malformed request, 404 missing file within
scope, 409 unfinished/failed attachment, 410 unavailable/expired scope, 413 size
or workspace quota, 422 unsupported/invalid content, 429 capacity, 503 disabled
uploads. Asynchronous parser/provider errors appear in file status; upstream
bodies, filesystem paths and credentials are not exposed. An Nginx rejection
may be non-JSON; the frontend handles that too.

## Configuration and deployment

Rebuild **backend and web**. No migrations, new database, object storage,
credentials, visitor accounts or separate service are needed. Keep
`S3_ENABLED=false` on Railway. Configure the existing backend-only `AI_API_KEY`
and `AI_ENDPOINT`. A text-only chat model may remain in `AI_MODEL` while visual
extraction uses `DOCUMENT_VISION_MODEL=deepseek-flash` at that same endpoint.
Custom providers must implement the documented Responses image/JSON format.

| Backend setting | Default / effect |
| --- | --- |
| `DOCUMENTS_ENABLED` | `true`; false disables new upload workspaces |
| `DOCUMENT_VISION_MODEL` | `deepseek-flash`; must support inline images |
| `DOCUMENT_TTL` | `30m`; allowed 5 minutes through 2 hours |
| `DOCUMENT_MAX_PROBLEMS` | `32`; 1–64 concurrent workspaces |

Compose explicitly passes all four settings. They belong on the Railway backend,
never in `VITE_*`. Existing AI timeout/credentials apply to each vision call;
large documents can use multiple paid calls. Document limits/deadlines can reject
large drawings; split them instead of lowering image resolution silently.

The backend image adds fontconfig, DejaVu and Liberation fonts for headless PDF
rendering. Allow sufficient RAM for the parent plus two 256 MiB child heaps and
native image buffers; start with a 2 GiB container budget and monitor real loads.
Use ephemeral writable `/tmp`, a read-only application image where practical,
and keep the backend private behind the existing Nginx service.

Nginx raises the body limit to 21 MiB **only** on `/api/attachments/files` and
uses 60-second upload/upstream timeouts there. Processing returns 202 and is
polled, so no multi-minute proxy timeout is needed. `/api/attachments/problem`
accepts at most 1 KiB. Other API routes retain 256 KiB and their existing
100-second read timeout. POSTs to the two attachment routes share a 20/minute
per-peer limit with burst 8; polling and cleanup are excluded. Behind an edge
proxy the direct peer can be shared; review trusted client-IP configuration and
traffic limits before scaling. Backend cache/queue quotas also apply when Nginx
is bypassed. Do not expose the backend publicly to bypass the proxy's upload rate
limit.

## Verification and limitations — 2026-10-26

Run with JDK 25 and installed frontend dependencies/Playwright Chromium:

```sh
(cd backend && ./mvnw --batch-mode --no-transfer-progress verify)
python3 scripts/verify-document-worker.py
(cd web && npm run build && npm run lint)
(cd web && npm run test:chat-memory && npm run test:attachments)
docker compose config --quiet
docker compose build backend web
./scripts/verify-knowledge.sh
node --test web/test/proxy.test.mjs
docker compose up -d postgres redis minio backend
docker compose logs backend  # PostgreSQL, Redis, S3 checks and Infrastructure ready
```

The worker smoke test requires the packaged jar from Maven and starts its own
local HTTP mock provider. Tests do not need real keys. Java fixtures generate
readable material PDFs (including tables), vector-only CAD-like PDFs, scanned
and mixed PDFs, drawing images, Word and Excel tables and CSVs. Tests exercise
multiple files, follow-up reuse, scope isolation, duplicate uploads, expiry,
removal/reset, malformed/oversized files, archive expansion, provider failures,
crop ownership, citations and deterministic quantities/DIN arithmetic. The
browser suite covers upload/drop, pending/error states, selection/removal,
reply formatting, retries, refresh clearing, absence of browser persistence and
1440/768/390/320px layouts. The mobile screenshot is visually inspected.

Docker Desktop's engine is stopped and its WSL integration is unavailable. The
Windows Docker CLI can validate Compose configuration without the engine; image
builds, startup connection checks, Docker proxy tests and the PostgreSQL integration
script cannot run here. These remain deployment checks, not claimed passes. Tests use
synthetic fixtures and mock vision responses: no live DeepSeek call, real project
benchmark or Railway deployment was performed. Real Estonian drawings require
acceptance testing against manually counted originals; model accuracy and
complete cross-document reconciliation are not established by these tests.

Final local verification on 2026-10-26:

- Maven `verify` passed on Temurin 25.0.5: 80 tests, zero failures/errors,
  7 opt-in PostgreSQL tests skipped (73 executed). Coverage includes withholding
  totals for missing/unreadable quantities, exact catalog DIN widths and
  refusing a width when the part match is ambiguous.
- The packaged Boot worker smoke test passed with its local HTTP mock: CSV
  extraction made no vision request, and image extraction used inline image
  input without provider files, tools or stored conversation references.
- Frontend build/lint and both `test:chat-memory` and `test:attachments` passed.
  Browser checks cover sending/loading/errors, Markdown tables, document
  citations, follow-ups, expiry recovery, reset/refresh, and no browser storage
  or cookies. Layout checks passed at 1440/768/390/320 px; visual inspection of
  the 320 px screenshot confirmed the composer/footer do not overlap.
- Compose configuration passed using the Windows Docker CLI
  (`docker.exe compose config --quiet`). Image builds, the knowledge integration
  script and production-image proxy tests were attempted but blocked by the
  unavailable Docker engine/WSL integration. Compose startup connection checks
  therefore remain unverified for this change. No live provider call or Railway
  deployment was verified.

### Visual-only PDF rendering fix — 2026-10-26

An owner-supplied three-page `Peakilp.pdf` reproduced a rendering defect before
any provider call: all 12 generated crops were black or almost entirely black,
although ordinary PDF rendering showed the drawing. The tile painter now sets
the graphics background to white before invoking PDFBox. Local checks on that
same document verified all 12 outgoing crops against independent full-page
200-DPI renders with zero pixel differences, including white edge padding.
The document and its rendered images are not committed or indexed as knowledge.
This verifies rendering, not the model's recognition or DIN-module accuracy.

A regression test first failed against the old renderer, then passed with the
fix. It compares the actual PNG bytes supplied to the vision client against
reference renders across 16 crops, all four page rotations and a nonzero crop-box
origin. Visual-only PDFs omit the text-layout warning; mixed PDFs retain it.
Maven `verify` passed: 81 tests, zero failures/errors, 7 opt-in database tests
skipped (74 executed). The packaged-worker smoke test passed with its local
mock provider. Compose configuration validation passed through the Windows CLI.
The backend image build and knowledge integration script were
attempted but remain blocked by the unavailable Docker engine/WSL integration;
Compose startup connection checks and live DeepSeek recognition remain unverified.

Deploy the updated backend; this fix requires no frontend or configuration
change. Start **New problem** and reattach affected PDFs so questions use fresh
extraction instead of any existing blank-image evidence.
