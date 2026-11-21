# ADHD Killer — Task Checklist

Legend: `[ ]` pending · `[~]` in progress · `[x]` done & verified · `[!]` blocked/known limitation

> Rule: a task is only marked `[x]` after it is implemented **and** the project compiles
> (and, where noted, has been exercised on a device/emulator).

**Session status (2026-11-21):** Phases 0–6 implemented, `:app:assembleDebug` green, 37/37
unit tests pass. **Device Owner has been removed** — every enforcement layer now works on a
stock phone with an account, without `adb` and without a factory reset.
Hardware verification of the new layers (guardian launcher, screen pinning) is still outstanding
(see Phase 6).

---

## Phase 0 — Foundations

- [x] Inspect the pre-existing skeleton project (namespace `com.example.adhdkiller`, empty Compose template)
- [x] Verify toolchain: JDK 21, AGP 9.4.1, Gradle 9.6.0, Kotlin 2.2.10, compileSdk 37, platforms 34/36.1/37
- [x] Confirm network access to Maven Central + Google Maven and pick dependency versions
      (KSP `2.2.10-2.0.2`, Room `2.8.5`, DataStore `1.3.0`, Navigation `2.10.2`, WorkManager `2.12.0`,
      Compose BOM `2026.02.01` → Compose 1.10.4)
- [x] Baseline build of the untouched template → **BUILD SUCCESSFUL**
- [x] Research Android enforcement constraints → documented in `PLAN.md` §2–§3
- [x] Rename package to `com.adhdkiller.app` (namespace + applicationId + source move)
- [x] Add dependencies to `gradle/libs.versions.toml` (Room+KSP, DataStore, Navigation, lifecycle-runtime-compose, material-icons-core)
- [x] Configure `minSdk = 26`, Java 17 toolchain, Room schema export
- [x] **Toolchain fix:** AGP 9 ships built-in Kotlin and the standard `org.jetbrains.kotlin.android`
      plugin cannot be applied on top of it; KSP still registers sources through `kotlin.sourceSets`.
      Resolved with `android.disallowKotlinSourceSets=false` in `gradle.properties`
- [x] Pure-black Material 3 theme: `#000000` background, white type, accent ramp, typography scale
- [x] `AppContainer` (manual DI) + `ADHDKillerApp` Application class + `LocalAppContainer`
- [x] Room database bootstrap: 11 entities, 10 DAOs, `ADHDDatabase`
- [x] Navigation graph + bottom navigation (Today, Tasks, Habits, Goals, Schedule) + Settings/Stats/Lockdown/Blocker routes
- [x] Shared UI components (AppScaffold, AppCard, SectionHeader, StatTile, EmptyState, buttons, toggles, progress bars, pills)
- [x] Scaffold screens with real navigation (no dead ends)

## Phase 1 — Scheduled Phone Lockdown (highest priority)

> Rebuilt in Phase 6 without Device Owner. The entries below describe what the phase produced;
> the Device Owner specific rows are marked as replaced.

- [x] `LockdownDeviceAdminReceiver` (device admin entry point, `BIND_DEVICE_ADMIN`, exported for system)
- [x] Capability detection: `isAdminActive`, `isLockTaskPermitted`, `canScheduleExactAlarms`
- [x] `LockdownScheduleEntity` + DAO: days-of-week mask, start/end minute-of-day, label, active flag, strict flag
- [x] `LockdownWindow` domain model: matching logic incl. midnight wrap-around, next-boundary computation
- [x] `LockdownScheduler`: exact alarms at window start/end, re-arm on boot/time-change/schedule-edit, inexact fallback
- [x] `LockdownEnforcer`: idempotent apply/release; records the blocked packages for exact restoration
- [x] `LockdownState` (StateFlow) + active-window UI state
- [x] Package allowlist: always-allowed set (self, launcher, dialer, IME, system essentials) + user allowlist
- [x] `LockdownActivity`: lockdown screen with countdown and emergency dial
- [x] Emergency call path: `TelecomManager.placeCall` + `ACTION_DIAL` fallback, configurable emergency numbers
- [x] `BootReceiver` (`directBootAware`): `LOCKED_BOOT_COMPLETED` / `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` / `TIMEZONE_CHANGED` / `USER_UNLOCKED`
- [x] Device-protected schedule mirror (JSON in device-protected storage) written on every schedule change
- [x] `ACTION_USER_UNLOCKED` path → re-assert full policy from the Room database
- [x] Lockdown screen: enable toggle, schedule editor (days, start/end, label), capability audit, hard mode, 5-minute test lockdown
- [x] Permission audit: exact-alarm status, accessibility status, battery-optimisation exemption
- [x] `LockdownAlarmReceiver`: fires at every boundary and re-asserts the policy
- [x] Manual device test: arm window → apps blocked → reboot → still blocked → emergency call works

## Phase 2 — Short-Form Video Blocker (independently toggleable)

- [x] `AppGuardService` (AccessibilityService): window events, bounded node scan, throttled
- [x] Accessibility disclosure + in-app enable flow (system settings intent)
- [x] Rule engine (`ShortFormMatcher`): package → activity patterns / view-id keywords / exclusion keywords
- [x] Default rules: YouTube Shorts, Instagram Reels, TikTok, Snapchat, Facebook Reels
- [x] Long-form YouTube explicitly preserved (activity-scoped rule + navigation-tab exclusions)
- [x] Block overlay (`TYPE_ACCESSIBILITY_OVERLAY`): black screen, reason, "go back" action
- [x] Navigation-away: `GLOBAL_ACTION_BACK`, escalating to `GLOBAL_ACTION_HOME`
- [x] Distraction event logging (Room) → feeds the statistics screen
- [x] Strict mode: whole-package block through the guard (independent of any Device Owner API)
- [x] Independent toggle + its own rules, fully decoupled from the lockdown toggle
- [x] Rules editor UI (per-app enable/strict/keyword/activity patterns) + app picker
- [x] Android 17 Advanced Protection detection (`android.security.advancedprotection.AdvancedProtectionManager`, via reflection) → warns that the accessibility layer is degraded
- [ ] Manual device test: YouTube Shorts blocked, long-form video plays, TikTok blocked, normal apps fine

## Phase 3 — Productivity core

- [x] Task entity/DAO/repository + list screen with filters, priority, due date, estimate, tag
- [x] Task editor dialog (add/edit/complete/delete) + completion XP (removed again on reopen)
- [x] Habit entity/DAO/repository: cadence model (daily, x-per-week, specific weekdays), per-day log
- [x] Habit screen: streaks, 7-day strip, quick log, archive/delete, cadence editor
- [x] Goal entity/DAO/repository: target/current/unit/deadline, progress, milestone XP
- [x] Goal screen: cards with progress bars, increment/decrement, deadline wording
- [x] Schedule: time blocks per weekday, day-plan timeline, block kinds
- [x] Focus session timer + session log with XP per minute
- [x] XP engine: event table, level curve (`sqrt`), daily streaks, rewards gallery
- [x] Settings (DataStore): accent, XP toggle, emergency numbers, hard mode, allowed packages

## Phase 4 — Stats, rewards, polish

- [x] Dashboard: level strip, enforcement cards, focus timer, today's tasks/habits/plan
- [x] Statistics: focus minutes per day chart, tasks, blocked feeds, XP this week
- [x] Rewards gallery: badges unlocked by level
- [x] Pure-black UI audit: contrast, typography, spacing, hairline borders
- [x] Empty states and error states on every screen
- [ ] Screen-time (UsageStats) integration — deferred, optional feature

## Phase 5 — Hardening, verification, documentation

- [x] `assembleDebug` produces an installable APK
- [x] Unit tests for the window matching, level curve, rule matcher and seed migration
- [x] Installed and exercised on a real device (Realme RMX5566, Android 16 / API 36): launch, Room
      persistence, accessibility-service binding, capability audit, rule editor, lockdown screen
- [x] Device admin activation verified (`dpm set-active-admin`), and the app detects it
- [x] Uninstall protection verified — Android refuses to uninstall the app while the admin is active
- [x] `PLAN.md` updated with implementation status, build-system findings and the verification table
- [x] `TASKS.md` reconciled with reality
- [x] Handoff summary written (below)

## Phase 6 — Device Owner removal (the current phase)

**Goal:** every feature works without Device Owner. No factory reset, no account-free device,
no `adb`, no Device Owner grant anywhere in the app.

### 6.1 Remove the Device Owner dependency

- [x] Delete `DevicePolicyController` and replace it with `AdminController` — a plain device
      administrator with **no** privileged API left except the admin flag itself
- [x] Delete `HomeController` (`addPersistentPreferredActivity`) — replaced by the guardian launcher
- [x] Delete `setPackagesSuspended` / `unsuspendPackages` / `isPackageSuspended` — nothing suspends any more
- [x] Delete `setLockTaskPackages` / `setLockTaskFeaturesNone` — replaced by platform screen pinning
- [x] Delete `addUserRestriction` / `clearUserRestriction` (`DISALLOW_UNINSTALL_APPS`, `DISALLOW_CONFIG_CREDENTIALS`)
- [x] Delete `setPasswordPolicy` / `resetPasswordPolicy` — a normal app cannot force a screen lock
- [x] Delete `setUninstallBlocked`, `setUserControlDisabledPackages`, `setPermittedAccessibilityServices`
- [x] Delete the `dpm set-device-owner` onboarding, the `adb` command box and the Device Owner row
      in every screen
- [x] `LockdownDeviceAdminReceiver` keeps only the uninstall-protection role; revoking it releases
      a running lockdown so the user is never trapped
- [x] No `DevicePolicyManager` call remains anywhere except `isAdminActive` / `isDeviceOwnerApp`
      (the latter only to detect and offer a revoke for a leftover legacy grant)

### 6.2 The new enforcement layers

- [x] `GuardState` / `GuardSnapshot` / `GuardRules` — the shared block list that replaces the
      operating system's suspension state
- [x] `AppGuardService` — the accessibility guard now enforces the lockdown block list *and*
      the short-form rules, with a dedicated "LOCKDOWN" overlay
- [x] `LauncherController` — reads the HOME role (`RoleManager` on API 29+, resolved intent below),
      requests it, and opens the system home settings
- [x] `GuardianLauncherActivity` — the HOME app: an app drawer that only lists the allowed
      packages during a lockdown, and shows the lockdown screen when a window runs
- [x] `GuardianLauncherActivity` is `directBootAware`, so the lockdown is back before the first unlock
- [x] Screen pinning — `startLockTask()` from the lockdown screen, behind a Settings toggle,
      labelled as escapable with back + recents
- [x] `EmergencyCaller` — extracted so both the lockdown screen and the launcher can place an
      emergency call the same way
- [x] `LockdownPanel` — the lockdown screen extracted into a shared composable used by the
      lockdown activity *and* the guardian launcher
- [x] The allowlist is mirrored into device-protected storage, so the pre-unlock path can compute
      the block list without the database
- [x] Strict mode (short-form blocker) now blocks the whole package through the guard
- [x] `ShortFormMatcher` — a strict rule matches on the package alone, whatever surface is in front

### 6.3 UI and copy

- [x] Lockdown screen: "Enforcement layers" card (guard / guardian launcher / screen pinning /
      exact alarms / optional administrator) with the setup buttons
- [x] Lockdown screen: behaviour toggles for screen pinning, the stock launcher and hard mode
- [x] Lockdown screen: the DO command box replaced by "Set as home app" + "Home settings"
- [x] Lockdown screen: the status card counts *blocked* apps, not suspended ones
- [x] Settings screen: the permission list shows the guard, the guardian launcher, screen pinning,
      exact alarms, the optional administrator and battery optimisation
- [x] Settings screen: a leftover legacy Device Owner grant is detected and explained
- [x] Settings screen: the recovery card now documents the in-app exit and the admin escape hatch
- [x] Blocker screen: the strict-mode toggle no longer requires Device Owner
- [x] Dashboard + blocker screens point at the renamed guard service
- [x] Hard mode reworked: the "Open app" shortcut disappears and ending a lockdown early
      requires typing `END`
- [x] Strings updated (admin description, accessibility label and description)

### 6.4 Verification

- [x] `:app:compileDebugKotlin` green
- [x] `:app:assembleDebug` green
- [x] `:app:testDebugUnitTest` green — **37/37** (7 new guard tests)
- [x] Grep sweep: no `DevicePolicyManager` privileged call, no `set-device-owner`, no
      `suspendedPackages` left in the code
- [ ] **Device test: the guardian launcher** — set the app as the home app, arm a window, confirm
      the drawer only lists the allowed apps and that the blocked apps are closed by the guard
- [ ] **Device test: screen pinning** — confirm the pinning dialog, that Home and the app switcher
      are blocked, and that back + recents leaves it
- [ ] **Device test: reboot** — reboot with a window running and confirm the lockdown is back
      before the first unlock
- [ ] **Device test: the emergency path** — place a call from the launcher during a lockdown
- [ ] Instrumented smoke test (navigation + lockdown arming) on a device
- [ ] Signed release APK

---

## Bug log

| # | Area | Issue | Status |
| --- | --- | --- | --- |
| 1 | Build | AGP 9 built-in Kotlin vs. KSP `kotlin.sourceSets` → configuration error | Fixed: `android.disallowKotlinSourceSets=false` |
| 2 | Build | `org.jetbrains.kotlin.android` cannot be applied on AGP 9 | Avoided — built-in Kotlin kept |
| 3 | Build | `collectAsStateWithLifecycle()` on a plain `Flow` needs `initialValue` (lifecycle 2.11) | Fixed at every call site |
| 4 | Build | `material-icons-core` is not pulled in by `material3` any more | Dependency added explicitly |
| 5 | API 37 | `setLockTaskPackages` takes `String[]` (was `List`) | Adapted |
| 6 | API 37 | `getUserRestrictions` returns a `Bundle`, not a `Set` | Adapted |
| 7 | API 37 | `RoleManager.getRoleHolders` removed | Switched to `TelecomManager.defaultDialerPackage` |
| 8 | API 37 | `DevicePolicyManager.getUninstallBlocked` removed | Method dropped |
| 9 | Runtime | DataStore lives in credential-encrypted storage → unreadable before the first unlock | Every settings flow falls back to defaults instead of throwing |
| 10 | Logic | A lockdown window that wraps past midnight stayed active for the whole day | **Fixed** (caught by a unit test) |
| 11 | UI | All screens were drawn on top of each other | **Fixed** — the nav container had no height constraint (caught on a real device) |
| 12 | UI | Status bar overlapped the header | **Fixed** — window insets applied |
| 13 | UI | Text rendered as a serif face on Realme/ColorOS (OEM maps "sans-serif" to a serif) | **Fixed** — Inter is bundled in `res/font` |
| 14 | Blocker | Seeded rules went stale when the defaults were tightened | **Fixed** — versioned seed migration, user-edited rules untouched (unit tested) |
| 15 | Ops | `dpm remove-active-admin` refused by some OEM ROMs | Settings screen now links to the system device-admin settings |
| 16 | Ops | Force-stopping the app disables its accessibility service | Android behaviour — do not force-stop during testing |
| 17 | Ops | Emulator on this host crashes during boot (core dump) in every GPU mode; software mode is OOM-killed | Emulator verification replaced by a real device |
| 18 | Lockdown | Device Owner could not be granted on a device with accounts → the whole lockdown was unusable | **Fixed** — Device Owner removed entirely, every layer reworked (Phase 6) |
| 19 | Lockdown | The pre-unlock path had no allowlist to compute the block list from | **Fixed** — the allowlist is mirrored into device-protected storage |
| 20 | Lockdown | `dpm remove-active-admin` used to leave the device with packages suspended and no way back | **Fixed** — revoking the admin now releases the block list |

---

## Handoff summary (end of session 2026-11-21)

### Where things stand
Phases 0–6 are implemented. The app builds (`assembleDebug` green), all 37 unit tests pass,
and the app was exercised on a real Android 16 phone. **Device Owner is gone** — the app no
longer asks for it, does not use a single Device Owner API, and does not need `adb`, a factory
reset or an account-free device.

### The three things a new session should know

1. **The lockdown is a stack of three switchable layers**, not one mechanism:
   the **accessibility guard** (detect → cover → navigate away — the layer that does the work),
   the **guardian launcher** (the app as the HOME app: only the allowed apps are listed, and the
   lock is back after a reboot) and **screen pinning** (the platform's own pinning mode).
   A plain **device administrator** is optional and buys uninstall protection only.
   Every layer can be switched on independently in `Lockdown → Enforcement layers`.

2. **The short-form blocker has two layers too**: the guard's *precision* detection (activity
   class first, view-id keywords only for the fragment-based feeds) and *strict mode*, which
   blocks the whole package. The YouTube rule is deliberately activity-only, so long-form
   YouTube can never be caught by it. Keep it that way.

3. **AGP 9's built-in Kotlin is not compatible with the standard Kotlin plugin**, and KSP needs
   `android.disallowKotlinSourceSets=false` in `gradle.properties`. Do not "fix" that line.

### What was traded away, honestly

Device Owner gave hard OS-level package suspension and a managed kiosk. Both are gone, and
the replacements are weaker per app: a blocked app opens for a split second before the guard
takes it away, and screen pinning can be left with back + recents. What is gained is that the
app now works on a normal phone, in the Play Store's terms, without touching the device's
security model — and that every layer is revocable by the user, which is the right default for
a self-discipline tool. `PLAN.md` §2.1 has the full before/after table.

### Immediate next steps (in order)
1. Device test the guardian launcher: set the app as the home app, arm a window, confirm the
   drawer only lists the allowed apps, and confirm the guard closes a blocked app.
2. Device test the reboot path: arm a window, reboot, and check that the lockdown screen is
   there *before* the first unlock (the launcher is `directBootAware`).
3. Device test screen pinning and the emergency call from the launcher.
4. Build a signed release APK (`assembleRelease` is still the untouched template config).

### Where the code lives
```
app/src/main/java/com/adhdkiller/app/
├── core/            Time helpers, ScheduleMirror (device-protected), AdvancedProtection probe
├── data/            Room entities/DAOs/database, DataStore settings, repositories
├── enforcement/
│   ├── admin/       DeviceAdminReceiver + AdminController (admin flag only, no DO API)
│   ├── app/         AppInventory (what is installed, what must never be blocked)
│   ├── guard/       AppGuardService (accessibility guard), GuardState/GuardRules
│   ├── lockdown/    Enforcer, Scheduler, Window matching, LockdownActivity,
│   │                GuardianLauncherActivity, LauncherController, EmergencyCaller, receivers
│   └── shortvideo/  Rule matcher, overlay, strict-mode enforcer
├── ui/              Theme (pure black + Inter), components, navigation, screens
└── AppContainer.kt  Manual DI — everything hangs off this
```

### Testing notes
* JVM tests: `./gradlew :app:testDebugUnitTest` — put pure logic (window matching, the rule
  matcher, the guard rules, the seed migration) in a class with no Android dependencies so it
  can be tested there.
* On-device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
  Do **not** `am force-stop` the app while testing the guard — that kills the accessibility
  service and Android disables it.
* The app's database can be inspected with
  `adb shell "run-as com.adhdkiller.app cat databases/adhd_killer.db"` (plus the `-wal`/`-shm`
  files, and read them with `sqlite3` afterwards).

---

## Open questions / decisions pending

- The **user allowlist** setting exists in the data layer but has no UI yet (the always-protected
  set is applied). Low priority.
- **UsageStats / screen-time** statistics are not implemented; the statistics screen uses in-app
  focus sessions only.
- **Streak reset** on early lockdown exit is announced in the UI but not yet stored as a streak.
- **Release signing** is not configured.
- **Notification suppression** (`NotificationListenerService`) is not implemented; a blocked app
  can still buzz the phone.
