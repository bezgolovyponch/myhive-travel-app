# Postmortem: backend container OOM-killed by Render (2026-10-02 → 2026-10-06)

## Impact

Render killed the `myhive-backend` instance (plan `starter`, 512 MiB) six times for
exceeding its memory limit — `server_failed` with `oomKilled {memoryLimit: 512Mi}`:

| UTC | Context |
|---|---|
| 2026-10-02 12:55 | first OOM kill in the service's history (none in Aug–Sep) |
| 2026-10-06 13:52 | 40 min after an AI-planner generation (+47 MiB in five minutes) |
| 2026-10-06 21:38, 21:51, 22:06, 22:55 | four kills in 80 minutes while the team tested Group vote v3 |

Each kill cost ~70 s of downtime (JVM restart) and dropped in-flight requests; the
evening burst also lost whatever the testers had in progress. No data was lost.

## Root cause

Capacity, not a leak: **the JVM's maximum heap plus its non-heap footprint exceeded
the container.** `myhive-backend/Dockerfile` ran with `-XX:MaxRAMPercentage=55`,
i.e. a 282 MiB heap ceiling, chosen on 2026-07-07 (`6539a46`) when 70 % had already
been container-killed and 55 % "just fit". Native Memory Tracking on a local build
with the same sizing (1 vCPU ⇒ SerialGC, as in prod) gave, in MiB committed:

| Area | `88b94ed` (18 Sep, pre-AI) | `origin/main` (7 Oct) |
|---|---|---|
| Metaspace + class space | 109 | 127 |
| Code cache after JIT warm-up | 48 | 58 |
| Symbols + CDS archive | 36 | 40 |
| Threads, arenas, misc | 15 | 13 |
| **Non-heap total** | **~208** | **~238** |
| Max heap (55 % of 512) | 282 | 282 |
| **Sum when the heap is full** | **490** | **520 — over the limit** |

Two things combined:

1. **The margin was always thin.** With ~22 MiB to spare, the pre-AI build survived
   only because metaspace and the code cache grow slowly (JIT warm-up, lazily loaded
   classes) and a deploy usually replaced the instance before the creep reached the
   limit. The 18 Sep build hit 501 MiB on 28 Sep, minutes before the next deploy.
2. **The AI planner stack (PRs #27–#29, 28 Sep) ate the margin.** Spring AI 2.0 pulls
   in the official OpenAI Java SDK (50 MB), Kotlin, Netty and langgraph4j; the fat jar
   grew from 118 MB to 206 MB and resident non-heap by ~30 MiB. From then on the sum
   was over 512 MiB whenever the heap filled up to its max.

The heap reaches its max under any sustained allocation — an AI generation (13 k-token
prompts, 28 KB results, 80-activity catalog snapshots) or ordinary traffic. The JVM
never sees pressure: from its point of view the heap is under its ceiling, so it keeps
committing pages, and the kernel kills the container instead. That is why the memory
graph climbs with ~1 % CPU and why no log line precedes a kill. Render's metric also
counts the page cache for the 206 MB jar, so the last few MiB arrive for free.

What the 6 Oct deploys (PR #33 Group vote v3, PR #34, PR #35 preset packages) did
**not** do: add a leak. `build.gradle` is unchanged, the langgraph4j checkpoints live
in Postgres in prod (`PostgresSaver`), the in-process maps (`SessionLocks`,
`DailySessionCap`, `RateLimitFilter`) are bounded, and the new vote code is plain JPA.
The DB shows no AI session or generation during the evening burst — only one
`vote_session`. The deploys supplied traffic and a cold JVM, nothing else.

Contributing factor: prod logs `com.myhive.backend` at WARN and exposes only the
`health` actuator endpoint, so there was no memory number anywhere; the split above
had to be reconstructed by rebuilding both commits locally with NMT.

## Resolution

- `myhive-backend/Dockerfile`: `-XX:MaxRAMPercentage=40` (205 MiB heap ⇒ ~65 MiB of
  headroom), `-XX:MaxMetaspaceSize=160m` and `-XX:ReservedCodeCacheSize=80m` so the two
  areas that grow with uptime have ceilings: metaspace exhaustion becomes a logged
  `OutOfMemoryError` instead of a silent SIGKILL. Deliberately no `ExitOnOutOfMemoryError`:
  a heap OOME on one request thread is a 500 today and the JVM carries on, while an exit
  would drop the in-memory email queue every time. Thread stacks (~15 MiB idle, Tomcat
  allows 200 request threads) are the one area left uncapped.
- `MemoryUsageLogger` logs the heap / metaspace / code-cache split every 10 minutes
  (`app.monitoring.memory-log-interval`, env `MEMORY_LOG_INTERVAL`); prod keeps
  `com.myhive.backend.monitoring` at INFO under its WARN default.

Not done, by choice: the `starter` → `standard` plan upgrade (1 vCPU / 2 GB). It is the
real fix once the AI planner is enabled for the public; 40 % is the bridge until then.
Follow-ups worth a separate change: bound `server.tomcat.threads.max` (50 is plenty on
0.5 vCPU) to close the thread-stack gap; give the memory line its own scheduler thread
if it turns out to queue behind the email-sending jobs.

## Verification

- `java -XX:MaxRAM=512m <new flags> -XX:+PrintFlagsFinal -version` on JDK 25:
  `MaxHeapSize=216006656`, `MaxMetaspaceSize=167772160`, `ReservedCodeCacheSize=83886080`,
  `UseSerialGC=true`, `SegmentedCodeCache=false` (an 80 MiB cache is a single
  `CodeCache` pool; `JvmMemorySnapshot` reads both layouts).
- `JvmMemorySnapshotTest`, `MemoryUsageLoggerTest`; the full suite stays green.
- After deploy: the `JVM memory:` line should settle around `heap x/≤205/205`,
  `metaspace ≈ 128 (class space ≈ 20)`, `code cache ≈ 60`, i.e. `heap+metaspace+code`
  ≤ ~395 MiB. That is the JMX view: symbols, the CDS archive and thread stacks (~55 MiB)
  are not memory pools, so Render's graph runs about that much higher plus page cache —
  expect it to level off around 450 MiB, and no further `server_failed` events.

## Lessons

- A container limit must be budgeted from the non-heap side first: measure
  `VM.native_memory summary`, then give the heap what is left — not the other way round.
- Every dependency that ships a client SDK costs metaspace on a 512 MiB box; Spring AI
  alone was worth ~30 MiB resident.
- A service with no memory line in its logs cannot be diagnosed after a kill; the
  10-minute summary is the minimum.
