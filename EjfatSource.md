# EjfatReceiverSource — Build & Deployment Guide

`EjfatReceiverSource` is an ERSAP Java source engine that receives fully
reassembled EJFAT events from the native E2SAR Reassembler via JNI and hands
each event downstream as `EngineDataType.BYTES` (a `java.nio.ByteBuffer`).

This document describes what to compile, in what order, and how the resulting
artifacts wire together at runtime.

---

## 1. Artifact map

Two independently built pipelines share `libe2sar` at runtime but never talk
to each other. Pick the one appropriate for your deployment.

| Artifact | Built from | Consumer | Depends on |
|---|---|---|---|
| `libEjfatReceiverActor.so` | `e2sar-utils/src/ejfat_receiver_actor.cpp` | ERSAP **C++** DPE (loaded as an engine plugin via `create_engine()`) | `libe2sar`, `libersap`, `libxmsg` |
| `libEjfatProcessorActor.so` | `e2sar-utils/src/ejfat_processor_actor.cpp` | ERSAP **C++** DPE | `libe2sar`, `libersap`, `libxmsg` |
| `libEjfatReceiverJni.so` | `e2sar-utils/src/ejfat_receiver_jni.cpp` | The **JVM** via `System.loadLibrary("EjfatReceiverJni")` | `libe2sar`, JNI ABI |
| `ersap-java-<version>.jar` | `ersap-java` Gradle build | ERSAP **Java** DPE (contains `EjfatReceiverSource.class` + `EjfatReceiverSource.yml`) | Standard `ersap-java` deps only |

`libEjfatReceiverActor.so` and `libEjfatReceiverJni.so` are **independent siblings**
— neither links against the other. They each construct their own
`e2sar::Reassembler` from `libe2sar` directly.

`EjfatReceiverSource.java` loads **only** `libEjfatReceiverJni.so`. It never
touches `libEjfatReceiverActor.so`.

---

## 2. Prerequisites (once per host / container)

Must be present before any of the steps below will succeed. The existing
`e2sar-utils/Dockerfile.cli` already provisions all of these.

- `libe2sar` installed and discoverable via `pkg-config --exists e2sar`
- ERSAP C++ runtime under `$ERSAP_HOME` (with `include/`, `lib/` or `lib64/`,
  and `pkgconfig/ersap.pc`) — only needed if building the C++ ERSAP plugins
- JDK with `$JAVA_HOME` set — only needed if building the JNI bridge
- `$CODA` set — only needed if you also want `enable_et=true`

### Is `$ERSAP_HOME` required?

Depends on which artifact you are compiling:

| Building / running | `$ERSAP_HOME` required? | Why |
|---|---|---|
| `libEjfatReceiverActor.so` (C++ ERSAP plugin) | **Yes** at build time | Meson resolves `ersap_dep` by probing `$ERSAP_HOME/{lib64,lib}/pkgconfig/ersap.pc`. |
| `libEjfatProcessorActor.so` (C++ ERSAP plugin) | **Yes** at build time | Same `ersap_dep`. |
| `libEjfatReceiverJni.so` (JNI bridge) | **No** | Links only against `libe2sar` and JNI headers. Requires `$JAVA_HOME` instead. |
| `ersap-java` (`./gradlew build`) | **No** at build time | Gradle does not consult `$ERSAP_HOME`. |
| ERSAP **C++** DPE at runtime | **Yes** | The DPE binary and its `dlopen`-ed engine plugins live under `$ERSAP_HOME`. |
| ERSAP **Java** DPE at runtime | Practically yes | Launch scripts under `$ERSAP_HOME/bin` use it to locate config, plugin dirs, and native libs. |

Summary of the two clean paths:

- **JNI-only path** (`EjfatReceiverSource` + `libEjfatReceiverJni.so`): needs
  `$JAVA_HOME` at build time; `$ERSAP_HOME` only for launching the Java DPE.
- **C++ ERSAP actor path** (`libEjfatReceiverActor.so` / `libEjfatProcessorActor.so`):
  needs `$ERSAP_HOME` at both build time and runtime.

If you build with `-Denable_ersap=false -Denable_jni=true`, `$ERSAP_HOME` is
not needed to produce the JNI bridge.

---

## 3. Build sequence — C++ artifacts (`e2sar-utils` repo)

```bash
cd /path/to/e2sar-utils
meson setup build \
    -Denable_ersap=true \
    -Denable_jni=true \
    -Denable_et=true          # only if you also need the ET binaries
meson compile -C build
meson install -C build        # installs into ${prefix}, e.g. /usr/local
```

This single Meson build produces every native artifact listed in the map
above. Each `enable_*` flag can be flipped independently; e.g. to build only
the JNI bridge:

```bash
meson setup build -Denable_ersap=false -Denable_jni=true -Denable_et=false
meson compile -C build
meson install -C build
```

Installed layout (using default `/usr/local` prefix):

```
/usr/local/lib/libEjfatReceiverActor.so     # ERSAP C++ plugin
/usr/local/lib/libEjfatProcessorActor.so    # ERSAP C++ plugin
/usr/local/lib/libEjfatReceiverJni.so       # JNI bridge (loaded by JVM)
```

---

## 4. Build sequence — Java package (`ersap-java` repo)

`ersap-java` is a standard Gradle project. The JNI bridge is opaque to
`javac`, so the Java build has no C++ dependency at compile time.

```bash
cd /path/to/ersap-java
./gradlew build                  # compile + test
./gradlew publishToMavenLocal    # or ./gradlew install / assemble,
                                 # depending on how downstream picks it up
```

Produced:

- `build/libs/ersap-java-<version>.jar` — contains the compiled class
  `org.jlab.epsci.ersap.examples.engines.generic.EjfatReceiverSource` and
  the resource `EjfatReceiverSource.yml`.

Deployment: put this jar on the classpath of whatever JVM will host the
source engine (typically the ERSAP Java DPE). Classpath is separate from
`java.library.path`, which locates the JNI `.so`.

---

## 5. Runtime configuration

### 5.1 Native library discovery (JVM must find `libEjfatReceiverJni.so`)

The JVM has to resolve `libEjfatReceiverJni.so` from `java.library.path`
(or `LD_LIBRARY_PATH`) **before** `EjfatReceiverSource` is class-loaded:

```bash
# One of:
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH
# or:
java -Djava.library.path=/usr/local/lib ...
```

`libEjfatReceiverJni.so` itself needs `libe2sar` (and its transitive
Boost / gRPC deps) resolvable via the same loader path.

Inside `Dockerfile.cli` these libraries land in `/usr/local/lib`, which is on
the container's default loader path — no extra `LD_LIBRARY_PATH` is needed
there.

### 5.2 Classpath

`ersap-java-<version>.jar` must be on the JVM classpath so ERSAP can
instantiate `EjfatReceiverSource` by class name from the YAML spec.

### 5.3 Engine configuration (JSON passed at `open` time)

```json
{
  "action":           "open",
  "file":             "ejfat://receiver",
  "ejfat_uri":        "ejfat://token@cp-host:18008/lb/1?data=192.168.1.100:19522",
  "recv_ip":          "0.0.0.0",
  "recv_port":        19522,
  "recv_threads":     1,
  "event_timeout_ms": 500,
  "max_wait_ms":      5000,
  "with_cp":          false,
  "validate_cert":    true
}
```

Notes:
- `file` is a name-carrier only (same convention as `SourceOfDoubles`);
  the real config rides in the sibling fields.
- `ejfat_uri` is the only required field; the rest have sensible defaults
  matching `EjfatReceiverActor`.

---

## 6. Incremental rebuild matrix

| You edited | Rebuild | Restart |
|---|---|---|
| `ejfat_receiver_actor.cpp` | `meson compile -C build && meson install -C build` → `libEjfatReceiverActor.so` | ERSAP C++ DPE |
| `ejfat_processor_actor.cpp` | same → `libEjfatProcessorActor.so` | ERSAP C++ DPE |
| `ejfat_receiver_jni.cpp` | same → `libEjfatReceiverJni.so` | JVM (loader caches `.so` per-process) |
| `EjfatReceiverSource.java` (Java-only, JNI signatures unchanged) | `./gradlew build` | JVM |
| `EjfatReceiverSource.yml` | `./gradlew build` | ERSAP orchestrator (re-reads engine spec) |
| **JNI signature changed on either side** | Rebuild both `libEjfatReceiverJni.so` **and** the Java class | JVM. A mismatch surfaces as `UnsatisfiedLinkError` at first `native` call. |

Never rebuild `ersap-java` in response to C++ changes alone, and never
rebuild the C++ shared objects in response to `.java` changes alone.

---

## 7. Runtime dataflow

Two pipelines, chosen at deployment time, sharing `libe2sar` at the bottom
but never talking to each other:

```
                         ┌──────────────────────────────────────────┐
   UDP EJFAT packets ──▶ │ libe2sar (Reassembler)                   │
                         └──────────────────────────────────────────┘
                                    │                       │
              ┌─────────────────────┘                       └────────────────┐
              ▼                                                              ▼
  ┌───────────────────────────────┐              ┌─────────────────────────────────────┐
  │ libEjfatReceiverActor.so      │              │ libEjfatReceiverJni.so              │
  │  (ERSAP C++ engine)           │              │  (JNI bridge)                       │
  └───────────────────────────────┘              └─────────────────────────────────────┘
              │ xMsg (BYTES)                                    │ JNI (direct ByteBuffer)
              ▼                                                 ▼
  ┌───────────────────────────────┐              ┌─────────────────────────────────────┐
  │ EjfatProcessorActor (C++)     │              │ EjfatReceiverSource.java            │
  │ or any downstream ERSAP engine│              │ + downstream Java ERSAP actors      │
  └───────────────────────────────┘              └─────────────────────────────────────┘
```

Envelope emitted by both pipelines is byte-identical:

```
[ 8 bytes: data_id as double (native byte order) ]
[ N bytes: reassembled event body                ]
```

---

## 8. Troubleshooting quick reference

- `UnsatisfiedLinkError: no EjfatReceiverJni in java.library.path` — the JVM
  cannot find `libEjfatReceiverJni.so`. Set `-Djava.library.path` or
  `LD_LIBRARY_PATH`.
- `UnsatisfiedLinkError: <method-name>` at first native call — Java class and
  `.so` are out of sync. Rebuild both, restart the JVM.
- `libe2sar.so: cannot open shared object file` when loading the JNI `.so` —
  the loader path is missing E2SAR. Add its install location to
  `LD_LIBRARY_PATH`.
- Meson configure fails with `enable_ersap=true but ERSAP_HOME is not set` —
  export `$ERSAP_HOME`, or drop `-Denable_ersap=true` if you only need the
  JNI bridge.
- Meson configure fails with `enable_jni=true but JAVA_HOME is not set` —
  export `$JAVA_HOME`, or drop `-Denable_jni=true`.
