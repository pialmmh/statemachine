Below is a refined, structured specification plus enhancement suggestions.

Goals
Capture complete runtime history of each state machine instance.
History includes: state BEFORE event, triggering event (name + payload), state AFTER event, timestamps, version, context snapshot.
Snapshots stored only when debug flag is enabled.
Retrieval and human-friendly viewing of history (HTML or external open-source tooling).
Re-usable across different machine entity types (CallEntity, SmsEntity, etc.).
Debug Flag & Activation
Add boolean debug flag to GenericStateMachine (default false).
Expose in builder: builder.enableDebug() / builder.debug(true).
When debug=true, every processed event produces a Snapshot record (wrapped in try/catch so history capture never breaks core flow).
Optional: Allow dynamic enabling via registry (e.g., registry.enableDebugForId(id)) for live troubleshooting.
Snapshot Semantics Per event processed (including internal timeout events):
BEFORE: stateIdBefore, contextHashBefore (SHA-256 of serialized context), contextJsonBefore (optional to save both before/after or only after to reduce size).
EVENT: eventName, eventType, eventPayloadJson (if present).
AFTER: stateIdAfter, contextHashAfter, contextJsonAfter.
Meta: machineId, machineType (class name), runId (from test base or generated), transitionDurationMillis, version (incrementing 1..N per machine), createdAt (UTC), correlationId (optional), debugSessionId (optional).
Storage optimization option: store only AFTER full context JSON plus a JSON diff (RFC 6902 patch) of BEFORE -> AFTER. (Phase 2 optimization.)
Serialization
Serialize context object to JSON using configured ObjectMapper.
Base64 encode final JSON string (Context JSON + optional event payload JSON) for storage (field: context_b64).
Keep small textual columns unencoded for indexing/filtering: machine_id, version, state_before, state_after, event_name, created_at.
Entity Naming Convention If main entity is CallEntity then snapshot entity class: CallEntitySnapshot. General pattern: <DomainEntityName>Snapshot. Common interface: MachineSnapshot (fields getters). Optional abstract base: AbstractMachineSnapshot (id, machineId, version, createdAt, etc.).

Partition Key & Repository

Use existing partitioned repo mechanism (same sharding key as primary entity if applicable).
Partition key: machineId (or shard key of main entity).
Table naming: call_entity_snapshot (snake_case) or reuse naming conventions already present.
Indexes: (machine_id), (machine_id, version DESC), (created_at), (event_name).
Retention policy: optional TTL cleanup job (Phase 2).
Repository API Interface SnapshotStore:
void save(MachineSnapshot snapshot)
List<MachineSnapshot> findByMachineId(String machineId)
Optional pagination: List<MachineSnapshot> findByMachineId(String machineId, int offset, int limit)
Optional streaming: Stream<MachineSnapshot> streamByMachineId(String machineId) Implementation PartitionedSnapshotRepository using underlying partitioned repo API.
Hook Points in State Machine Flow At event processing boundary:
Determine stateBefore.
Serialize context (BEFORE if storing both).
Process transition (existing logic).
Capture stateAfter + contextAfter JSON.
Build snapshot object and persist (async optional). Add minimal overhead:
Provide SnapshotRecorder interface so alternative strategies (sync, async executor, queue) can be plugged in. Default: synchronous, try/catch swallow errors with logging.
Performance & Config
Config object: SnapshotConfig {boolean storeBeforeJson; boolean storeAfterJson = true; boolean storeJsonDiff; boolean async; int queueSize;}
Default: only after JSON, synchronous, no diff.
If async=true: use a bounded LinkedBlockingQueue + single consumer thread to persist; drop with warning when full.
Viewer Options (Incremental) Phase 1 (fast):
Simple HTML generator command that dumps all snapshots for a machine into a JSON file + static HTML (client-side filtering).
Use open-source lightweight libs:
DataTables or Tabulator for tabular view.
Diff view: jsondiffpatch + html formatter.
Timeline: vis-timeline (optional).
State graph: Mermaid (generate sequence/flow diagram from snapshots). Phase 2:
Mini embedded HTTP server endpoint /history/{machineId}. Phase 3 (optional external tools):
Ship snapshots additionally to:
Loki (Grafana): push structured log lines (JSON).
OpenSearch / Elasticsearch (OpenSearch Dashboards/Kibana UI).
Grafana Tempo/OTel traces (model each event as span). Recommendation: Start with static HTML viewer + jsondiffpatch + Mermaid.
Command-Line / Build Integration Add Maven profile -Psnapshots-viewer that:
Runs a Java class SnapshotHtmlExporter producing target/snapshots/<machineId>/index.html.
Optionally aggregate multiple machine histories.
Security / Privacy
Redaction hook for sensitive fields (e.g., msisdn) before serializing.
Provide interface ContextRedactor { JsonNode redact(JsonNode original); }
Testing Strategy (Human Tests) New package: com.telcobright.statemachine.humantests.snapshots Scenarios:
testCallFlowHistoryComplete(): run a typical call lifecycle, assert sequential version numbers and correct final state.
testSnapshotDisabled(): ensure no snapshots recorded when debug=false.
testDynamicEnableMidFlow(): process first events w/o snapshots, enable debug, ensure later events recorded starting with correct next version.
testDiffGeneration(): enable diff option, assert diff patch applies cleanly.
testAsyncRecorder(): stress with N concurrent events, ensure all versions present and ordered.
Minimal Failure Handling If snapshot save fails:
Log warn with machineId, version, cause.
Continue machine processing uninterrupted.
Extensibility Future: add compression (gzip) to JSON before Base64 for large contexts. Future: pluggable encryption for sensitive deployments.

Deliverables (Initial Implementation)

Debug flag + builder methods.
Snapshot infrastructure (entity + repository + recorder).
Hook integration.
CallEntitySnapshot example implementation.
HTML viewer exporter (basic).
Human integration tests (at least 3 scenarios).
Documentation section in README (Debug & Snapshot History).