# Ripple — The Ashcroft

> Established 1924.

**Ripple** is a self-evolving life simulation set inside a prestigious historic
hotel, **The Ashcroft**. It is *not* a hotel-management, tycoon or scripted-story
game. The hotel is the environment in which independent simulated lives
repeatedly intersect, and the player is an **observer** who follows the
consequences of those encounters across decades.

This repository currently contains **Phase 1 — Foundation**. No simulation
content is implemented yet: Phase 1 delivers the technical foundation and a
premium isometric proof-of-concept of the hotel view.

---

## Phase 1 status

Delivered:

- **New Android project** with a Gradle version catalog and modular structure.
- **Dependency injection** (Hilt) wired through the app graph.
- **Room database** with a save system (`saved_games`, `simulation_metadata`),
  DataStore-backed observer settings, and a migration path from v1 (no
  destructive migration).
- **Navigation** (Compose Navigation) with the hotel view as the start
  destination and placeholder routes for later features.
- **Design system** — the warm historic-luxury Ashcroft theme (cream stone,
  dark woods, hotel green, muted gold, tungsten/moonlight), not a generic
  Material scheme.
- **Custom hotel renderer** — a Canvas-based isometric cut-away with smooth
  **pan**, pinch **zoom**, **floor focus** and **tap-to-select** room
  hit-testing. The projection/camera/hit-test maths lives in pure,
  framework-free classes so the renderer is fully unit-testable without
  launching graphics, keeping render state deterministic and separate from
  (future) simulation state.
- **The Ashcroft layout** — the Phase 1 vertical-slice geometry (lobby,
  reception, bar, restaurant, kitchen, staff room, a corridor, six guest rooms,
  a suite, housekeeping store, manager's office).
- **Temporary room information panel** for the selected room.
- **Tests** across model, world, rendering maths, hit-testing, save mapping and
  the hotel view model, plus **CI**.

Deliberately **not** in Phase 1 (later phases): people, needs, decisions,
memories, relationships, conversations, the causal graph, the chronicle, and a
running simulation clock. The time controls are present as UI scaffolding but do
not drive anything yet.

---

## Module structure

```
app                     Android application: Activity, Application, navigation
core:model              Immutable domain models, ids, geometry, sim time
core:database           Room + DataStore + the save system (Hilt-provided)
core:designsystem       The Ashcroft Compose theme
core:rendering          Isometric projection/camera/hit-test maths + render View
core:world              The Ashcroft physical layout
core:simulation         (placeholder — Phase 2+ engine)
core:decision           (placeholder — Phase 3+ decision engine)
core:testing            Shared test utilities
feature:hotel           The default hotel view (renderer host + chrome)
feature:person          (placeholder — Phase 2)
feature:timeline        (placeholder — Phase 6)
feature:history         (placeholder — Phase 6, the Chronicle)
feature:relationships   (placeholder — Phase 4)
feature:settings        (placeholder)
benchmark               Macrobenchmark module (runs on device; Phase 7)
```

---

## Tech stack

- Kotlin 2.2.10 (AGP 9 built-in Kotlin), Jetpack Compose (stable BOM), Material 3 with a custom look
- **AGP 9.2.1**, Gradle 9.4.1, JDK 17 target, `compileSdk`/`targetSdk` 36,
  `minSdk` 28
- Hilt, Room, DataStore, Coroutines/StateFlow, Kotlinx Serialization, KSP
- JUnit, Turbine, Detekt, Ktlint, Gradle version catalog

## Build

From a clean clone:

```bash
./gradlew clean test lint assembleDebug
```

Static analysis:

```bash
./gradlew detekt ktlintCheck
```

CI (`.github/workflows/ci.yml`) runs the same commands on every push/PR.

### A note on build verification in the authoring sandbox

The committed configuration targets **AGP 9.2.1 + Gradle 9.4.1** as specified,
and Hilt 2.60.1 (which requires AGP 9). Gradle 9.x distributions are served from
GitHub, which the authoring sandbox's egress policy blocks, so the full 9.2.1
build could not be executed there. The Kotlin/Compose/Room/Hilt/KSP sources were
instead compiled and the tests/lint/`assembleDebug` exercised locally under an
equivalent **AGP 8.13.2 + Gradle 8.14.3 + Hilt 2.57.2** harness (all resolvable
from allowed hosts). CI runs the real AGP 9.2.1 build on GitHub-hosted runners.
