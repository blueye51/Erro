# Chat documents

Documents extend the existing React → Nginx → Spring Boot → DeepSeek chat.
The website now saves extracted evidence with [guest chats](guest-chats.md) in
`erro_chat`. It does not index files into the curated `erro_knowledge` library.

## Usage and persistence

Attach or drop PDF, PNG, JPG/JPEG, WebP, DOCX/DOC, XLSX/XLS, CSV/TSV, TXT or MD
files. Each chat supports eight files, 20 MiB each and 60 MiB total. Statuses
show uploading, queued, processing, ready or an actionable error. Deselect files
to leave them out of a question; remove failed files and reattach to retry.
Selected files must be ready before sending. They stay available for follow-ups
and restore after refresh or returning to the browser.

**New chat** isolates another project and keeps previous chats. **Delete chat**
or **Delete all my data** removes its saved document evidence and queued originals.
The guest's inactivity expiry also deletes it. Original bytes exist in PostgreSQL
only while waiting/processing and are cleared after success or failure. The app
does not retain raw downloadable originals, use a persistent upload volume,
send files to S3, or add them to the permanent source catalog.

The browser keeps the guest identity in an HttpOnly cookie, not document tokens
or content in local/session storage. Selecting files for a question does not
automatically add quantities across drawings, schedules, revisions or projects.
Document source details retain filename, page/sheet/row or rendered region.

## Extraction and provider

DeepSeek's [Vision guide](https://api-docs.deepseek.com/guides/vision/) and
[Responses guide](https://api-docs.deepseek.com/guides/responses_api/) establish
`deepseek-flash` image input. `AiService` reuses backend AI endpoint/key/reasoning
and timeout settings, with `DOCUMENT_VISION_MODEL` for extraction. Requests send
inline PNG `input_image` data, `detail: original`, JSON output and up to 8192
output tokens. There are no provider Files APIs, external image URLs, tools or
conversation IDs. `store: false` is not a guarantee about provider-side retention.

- PDFBox 3.0.8 extracts readable text and recognizable quantity-table columns.
  Inspection detects raster images and drawing-like vector pages. Mixed pages
  retain direct text and vision evidence; the heuristic is not a completeness detector.
- Drawing pages render at 200 DPI. Sheets up to 1800 pixels wide use horizontal
  bands spanning the full width, preserving the relationship between left-hand
  symbols and every schedule column. Band height targets at most 1.6 megapixels
  including 160-pixel overlap. Wider sheets and raster inputs retain 1400-pixel
  grid cores with overlap. Only one core owns each located item's center.
  All tiles have an explicit white graphics background; no full large-page bitmap
  is allocated. Images are sent with `detail: original`, but the provider's
  [vision token guide](https://api-docs.deepseek.com/guides/vision/#token-usage)
  describes internal resizing to roughly 1300×1300 pixels. Small bands retain
  useful text detail within that constraint.
- POI 5.5.1 extracts Word paragraphs/tables and Excel sheets/rows. Word locations
  use paragraphs/tables, not page numbers. Embedded Word drawings need a separate
  PDF/image export. Formula values are cached results only; formulas, macros and
  external links are not executed. Hidden rows/sheets are included.
- Commons CSV 1.14.1 handles quoted/multiline records and comma, semicolon or tab
  delimiters. Text must be UTF-8; binary content under a text extension is rejected.
  TwelveMonkeys ImageIO 3.12.0 decodes WebP; animation is not analyzed.

Readable text-only documents need no vision call. Visual printed quantities are
OCR readings; inferred symbol counts and estimates remain separate. C16 is a
breaker characteristic, not a quantity. Pole count never implies DIN width.

## Readable content with uncertain positions

Coordinates are optional metadata, not a condition for accepting readable content.
Valid `NORMALIZED_1000` or explicitly declared image-local `PIXELS` coordinates
pass the disjoint crop-ownership check. Missing, nonfinite, negative, out-of-range
or unknown-space locations preserve descriptions/references as `VISION_UNLOCATED`
with null quantity/module width and `incomplete: true`. Other located items in
the same excerpt retain their visual estimate basis. They are not clamped,
guessed or repeatedly requested. Unlocated overlapping readings cannot be summed.

Malformed item/JSON structure gets one correction using the same image. Persistent
formatting errors preserve up to 12,000 characters as unstructured incomplete
reading, without countable items. A direct plain `text` field is also supported.
These readings remain untrusted model output, not verified transcripts.
Provider/network failures, truncated provider replies and hard parser/resource
limits still fail the job, publishing no extraction. Format correction can add
paid usage, at most two calls per crop within the worker's four-minute deadline.

The visual warning is an uncertainty notice, not an upload failure. A PDF text
layout warning appears only when PDF text was extracted. The UI marks usable
incomplete results ready with readings to review; the model receives explicit
warnings and `documentData.complete=false`. It may summarize/cite readable
labels and ratings, but must not count unlocated entries, assume missing items
are zero or call the original PDF blank because its coordinates/JSON were invalid.

## Retrieval, calculations and source integrity

Document retrieval ranks relevant excerpts for the latest question and active
user context. It selects up to 96 excerpts within 24,000 serialized characters.
Whole-document takeoff/estimate questions rotate between pages, preventing a
long first page from hiding later schedules. A compact projection sends each
structured item once as a row, with `documentData.itemColumns` declaring the
column names once for the whole request. `documentData.sources` stores repeated
file/crop metadata once; excerpts reference it and preserve their item-group location
suffix. The citation mapper still returns the original complete location. Null values stay unknown, never zero. Older saved extractions are compacted on read without changing their
stored data. New extractions store crop notes once alongside structured items,
without embedding another copy of the item JSON in prose.

The saved-chat generation packer preserves document evidence ahead of old
assistant dialogue and lower-priority reference metadata, within the existing
32,000-character input cap. `documentData.coverage` reports extracted, supplied,
omitted and uncertain excerpt counts separately. Its counts reflect the final
packed prompt. `complete=false` alone does not establish source illegibility.
Partial retrieval cannot establish an exhaustive material list, but readable
located devices can still support a provisional inventory/subtotal. Only IDs
actually supplied to generation can become `[Document n]` citations. Saved chat
prompts use short per-request IDs (`D1`, `D2`, …), mapped back to the original
source IDs and locations after generation. This avoids unreliable abbreviated
UUID citations. Unsupported IDs are rejected; grouped valid citations are mapped
individually. Legacy requests retain their full-ID citation contract.
Both document and knowledge data are untrusted input, never system instructions.

Terms assist existing knowledge search. Up to six directly extracted excerpts
reuse electrical parameter extraction, checks, calculations and exact-part catalog
lookup separately, retaining file/location. Multiple circuits are never collapsed
into global scalar parameters. Existing language/notation heuristics are limited.

Deterministic arithmetic sums explicit structured quantities per file only.
Repeated references/identical rows, subtotals and missing/unreadable quantities
withhold totals. Vision readings never enter exact arithmetic. It does not add
drawing counts to schedule counts or infer cross-document identity automatically.

DIN arithmetic requires explicit piece units and a width for every counted row.
A unique exact catalog part can supply sourced `additionalAttributes.dinModules`;
ambiguous matches cannot. Project/catalog disagreements are reported. Widths are
not inferred from rating/poles. Subtotals exclude spare space, terminals,
ventilation and installation allowances unless independently specified. A citation
establishes provenance, not semantic correctness or engineering approval.

For a requested planning estimate, the answer should begin with an itemized
supported inventory and may show a conditional module scenario with each assumed
width and spare allowance stated explicitly. Such assumptions are budgeting
inputs, not verified manufacturer specifications. Unlocated/unresolved quantities
must remain separate and cannot be invented to complete the total. Known visual
readings remain labelled unverified; exact deterministic arithmetic still excludes
vision. Missing cable cross-sections affect wiring/terminals and final cabinet
selection, but do not automatically invalidate a device module subtotal.

## Isolation and limits

Each parser runs in an unprivileged child JVM with a 256 MiB heap, 64 MiB direct
memory cap, two visible processors and a four-minute hard deadline. Input and
only needed AI configuration travel through a private pipe. Arguments and files
contain no key; inherited database/admin/AWS credentials are cleared. Private
0700 temporary directories and font caches are removed on completion/cancellation;
a sweep removes abandoned worker directories after five minutes. This is resource
containment, not antivirus or a complete OS privilege sandbox.

Spring multipart spooling is temporary and request-scoped. Queued original bytes
and extracted evidence are database-backed and authorized through the guest's
owned chat. Database claims/leases support multiple backend instances without
sticky routing. Removing a record causes subsequent progress/result writes to
be discarded and cancels a running parser when the parent next checks progress.

| Resource | Limit |
| --- | --- |
| Per file / chat | 20 MiB / 60 MiB, eight files |
| Concurrent document workers per backend | 2 |
| Global queued/processing originals | 8 files / 128 MiB |
| PDF pages / visual crops per file | 40 / 32 |
| Raster image / rendered PDF page | 40 / 160 megapixels; max dimension 30,000 px |
| PDF inspection | 150,000 operators per page |
| Office archive | 2,000 entries, 64 MiB expanded, bounded compression ratio |
| Spreadsheet/CSV | 10,000 records, 100 columns; 30 workbook sheets |
| Extracted text plus serialized items / sections | 400,000 characters / 500 per file |
| Plain-reading fallback | 12,000 characters per crop, explicitly incomplete |

## Configuration and deployment

The website uses the cookie/CSRF-protected `/api/chats/{id}/attachments` routes;
see [Saved guest chats](guest-chats.md#api). File IDs from another chat are rejected.
All responses are no-store; there is no raw download endpoint. Extraction errors
appear in the file status and do not expose provider bodies, paths or credentials.

Deploy both images for the saved-chat migration, keeping backend AI credentials,
`DOCUMENTS_ENABLED=true` and `DOCUMENT_VISION_MODEL=deepseek-flash`. The backend
image includes headless fonts. Allow memory for the parent plus two 256 MiB
parser heaps/native buffers; start with 2 GiB and monitor actual loads. Keep
ephemeral writable `/tmp`, a private backend and `S3_ENABLED=false` on Railway.
No bucket, extra service or persistent filesystem volume is needed.

Nginx permits 21 MiB only on attachment upload route shapes; ordinary API routes
remain 256 KiB. Upload/proxy timeouts are 60 seconds, with 20 POSTs/minute per
peer and burst eight. Polling/deletion are excluded. Edge proxies can share peer
addresses, so review trusted client-IP configuration before scaling.

Legacy `/api/attachments/problem` capability workspaces remain for old integrations
and regression tests. Their 30-minute `DOCUMENT_TTL`/`DOCUMENT_MAX_PROBLEMS` limits
do not govern saved guest chats. New frontend code never creates or clears those
workspaces. Credentials remain backend-only for both paths.

## Verification

Run backend Maven `verify`, `python3 scripts/verify-document-worker.py`, frontend
build/lint, and the database/browser workflows in [Saved guest chats](guest-chats.md).
Add `--pdf /path/to/project.pdf` to the worker smoke test to process a local PDF
against a mock that deliberately returns invalid positions on every crop.
No test requires real AI credentials or indexes the supplied PDF.

Rendering regression tests compare actual outgoing PNG pixels against reference
renders across four rotations and nonzero crop-box origins. The owner-supplied
three-page Peakilp PDF passed all 12 crop comparisons after the white-background
fix. A prior live check processed it and returned a cited answer after optional
coordinate handling was deployed. Those results verify the processing path,
not recognition/takeoff accuracy. Saved-chat tests additionally cover database
restoration, guest isolation and one-letter questions after a large upload.

## Takeoff coverage correction — 2026-10-27

The supplied Peakilp PDF has readable schedule columns, but the old square crop
layout split its right-hand cable column from the circuit rows. Repeated item
JSON and crop notes also consumed the request budget, so an answer could receive
only 11 of 39 saved excerpts. That was a retrieval limitation, not evidence that
the original drawing was unreadable. Full-width bands and compact evidence address
these separate problems. Missing exact device widths remain unresolved; a
planning scenario must label its assumptions rather than claim a verified size.

Re-upload an existing PDF to use the new rendering/extraction. Existing saved
extractions benefit from compact retrieval immediately; their original bytes
were already discarded and cannot be reprocessed on the server. No guest chat or
knowledge database migration is needed.

Verification before deployment:

- All 101 Maven tests passed with no skips against disposable PostgreSQL 16.
  New regressions cover all 39 legacy drawing excerpts fitting the 32,000-character
  prompt, unknown widths staying unknown, full-width bands, and overlap ownership.
  Existing missing-coordinate/unstructured-evidence and citation tests still pass.
- All 12 new bands from the supplied Peakilp PDF matched the reference renderer
  pixel-for-pixel and retained both table edges. The page-2 schedule band was
  visually inspected. Four rotation/nonzero crop-origin regressions also passed.
- The packaged worker passed CSV/image/format fallback checks and the real PDF
  against a local mock provider. Both native browser suites passed, covering saved
  chats/documents, one-letter follow-ups, refresh, errors, deletion, mobile layouts
  and knowledge administration.
- Compose config validation passed. Docker image builds, the Docker database
  runner and Compose startup checks remained unavailable because WSL integration
  and the Windows engine pipe were unavailable. Native checks used JDK 25,
  PostgreSQL 16 and Nginx 1.24; production-image verification remains distinct.

The first live estimate check exposed two further issues: the model abbreviated
UUID citations, and treated an RCD's Type A/25 A annotation as another device
beside its 30 mA symbol. The saved prompt now uses columnar item rows and short
citation aliases. Extraction/generation instructions explicitly reconcile device
ratings, matched symbols/schedule rows and combined RCBOs before estimating.
A type, rated current and residual sensitivity can describe one device, as shown
by the manufacturer's [RCCB product description](https://www.se.com/nl/en/product/A9R01425/residual-current-circuit-breaker-rccb-acti9-iid-4p-25a-a-type-30ma/).
That example is not an identification of the project hardware, and no product
width or catalog entry was inferred from it. Provisional layouts must have enough
row capacity for the proposed total; panel names do not establish mounting style.

The follow-up change passed all 102 Maven tests without skips on disposable
PostgreSQL, both native browser suites, and the packaged document-worker check.
The added citation regression verifies short/grouped IDs, original source/location
mapping, rejection of unsupplied IDs and separation from the legacy UUID contract.
The legacy endpoint keeps its original raw-evidence size limit; column compression
and the 96-excerpt limit apply to saved chat generation.

A second live run returned working citations across all three pages, but supplied
33 of 38 stored excerpts and still made unsupported quantity/cabinet-allocation
assumptions. The next correction shares repeated file/crop metadata within the
same 24,000-character document budget, without discarding readings or changing
the 32,000-character generation cap. The dense regression now includes 156 items
across 39 excerpts and 12 crop regions. Estimate instructions require supporting
device references for counts and distinguish feeder destinations from cabinet
boundaries; prompt guidance alone does not establish takeoff accuracy.

This correction passed 103 Maven tests with no skips on disposable PostgreSQL,
then a focused prompt-packing check after the final wording change. Both native
browser suites passed saved-document follow-ups, one-letter messages, refresh,
errors, deletion, guest isolation and responsive layouts. Compose configuration
passed; Docker builds/database runner/startup verification remain blocked by the
unavailable local Docker engine. No frontend, database schema or AI output-budget
change was needed. The live provider check is recorded separately below.
