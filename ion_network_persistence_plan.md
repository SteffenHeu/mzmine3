# Implementation plan: persisting IonNetworks via FeatureList registry

## Decisions locked in

- **Network ID allocation:** IDs are assigned at creation time by
  `FeatureList.nextIonNetworkId()` and become immutable. `IonNetworkLogic.renumberNetworks`
  and `resetNetworkIDs` are removed. The registry is keyed by ID
  (`Map<Integer, IonNetwork>`) — safe now that IDs never mutate. Caller code
  that depended on contiguous 0..N-1 IDs needs to be audited (it must not).
- **Legacy IIN data:** No backwards-compatibility shim. Old projects load
  but `IonIdentityListType` entries are silently dropped. Justified because
  `IonNetwork` itself was never persisted before, so this refactor doesn't
  regress any previously-working save.
- **Derived subtypes stay visible:** `NeutralMassType`, `SizeType`,
  `IonNetworkIDType`, `ConsensusFormulaListType`, `SimpleFormulaListType`,
  `BestIonFormulaType`, scoring types, and `IINRelationshipsType` remain as
  columns/bindings. Their `getValue` reads from `ion.getNetwork()`. Their
  `saveToXML` / `loadFromXML` become no-ops (or are skipped by
  `IonIdentityListType`'s overridden save iteration).
- **Orphan IonIdentities** (`network == null`) are skipped on save with a
  warning log. Assumption: every persistable ion belongs to a network by
  the time the project is saved.
- **Separate XML file for IonNetworks.** Networks are not written into the
  row-data file. Each feature list gets a third sibling file alongside the
  existing data file (`{name}.xml`) and metadata file
  (`{name}_metadata.xml`): `{name}_ionnetworks.xml`. This keeps the
  row-data XML focused on row/feature content and makes the network
  section trivially streamable (no need to skip past it when reading
  rows). Defined via a new `CONST.XML_ION_NETWORKS_FILE_SUFFIX`.

## Background / why this refactor

Persisting `IonIdentityListType` naively is blocked by a shared-object problem:
an `IonNetwork` is referenced by every member `FeatureListRow` through its
`IonIdentity`, and the network itself carries cross-row pointers
(`IonNetworkNode(row, ion)`) and inter-network relations
(`Map<IonNetwork, IonNetworkRelation>`). Saving via the default per-row
`DataType` path would either duplicate the network N times (one per row) or
require threading a "pre-loaded networks" context through every
`DataType.loadFromXML` override.

The refactor below removes both problems by:

1. Making `IonNetwork` the single owner of per-ion data (formulas, neutral
   mass, relations, node membership).
2. Moving the collection of `IonNetwork`s onto `FeatureList` as a registry.
3. Reducing the per-row `IonIdentityListType` persistence to a list of
   network IDs.
4. Loading the networks in a new pass between the existing two passes of
   `FeatureListLoadTask`, where rows already exist with their IDs but no
   other entries.

No `DataType.loadFromXML` signature changes are required — the existing
`flist` parameter is the seam.

---

## 1. Data model changes

### 1a. `IonNetwork` becomes the single owner of per-ion data

- Move `IonIdentity.molFormulas` out — keep a `molFormulas` list only on
  `IonNetwork` (it already has one at `IonNetwork.java:55`).
- Slim `IonIdentity` down to two final fields: `IonType ionType`,
  `IonNetwork network`. Make `setNetwork` package-private — only
  `IonNetwork.addNode / removeNode` should set it.
- Delete `IonIdentity.getMolFormulas / addMolFormula / clearMolFormulas /
  getBestMolFormula / setBestMolFormula / removeMolFormula` and forward all
  callers to the network. Audit:
    - `FormulaPredictionIonNetworkModule` (writes formulas)
    - `IonNetworkLogic` formula helpers
    - `ConsensusFormulaListType`, `SimpleFormulaListType`,
      `BestIonFormulaType`, `MolFormulaListType` subtype rendering in
      `IonIdentityListType` (`IonIdentityListType.java:64–76`)
    - GNPS / FBMN export (`GnpsFbmnExportAndSubmitModule`)
- Add `IonNetwork.getIonForRow(FeatureListRow) : @Nullable IonIdentity`
  (linear scan of `nodes` is fine — networks are tiny).

### 1b. `FeatureList` becomes the network registry

- Add to `ModularFeatureList`:
    - `private final Map<Integer, IonNetwork> ionNetworks = new LinkedHashMap<>();`
      (LinkedHashMap so save order is deterministic).
    - `getIonNetwork(int id)`, `getIonNetworks()` (unmodifiable view),
      `addIonNetwork(IonNetwork)`, `removeIonNetwork(IonNetwork)`,
      `nextIonNetworkId()`.
- `IonNetwork.delete()` and all creation sites must go through
  `flist.addIonNetwork` / `removeIonNetwork`. Confirmed call sites:
  `IonNetworkingTask:294` and `IonIdentityTest:113` (test).
- `IonNetwork` IDs are assigned at registry insertion via
  `flist.nextIonNetworkId()`. Replace the existing
  `new IonNetwork(-1)` + later renumber pattern with
  `flist.addIonNetwork(new IonNetwork(flist.nextIonNetworkId()))` at the
  call site.
- Delete `IonNetworkLogic.renumberNetworks` and
  `IonNetworkLogic.resetNetworkIDs`. Audit any caller that assumes IDs are
  contiguous 0..N-1 — none should remain.
- Once the registry is authoritative, deprecate
  `IonNetworkLogic.streamNetworks / getAllNetworks / getAllNetworksList` to
  delegate to `flist.getIonNetworks()`. Leave them as thin wrappers for one
  release to limit churn.

### 1c. `IonIdentityListType` value shape

The Java value of the type stays `List<IonIdentity>` for callers (no API
churn at the row level). Internally, persistence ignores the contained
`IonIdentity` and only writes the network IDs. The `IonIdentity` for a row
is always discoverable from the network: row holds `List<IonIdentity>`,
each `IonIdentity` knows its network, network knows which ion belongs to
which row. No new row-side type is needed.

---

## 2. Persistence shape

Networks live in a **dedicated per-flist XML file**, `{name}_ionnetworks.xml`,
zipped into the project archive next to `{name}.xml` (row data) and
`{name}_metadata.xml` (metadata). Naming pattern follows the existing
`fileNamePattern` regex in `FeatureListLoadTask:94` — add a sibling
`ionNetworksFileNamePattern` for the new suffix.

File layout:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ionNetworks flistName="..." dateCreated="..." count="N">
  <ionNetwork id="..." neutralMass="..." avgRT="..." heightSum="...">
    <molFormulas> ...ResultFormula entries... </molFormulas>
    <nodes>
      <node rowId="123"> <iontype>...</iontype> </node>
      <node rowId="456"> <iontype>...</iontype> </node>
    </nodes>
    <relations>
      <relation otherNetworkId="..." type="..."/>
    </relations>
  </ionNetwork>
</ionNetworks>
```

The `flistName` + `dateCreated` attributes mirror the row-data file
header so the loader can sanity-check it belongs to the flist it was
loaded for (same check as `FeatureListLoadTask:251`).

- `IonType` should round-trip via its own XML helpers. Verify; add
  `saveToXML` / `loadFromXML` on `IonType` if missing.
- `IonNetworkRelation` needs its own short save/load. Write each relation
  once, on the network with the lower ID; load it onto both networks. This
  keeps the symmetric `Map<IonNetwork, IonNetworkRelation>` from being
  written twice.
- Add a `CONST.XML_ION_NETWORKS_ELEMENT` / `XML_ION_NETWORK_ELEMENT` /
  `XML_ION_NETWORK_NODE_ELEMENT` / `XML_ION_NETWORK_RELATION_ELEMENT` /
  `XML_ION_NETWORK_ID_ATTR` set.

On the row side, `IonIdentityListType.saveToXML` writes only a list of
network IDs:

```xml
<datatype type_id="ion_identity_list">
  <networkRefs>
    <ref networkId="42"/>
  </networkRefs>
</datatype>
```

---

## 3. Save sequencing — `FeatureListSaveTask`

`FeatureListSaveTask.run()` (`FeatureListSaveTask.java:111`) currently does
`saveFeatureData()` then `saveAppliedMethods()`. Add a third step,
`saveIonNetworks()`, in between (or after — order doesn't matter, all
three write independent zip entries):

```java
if (!saveFeatureData()) return;
if (!saveIonNetworks()) return;     // new
saveAppliedMethods();
```

`saveIonNetworks()` mirrors `saveAppliedMethods()` (`FeatureListSaveTask:123`):

1. Create a temp file.
2. Open an `XMLStreamWriter`, write `<ionNetworks flistName=… dateCreated=… count=…>`.
3. Iterate `flist.getIonNetworks()` in insertion order. For each network
   write attributes, `<molFormulas>` (reuse `ResultFormula.saveToXML`),
   `<nodes>` (each `<node rowId=…>` wraps `IonType.saveToXML(writer)`),
   and `<relations>` — but only when `relation.networkA.id <
   relation.networkB.id` to dedupe the symmetric map.
4. Close out, copy the temp file into the project zip under
   `getIonNetworksFileName(flist.getName())` (new helper alongside
   `getDataFileName` and `getMetadataFileName`).

The row-data XML (`saveFeatureData()`) is **unchanged** apart from the
new `IonIdentityListType.saveToXML` writing only `<ref networkId="..."/>`
entries — no `<ionNetworks>` block lives in there anymore.

In the row write path, `IonIdentityListType.saveToXML` becomes:

```java
for (IonIdentity ion : ions) {
  IonNetwork net = ion.getNetwork();
  if (net == null) continue; // orphan ion — see migration note
  writer.writeStartElement("ref");
  writer.writeAttribute("networkId", String.valueOf(net.getID()));
  writer.writeEndElement();
}
```

The subtypes that were derived (formula lists, neutral mass, size, network
ID) become non-persisted view types — they read from the resolved network
at display time. Mark them so `ListWithSubsType`'s default save path skips
them, or short-circuit their `saveToXML` to no-op.

---

## 4. Load sequencing — `FeatureListLoadTask`

This is the cleanest part because of the existing two-pass design.

1. **Pass 1 — `createRows` (line 283)**: unchanged. After it returns, every
   row exists in `flist` with its ID and no entries.

2. **New pass 1.5 — `loadIonNetworks(flist, ionNetworksFile)`**: open the
   sibling `{name}_ionnetworks.xml` file (resolve its path from
   `dataFile` by swapping the suffix, same trick used for the metadata
   file at `FeatureListLoadTask:188`). If the file is absent (project
   saved before this refactor, or no networks for this flist), skip
   silently. Otherwise stream the `<ionNetwork>` elements:
    - Construct `IonNetwork` with its saved ID.
    - Restore `neutralMass`, `avgRT`, `heightSum`, `molFormulas`.
    - For each `<node>`: resolve `flist.findRowByID(rowId)` (rows already
      exist from pass 1), parse the `IonType`, build a fresh
      `IonIdentity(ionType)`, call `network.addNode(row, ionIdentity)`
      (which also sets `ionIdentity.network = network`).
    - Register the network with `flist.addIonNetwork(network)`.
    - Defer `<relations>` parsing into a second sweep, after all networks
      for this flist are in the registry, so cross-references resolve.

   Call this new method from `run()` between `createRows` and
   `parseFeatureList` (`FeatureListLoadTask:191`):

   ```java
   ModularFeatureList flist = createRows(storage, flistFile, metadataFile);
   if (flist == null) { /* ... */ continue; }
   final File ionNetworksFile = new File(flistFile.toString()
       .replace(FeatureListSaveTask.DATA_FILE_SUFFIX,
                FeatureListSaveTask.ION_NETWORKS_FILE_SUFFIX));
   loadIonNetworks(flist, ionNetworksFile);   // new
   parseFeatureList(storage, project, flist, flistFile);
   ```

   The unzip step at `FeatureListLoadTask:164` already extracts the whole
   feature-lists folder, so the new file lands in the temp dir alongside
   the data + metadata files automatically.

3. **Pass 2 — `parseFeatureList` (line 232)**: unchanged at the dispatch
   level. Inside `IonIdentityListType.loadFromXML`:
    - Parse each `<ref networkId=…>`.
    - Resolve the `IonNetwork` from `flist.getIonNetwork(id)`.
    - Resolve the `IonIdentity` via `network.getIonForRow(row)` (the
      network already added the row in pass 1.5).
    - Return the resulting `List<IonIdentity>`.

   No new parameter on `loadFromXML` — the existing `flist` parameter is
   the seam.

4. If `{name}_ionnetworks.xml` is absent (legacy project) the registry
   stays empty. `IonIdentityListType.loadFromXML` will then find no
   network for any `<ref networkId=...>` it parses (or it encounters the
   legacy inline `<ionidentity>` payload and drops it — see section 6).
   Either way the row ends up without an IIN annotation.

---

## 5. Touchpoints to refactor

Beyond the obvious save/load files:

- `IonIdentityListType` — rewrite `saveToXML` and `loadFromXML`, override
  `ListWithSubsType` subtype save iteration to skip derived subtypes.
- `IonIdentityListType` subtypes (`ConsensusFormulaListType`,
  `SimpleFormulaListType`, `BestIonFormulaType`, `NeutralMassType`,
  `SizeType`, `IonNetworkIDType`, `IINRelationshipsType`) — make them
  derived views over the network: `getValue(IonIdentity ion)` reads from
  `ion.getNetwork()`.
- `IonNetwork.addNode / removeNode / delete` — update to also notify the
  registry. `delete()` becomes `flist.removeIonNetwork(this)` plus clearing
  nodes.
- All `new IonNetwork(id)` call sites — they must pass through
  `flist.nextIonNetworkId()` and `flist.addIonNetwork(net)`. Greppable:
  `new IonNetwork(`.
- `IonNetworkLogic.streamNetworks / getAllNetworks / getAllNetworksList` —
  delegate to the registry.
- Callers of `IonIdentity.getMolFormulas / addMolFormula / …` — redirect
  to `network.getMolFormulas / addMolFormula`. Greppable.
- `FeatureList.clearIonIdentities` (if it exists) and any "clear
  annotations" actions — make sure they also clear the network registry to
  avoid orphans.

---

## 6. Backwards compatibility

**None.** Old project files load successfully (everything else is
preserved), but any `IonIdentityListType` entries from the legacy inline
format are silently dropped during row load. `IonIdentityListType.loadFromXML`
returns `null` when it encounters a legacy `<ionidentity>` payload instead
of the new `<networkRefs>` element, and logs a one-line info per row that
contained the legacy data.

Justification: `IonNetwork` was never actually persisted before this
refactor (networks were transient, reconstructed from per-row state via
`IonNetworkLogic.streamNetworks`). Dropping the per-row legacy data means
users have to re-run the IIN workflow on reopened old projects — same
practical outcome as today.

---

## 7. Verification

- Unit test for `IonNetwork.getIonForRow` (hit, miss, multi-network row).
- Round-trip test in `mzmine-community/src/test/`: build a tiny
  `ModularFeatureList` with three rows, two networks (one shared row across
  both), neutral mass, two formulas, one `IonNetworkRelation` between the
  networks; save to a temp project, load it back, assert: same network
  IDs, same node membership, identity equality of the network instance
  referenced by all member rows, formulas restored on the network (not on
  ions), relation present in both directions.
- Legacy-load smoke test: load a project saved by a pre-refactor build,
  assert the project opens without error and that
  `IonIdentityListType` entries are absent (rows otherwise intact).
- Smoke-run the IIN workflow (CorrelateGrouping → IonNetworking →
  Refinement → FormulaPredictionIonNetwork) end-to-end and save/load the
  project once, since you typically verify by running the app.

---

## How this matches the stated load order

"Networks can be constructed after the feature list metadata load, as this
step already creates all rows with their IDs" maps directly onto inserting
`loadIonNetworks` between `createRows` and `parseFeatureList` in
`FeatureListLoadTask.run()`. No `DataType.loadFromXML` signature changes;
the seam is the new pass plus the `FeatureList` registry.
