---
name: Vendor SDK bridge process
description: How to expose a vendor MS-data SDK that can't be wrapped in a clean C header (e.g. a managed C#/.NET library) to mzmine via a standalone helper process speaking a stdio wire protocol (newline-delimited JSON headers + little-endian float64 binary blobs). Use when the SDK is .NET/managed, COM, or otherwise not C-ABI-callable from Java FFM/JNI. Reference - the C# AgilentBridge (MIDAC/MHDAC).
---

When a vendor SDK is a **managed library** (C#/.NET), COM, or otherwise has no clean C ABI, do NOT
try to FFM/JNI it. Wrap it in a tiny standalone **helper process** and talk over **stdio**.
Benefits: no in-process interop, the vendor runtime (.NET) stays isolated, crashes don't take down
mzmine. The Java side is a thin subprocess client (see the `Vendor MS data import (mzmine)` skill —
the `DataAccess` class drives this protocol). Canonical example: the C# `AgilentBridge` wrapping
Agilent MIDAC (IMS) + MHDAC (non-IMS).

## Transport (the wire protocol)

- **Requests**: one line of JSON on **stdin**, e.g. `{"op":"scan","id":42}`.
- **Responses**: one line of JSON "header" on **stdout**, optionally followed by **binary blobs** =
  raw little-endian `float64` arrays (m/z, intensity, ...). The header carries the counts (
  `numPoints`, `pointsPerScan[]`) so the client reads exactly that many bytes.
- **stderr** = logs ONLY. Never write logs/anything else to stdout — it corrupts the binary stream.
- Strictly synchronous request→response. The client must fully drain a response (header line + all
  its blobs) before sending the next request, or the byte stream desyncs. A request whose blobs are
  never sent (because of an early `return`) needs no draining — only outstanding *sent* responses
  do.
- Why blobs not JSON: spectra are large numeric arrays; binary float64 is far smaller/faster than
  JSON numbers. Use `Buffer.BlockCopy`/`ByteBuffer` LE; on Windows x64 native order is already LE.

## Components (mirror AgilentBridge)

- `Wire` — transport: read a request line; write `header\n` + blobs; pump stderr.
- `Session` — holds the open file as an abstraction; dispatches on the `op` field.
- Readers behind shared interfaces — one per sub-API (e.g. IMS reader + non-IMS reader), so
  `Session` and ops are reader-agnostic. Define `ISpectrum {Mz, Intensity, Centroided}`,
  `IScanMetadata`, an `IRawDataReader` base, and per-modality sub-interfaces.
- `Program` — `--test` mode (human dump, for SDK exploration / de-risking) vs wire mode (the stdin
  loop until EOF).

## Op menu (generalize from the file's structure)

- `open {path, requestCentroid}` → file-level metadata (isIms, counts, polarity, drift axis, CCS,
  scan types, hasMrm/hasMsScans, ...).
- `mtd {id}` → per-item metadata only (rt, msLevel, polarity, collisionEnergy,
  precursor/isolation, ...). The bridge resolves whether `id` is a scan or frame from the open
  reader type.
- `scan {id}` / `tfs {frameId}` → **peak data only** (`{numPoints, centroided}` + 2 blobs).
  `mobilityScans {frameId}` → `{numScans, pointsPerScan[]}` + per-bin blob pairs.
- `mrm` / `analog` → chromatograms (`{count, [...]}` + rt/intensity blob pairs).
- `close`; errors → `{"error":"..."}`. Process exits on stdin EOF.

**Split metadata from bulk data** (`mtd` vs `scan`/`tfs`). Then the client can fetch metadata, apply
scan filters, and skip peak reads for filtered items. To avoid reading the SDK spectrum twice (
metadata often requires reading it — e.g. fragmentation class), keep a **1-entry cache** in each
reader keyed by index/frame so `mtd(x)` then `scan/tfs(x)` reads the SDK once.

## JSON gotchas

- .NET `JavaScriptSerializer` emits bare `NaN`/`Infinity` tokens (invalid JSON). Either sanitize on
  the bridge or make the Java client lenient:
  `JsonMapper.builder().enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)`.
- Keep the header a single line; the client reads bytes up to `\n` then parses, then reads blobs.

## Building & exploring the SDK

- net48 (or as required) **x64** console exe referencing the vendor DLLs. Build with VS MSBuild (
  `MSBuild.exe Bridge.sln /p:Configuration=Release /p:Platform=x64`). An IDE (Rider) may demand a
  newer .NET SDK just for its *build host* — install it; it doesn't change the net48 target.
- Learn the real API from the vendor's **sample client** first; then **reflect** over the DLLs for
  undocumented members — the shipped XML docs cover only a subset. (`Assembly.LoadFrom` +
  `GetType().GetMembers()` in a throwaway script.)
- Write the **`--test` spike first**: open a file, dump counts/spectra/metadata. De-risks DLL
  probing and lets you **verify assumptions empirically** — vendor docs lie. Examples actually hit:
  a documented `PeakDetectedTotalFrameMs` throws `NotImplementedException`; `MsLevel` stays "MS" for
  All-Ions fragmentation frames (use `FragmentationClass` instead); a "Profile" format reads huge,
  `ZeroBounded` gives a compact sparse profile.
- **Explicit interface implementation**: vendor concrete classes frequently implement their
  properties explicitly, so an object initializer on the concrete type won't compile. Assign through
  the interface variable: `IFilter f = new Filter(); f.Threshold = 0;` (seen on `IMsdrPeakFilter`,
  `IBDAChromFilter`).
- Centroiding may be unavailable: prefer the vendor's stored peak/centroid data; if only profile
  exists and the SDK can't centroid, return profile and let mzmine's mass detection do it.

## Deploy

- Build output = exe + ALL vendor DLLs (+ any `*.dll.config`). Copy them together into mzmine's
  `external_tools/<vendor>/`. The Java side launches with `ProcessBuilder` and sets the working dir
  to that folder so the runtime probes the sibling DLLs; locate the exe via
  `FileAndPathUtil.resolveInExternalToolsDir("<vendor>/<exe>")`.

## Verify

Pipe a `.jsonl` of requests to the exe's stdin and capture stdout. Pure-JSON ops (`open`, `mtd`,
`frames`, `close`) are readable as text. For blob ops, check **byte accounting**:
`fileSize == Σ(headerLineBytes) + Σ(numPoints * 8 * 2)`. Note `sed -n` line numbers break across
binary blobs — `grep -ao` the specific JSON header instead. Test the full per-item sequence (
`mtd → tfs → mobilityScans → close`) to confirm the stream stays aligned.
