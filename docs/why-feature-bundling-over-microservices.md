# Android Car Headunit: Why Feature Bundling > Microservice/Module Heaviness

## 1. The Core Constraint: Slow CPU, Not Scale-Out

A car headunit (Android Auto/AAOS) runs on automotive-grade SoCs:
- Typically ARM Cortex-A53/A55 at 1.2–1.8 GHz
- No thermal headroom for sustained turbo
- 2–4 GB RAM shared with the entire IVI system
- No cloud/network dependency for local media playback

This is the **opposite** of a cloud backend where microservices shine.
Microservices solve: independent deployability, horizontal scaling, fault isolation.
Car headunits solve: latency-critical local playback, memory pressure, cold-start speed.

## 2. Why Microservices / Multi-Module Heaviness Hurts (Not Helps)

### A) Classloading Overhead (Cold Start Tax)
- Each Android module (even dynamic features) adds dex loading, class verification
- On a slow CPU, 5 modules = 5× the classloading tax on every cold start
- The migration-blueprint explicitly calls this out (Section 7.2.1):
  > "Multi-module Gradle introduces build complexity without runtime benefit on single-APK targets"
  — i.e., you pay the complexity tax for **zero** runtime performance gain on a headunit

### B) Inter-Process Communication (Binder/IPC)
- True microservices on Android = separate processes = Binder serialization
- Every call: marshal → kernel context switch → unmarshal → execute → marshal → switch back
- For media operations (seek, play, queue, download status), this turns
  O(1) method calls into O(10–100 μs) IPC round-trips
- On a 1.2 GHz CPU, 100 μs is ~120 **cycles** wasted per call — adds up fast
  when a user is scrubbing through a video timeline

### C) Memory Fragmentation
- Each service process has its own Dalvik heap, native heap, and GC roots
- Shared memory (Ashmem/MemoryFile) is manual and error-prone
- A bundled approach shares **one** heap → lower total footprint, fewer GC stalls

### D) Warm-Up Is a Myth on Headunits
- Cloud microservices stay warm for hours. Car headunits:
  - Cold boot every drive
  - Doze / sleep states kill background processes
  - Users expect < 2 seconds from tap to music
- A warm cache is a luxury you don't have. Every process = every startup cost.

## 3. Single-Threaded vs Multi-Threaded: Which Service Performs Better?

| Operation | Recommendation | Rationale |
|---|---|---|
| Playback (ExoPlayer) | **Single-threaded** (dedicated looper) | ExoPlayer's internal looper **is** single-threaded by design — adding threads adds contention, not speed |
| Download (yt-dlp) | **Multi-threaded** (thread pool) | Network I/O is the bottleneck, not CPU. Parallel downloads = parallel network. CPU is idle during I/O wait. |
| UI Rendering | **Single-threaded** | Main thread. Any work > 16 ms must be offloaded, but only to a bounded coroutine/thread pool. |
| Media Scanning | **Multi-threaded** (single worker) | File I/O bounded. One worker is sufficient — more threads don't speed up disk reads. |
| Video Frame Decode | **Single-threaded** (hardware decoder) | Codec is already offloaded to DSP/GPU. CPU thread management adds overhead, not perf. |

**Key insight:** The document's architecture follows exactly this pattern.
[`PlaybackManager`](app/src/main/java/com/natkibe/playerpro/player/PlayerHolder.kt) runs on its own looper thread (single-threaded, predictable).
DownloadQueue uses a thread pool (parallel I/O). This is **correct** for headunits.

## 4. Why Package-by-Feature Bundling Is Better (The Core Argument)

**What "Package-by-Feature" means** (from migration-blueprint Section 7.2.1):
All features live in the **same module** (app module).
They are separated by Java/Kotlin packages, **not** Gradle modules.
Shared code lives in `:core` subpackages (model, network, di, util).
Everything compiles into **one DEX, one APK**.

### Why This Wins on a Headunit

✅ **Benefit #1 — No Classloading Tax × N**
One module = one classloader = verified once. Cold start is O(1), not O(N).

✅ **Benefit #2 — In-Process Method Calls (Zero IPC)**
[`PlaybackManager`](app/src/main/java/com/natkibe/playerpro/player/PlayerHolder.kt) calling DownloadQueue for a status check = direct function call.
No AIDL, no Binder, no marshalling, no kernel context switch.
On a 1.2 GHz CPU, this is the difference between:
- In-process: ~0.001 ms (3–5 CPU cycles for a virtual dispatch)
- IPC: ~0.1–1 ms (hundreds of cycles for marshalling + switch)

✅ **Benefit #3 — Shared Memory = Smaller Footprint**
One heap. One set of GC roots. Shared singletons. Shared caches.
Total memory: 40–60 MB instead of 80–120 MB for split processes.

✅ **Benefit #4 — Compile-Time Safety (No Cross-Process Contracts)**
Cross-process interfaces require AIDL + stub/proxy code generation.
A version mismatch between service and client crashes at runtime.
In-process: the compiler catches everything at build time.
On a headunit, you **cannot** push an OTA fix for a version mismatch.

✅ **Benefit #5 — Predictable Scheduling**
The Android kernel schedules **one** process's threads.
The CFS (Completely Fair Scheduler) can optimize priority of the app.
With 5 processes, each gets 1/5 of the CPU time slice — you lose control.
On a slow CPU, you **need** the scheduler to prioritize your audio thread.

✅ **Benefit #6 — Strangler Fig Migration Is Safer**
The blueprint uses the Strangler Fig pattern: extract interfaces **first**,
move implementations incrementally, keep tests passing.
Multi-module forces you to commit to module boundaries upfront — if wrong,
the refactor is expensive (module merges are painful).
Package-by-feature lets you refactor freely within the same module.

## 5. The Nuanced Truth (from the Documents Themselves)

The migration-blueprint **does** identify 5 future microservice candidates
(Section 7.3 — Backend Candidates):
1. Media metadata enrichment service
2. Subtitle search service
3. Download scheduling service
4. User preferences sync service
5. Analytics service

But these are **backend** services, not headunit services.
The principle:
- **Server-side** microservices for network-heavy, CPU-light tasks.
- **Client-side** bundling for CPU-heavy, latency-sensitive tasks.

This is the correct architectural split:

```
┌────────────────────────────────────────────────────┐
│  HEADUNIT (weak CPU, low latency req)              │
│  ┌──────────────────────────────────────────────┐  │
│  │  package-by-feature monolithic (ONE PROCESS) │  │
│  │  • Playback (single-threaded looper)         │  │
│  │  • Download (thread pool for I/O)            │  │
│  │  • UI (main thread + coroutines)             │  │
│  │  • Media scanning (single worker thread)     │  │
│  └──────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────┘
                         ↕ HTTPS (not IPC)
┌────────────────────────────────────────────────────┐
│  CLOUD (strong CPU, high latency tolerated)        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ Metadata │ │ Subtitle │ │ Analytics│  ...      │
│  │ Service  │ │ Service  │ │ Service  │           │
│  └──────────┘ └──────────┘ └──────────┘           │
└────────────────────────────────────────────────────┘
```

## 6. Summary: The 3 Laws for Low-Power Android Media Apps

**Law 1: One Process to Rule Them All**
No separate service processes. No AIDL. No IPC for media ops.
Everything in-process = everything fast.

**Law 2: Single-Threaded for Interactive, Multi-Threaded for I/O**
- UI rendering, playback pipeline, state management → single thread
- Network downloads, file scanning → thread pool
- Never oversubscribe a slow CPU with more threads than cores

**Law 3: Package-by-Feature, Not Module-by-Layer**
- Organize by domain feature (download, playback, browser, settings)
- Share core via internal packages (`:core.model`, `:core.di`)
- One Gradle module = one APK = one classloader = fast cold start
