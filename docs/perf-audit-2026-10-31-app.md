# Compose performance audit — 2026-10-31 — app

Audit of cold start and list scrolling, following the Measure → Diagnose → Fix → Verify loop. All app numbers come from Macrobenchmark on a real phone, against release builds with R8.

## Summary

- **Most of the lag you feel comes from the debug build.** On the same phone, debug scrolling has P95 frames of 38 ms and 22.7% janky frames; release with R8 has P95 of 10 ms and 7.5% janky frames. Cold start is 1214 ms in debug and 280 ms in release (`am start`).
- **Release builds crashed on launch.** R8 removed the constructor Room uses to create the database (`NoSuchMethodException: PersonalRecordsDatabase_Impl.<init>`). The APK published on GitHub (v1.1.0, rebuilt on 2026-10-21) is affected. Fixed with a keep rule.
- **The release workflow had been failing** on new Compose lint checks (19 errors), so no fixed APK could be published. All fixed; `lintRelease` passes.
- **A Baseline Profile now ships with the app.** Cold start to content: 652 ms → 508 ms (−22%) compared with no compiled code.
- **The Worker was spending most of each request on schema setup.** `/api/dashboard` ran about 30 `CREATE TABLE` / `ALTER TABLE` statements before reading data, and `/api/activity` read nine tables one after another. After the fix the dashboard answers in about 0.4 s instead of 2.4 s, and activity in about 0.3 s instead of 1.8 s.
- Lists are already in good shape: stable `key` and `contentType` everywhere, and every restartable composable is skippable (Strong Skipping).

## Environment

- Compose BOM 2026.09.00 (Compose UI 1.13.0-beta02), Material 3 1.5.0-beta02
- Kotlin 2.2.21 (Strong Skipping on by default), AGP 9.2.1
- Macrobenchmark and Baseline Profile plugin 1.5.0, UiAutomator 2.4.0, ProfileInstaller 1.4.1
- Device: Xiaomi 2412DPC0AG (rodin), Android 16, 1220×2712 at 520 dpi, high refresh rate (frame budget about 8.3 ms)
- Server: Cloudflare Worker + D1 (production)

## Baseline (Phase 1)

### Debug vs release

Timeline, 10 flings down and 10 up, measured with `dumpsys gfxinfo`:

| | Debug | Release (R8) |
| :--- | ---: | ---: |
| Cold start (`am start` TotalTime) | 1214 ms | 280 ms |
| Frame time P50 / P90 / P95 | 7 / 16 / 38 ms | 6 / 9 / 10 ms |
| Janky frames (legacy metric) | 22.7% | 7.5% |

### Cold start (`StartupTimingMetric`, 10 iterations, median)

| Compilation | Time to initial display | Time to full display |
| :--- | ---: | ---: |
| None (fresh install, nothing compiled) | 508 ms | 652 ms |

Time to full display is reported by `ReportDrawnWhen` on the Overview screen, once the dashboard data is shown.

### Scrolling (`FrameTimingMetric`, warm start, 5 iterations, 5 flings down and up)

| List | CPU frame time P50 / P90 / P99 | Frame overrun P99 |
| :--- | :--- | ---: |
| Overview | 5.2 / 7.3 / 10.0 ms | 2.5 ms |
| Timeline | 5.5 / 8.0 / 10.9 ms | 1.9 ms |
| Music | 5.7 / 7.4 / 9.8 ms | 0.7 ms |
| Notifications | 6.0 / 8.0 / 11.1 ms | 2.7 ms |

A positive overrun means the frame missed its deadline. Only the slowest 1% of frames do.

### Server response times

Requests the Overview screen makes on start, timed from a laptop (median of four runs; `/api/health` is about 130 ms, which is mostly network):

| Endpoint | Time |
| :--- | ---: |
| `/api/dashboard` | 2.4 s |
| `/api/activity?days=14&limit=20` | 1.8 s |
| `/api/letterboxd/films?limit=100` | 1.2 s |
| `/api/music/stats` | 0.7 s |

From the phone the same screen showed content in about 0.65 s, so the network path matters a lot. The server-side cost is real either way (see Diagnosis).

## Diagnosis (Phase 2)

- **Compose Compiler reports (release):** 1220 composables, 1150 restartable, 743 skippable. Every restartable function in the report is marked skippable because Strong Skipping is on. Unstable parameters appear mostly on screen-level parameters (`SettingsViewModel` ×13, `Flow<Unit>?` ×8, a few `List`/`Set` parameters). None sit in a list row, so they do not cost anything while scrolling.
- **Lazy lists:** every list (`Timeline`, `Music`, `Notifications`, `Books`, `Movies`, `Nutrition`, `Health`) uses a stable `key` and a `contentType`. Rows do no per-item formatter or regex work.
- **No Baseline Profile:** only the profiles bundled with libraries were present, so app code ran interpreted or JIT-compiled on first launch and first scroll.
- **Release crash:** `proguard-rules.pro` had `-keep class * extends androidx.room.RoomDatabase` without members. R8 full mode (default since AGP 8) then drops the no-arg constructor that Room calls by reflection.
- **Overview waits for everything:** `OverviewViewModel.loadData()` starts nine requests and shows nothing until all of them finish. The slowest request (the dashboard) decides when the screen appears. Two requests download 350 health records and 200 notifications only to count them.
- **Worker schema setup on every request:** `ensureMusicTables`, `ensureMdbListTables` and the others run 30 statements per dashboard request (10 + 9 + 5 + 5 + 1), each a round trip to D1. `/api/activity` also ran its nine reads sequentially.
- **CI blocked:** `lintRelease` failed on `NonObservableLocale` (4), `LocalContextGetResourceValueCall` (17), `LocalContextConfigurationRead` (1) and `ContextCastToActivity` (1).

## Fixes applied (Phase 3)

| Area | Change | Files | Measured result |
| :--- | :--- | :--- | :--- |
| R8 / Room | Keep the Room database constructor | `app/proguard-rules.pro` | Release builds start instead of crashing |
| Measurement | `ReportDrawnWhen` when Overview shows data | `OverviewScreen.kt` | Enables time to full display |
| Baseline Profile | Generated from startup, Timeline, Music and Notifications journeys (31,104 rules, 3,749 in app code); `profileinstaller` added | `app/src/release/generated/baselineProfiles/`, `baselineprofile/` | See Verification |
| Worker | Schema setup runs once per isolate (`ensureSchemaOnce`) | `worker/src/index.ts` | Removes about 30 D1 round trips per dashboard request; responses identical on the demo database |
| Worker | `/api/activity` reads all nine sources in one `db.batch()`; dashboard settings queries run in parallel | `worker/src/index.ts` | One round trip instead of nine |
| Lint / CI | `LocalResources` for strings in callbacks, `LocalActivity`, locale from `LocalConfiguration` | `MainActivity.kt`, `OverviewScreen.kt`, settings pages, catalog, music | `lintRelease` passes; in-app language switching still works |

## Verification (Phase 4)

### Cold start (median of 10)

| Compilation | Initial display | Full display |
| :--- | ---: | ---: |
| None (fresh install) | 508 ms | 652 ms |
| Library profiles only | 469 ms | 582 ms |
| **App Baseline Profile** | **424 ms** | **508 ms** |

Against a fresh install that is −17% to first frame and −22% to content. Against library profiles only, −10% and −13%.

### Scrolling (frame overrun P99, warm start)

| List | Library profiles only | App Baseline Profile |
| :--- | ---: | ---: |
| Overview | 2.5 ms | 0.6 ms |
| Timeline | 2.8 ms | 1.6 ms |
| Music | 0.7 ms | 2.7 ms |
| Notifications | 2.5 ms | 3.3 ms |

Warm-start scrolling barely changes, as expected: the code is already JIT-compiled by the time the list scrolls. The differences in Music and Notifications are within run-to-run noise at P99. The profile mainly helps the first launch and the first scroll after install or update.

### Server response times (after deploying, same laptop, median of five)

| Endpoint | Before | After |
| :--- | ---: | ---: |
| `/api/dashboard` | 2.4 s | 0.41 s |
| `/api/activity?days=14&limit=20` | 1.8 s | 0.31 s |
| `/api/letterboxd/films?limit=100` | 1.2 s | 0.14 s |
| `/api/music/stats` | 0.7 s | 0.16 s |

The first dashboard request on a new Worker isolate takes about 1.1 s, because it runs the schema setup once.

- Baseline Profile in the APK: yes (`assets/dexopt/baseline.prof`, 12.9 KB)
- CI stability gate: no (see open items)

## Open items

1. **Republish the release APK.** The one on GitHub crashes on launch; the release workflow should now pass.
2. **Show Overview progressively.** Render the dashboard as soon as it arrives instead of waiting for all nine requests, and replace the 350-record and 200-notification downloads with server-side counts.
3. **CI stability gate.** Strong Skipping makes this low priority; add `compose-stability-analyzer` with `stabilityDump` / `stabilityCheck` if unstable types start showing up in list rows.
4. **Regenerate the Baseline Profile** after large UI changes (see below).

## How to re-run

Gradle's connected test tasks uninstall the app when they finish, which would delete its data on a personal phone. These steps install and run everything with `adb` instead.

```bash
cd android

# 1. Benchmarks
./gradlew :app:assembleBenchmarkRelease :baselineprofile:assembleBenchmarkRelease
adb install -r app/build/outputs/apk/benchmarkRelease/app-benchmarkRelease.apk
adb install -r -t baselineprofile/build/outputs/apk/benchmarkRelease/baselineprofile-benchmarkRelease.apk
adb shell am instrument -w -r \
  -e class com.personalrecords.baselineprofile.StartupBenchmark \
  com.personalrecords.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r \
  -e class com.personalrecords.baselineprofile.ScrollBenchmark \
  com.personalrecords.baselineprofile/androidx.test.runner.AndroidJUnitRunner

# 2. Regenerate the Baseline Profile
./gradlew :app:assembleNonMinifiedRelease :baselineprofile:assembleNonMinifiedRelease
adb install -r app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk
adb install -r -t baselineprofile/build/outputs/apk/nonMinifiedRelease/baselineprofile-nonMinifiedRelease.apk
adb shell am instrument -w -r \
  -e class com.personalrecords.baselineprofile.BaselineProfileGenerator \
  com.personalrecords.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/media/com.personalrecords.baselineprofile/BaselineProfileGenerator_generate-startup-prof.txt
cp BaselineProfileGenerator_generate-startup-prof.txt app/src/release/generated/baselineProfiles/baseline-prof.txt
cp BaselineProfileGenerator_generate-startup-prof.txt app/src/release/generated/baselineProfiles/startup-prof.txt

# 3. Compose Compiler reports (written to app/build/compose_compiler/)
./gradlew :app:compileReleaseKotlin -PcomposeCompilerReports=true
```

Keep the phone unlocked with the screen on while the benchmarks run. The release and benchmark builds are signed with the same debug key as local debug builds, so they install over each other without losing data.
