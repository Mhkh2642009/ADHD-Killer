# ADHD Killer — Project Plan

> Discipline, time management and distraction blocking for Android.
> Enforcement-first design: the phone lockdown and the short-form video blocker are the
> core of the product; everything else (tasks, habits, goals, schedule, XP) exists to make
> the lockdown worth obeying.

**Status:** Phases 0–4 implemented and building · **Last updated:** 2026-11-21
**Target:** Android 17 (API 37) · **minSdk:** 26 · **Language:** Kotlin 2.2.10 · **UI:** Jetpack Compose (Material 3)
**Enforcement:** **no Device Owner** — the app installs and runs on a stock phone with an
account, with no `adb`, no factory reset and no enterprise tooling.

---

## 1. Product goals

| Goal | Success signal |
| --- | --- |
| Make deep focus physically unavoidable during chosen hours | During a lockdown window the user physically cannot stay in a non-essential app |
| Survive reboot / power-off without losing enforcement | The lockdown is re-armed automatically after a cold boot |
| Kill short-form feeds without killing the apps themselves | YouTube long-form still works; Shorts/Reels/TikTok do not |
| Make the friction honest and visible | Every block is logged, every bypass is documented, no hidden behaviour |
| Reward discipline, not just restrict it | XP, levels, streaks and unlockable rewards for completing focus blocks |

### Non-goals (v1)

* No cloud accounts, no backend, no telemetry. Everything is on-device.
* No MDM/enterprise deployment, no Device Owner, no `adb` setup ritual. The app is installed and
  used like any other app.
* No root / system-privilege hacks. Everything uses documented, shippable Android APIs.
* No "monitor your partner" features — the app is a self-discipline tool.

---

## 2. Enforcement architecture

Android has no API that says "block app X from being opened". Every mechanism below is a
**layer**, and the lockdown stacks them so that defeating one layer still leaves the others.
The user switches the layers on and off in Settings — the app never demands a particular
combination, and it never asks for a privilege the platform only grants on a factory-reset
device.

### 2.1 Why Device Owner was removed

The first version of this app built its lockdown on `DevicePolicyManager` as **Device Owner**.
That was the strongest thing a non-rooted app could do, and it was also the single biggest
liability in the product:

| Problem with Device Owner | Consequence |
| --- | --- |
| It can only be granted on a device with **no accounts added yet** | A daily-use phone can never have it — a factory reset with no Google account, or an emulator |
| A Device Owner app **cannot be uninstalled** | The user is stuck with it, even if they want it gone |
| It made the app a system-level tool | Play Store distribution became questionable; sideloading was the only path |
| Every enforcement layer depended on it | One missing grant turned the whole lockdown into a decorative screen |

The rewrite replaced every Device Owner API with something a normal app may do. What was lost
and what replaced it:

| Device Owner capability | Replacement | Strength compared to the old layer |
| --- | --- | --- |
| `setPackagesSuspended` (hard OS block) | **Accessibility guard** (detect → cover → navigate away) + **guardian launcher** (blocked apps are not offered) | Weaker per app — the app opens for a split second before the guard takes over — but it works on any device |
| `setLockTaskPackages` + `startLockTask` (managed kiosk) | **Screen pinning** via the platform's own pinning mode | Weaker — the user confirms each pin and can leave it with back + recents |
| `addPersistentPreferredActivity` (HOME lock) | **Guardian launcher**: the app *is* the HOME app, so the platform launches the lockdown screen at boot | Equal in effect, but the user grants it explicitly and can revoke it |
| `addUserRestriction` (no uninstall / no credential change) | **Device administrator** (optional) for uninstall protection | Narrower — it protects *this* app, not every app on the device |
| `setPasswordQuality` (forced screen lock) | Dropped | Gone — the platform does not let a normal app force a screen lock. "Hard mode" now means the app's own exit door is harder to open |
| `setUserControlDisabledPackages`, `setPermittedAccessibilityServices` | Dropped | Gone — a user who disables the accessibility service also disables the enforcement layer. The app says so |

**Net effect:** the lockdown is weaker per layer and honest about it, but it now works on a
phone that has a Google account, in the Play Store's terms, and without touching the device's
security model. For a self-discipline tool that is the better trade.

### 2.2 Layer 1 — The accessibility guard (the workhorse)

`AppGuardService` is an `AccessibilityService` that watches the foreground window and refuses
to let a blocked app stay there.

* On a window change it checks the live block list (`GuardState`): the packages the running
  lockdown blocks, plus the packages the short-form blocker marked strict.
* On a hit it draws a full-screen `TYPE_ACCESSIBILITY_OVERLAY` (black, red reason, "Go back"),
  logs a distraction event and issues `GLOBAL_ACTION_BACK`, escalating to `GLOBAL_ACTION_HOME`
  if the surface survives.
* The block list is published by the enforcers, so the same service enforces the lockdown and
  the short-form rules without either of them knowing about the other.
* It is **optional**: the user enables it in the system settings and can disable it again. The
  app never pretends otherwise — the Lockdown screen shows a red dot when the guard is off.

**Honest limitation:** the platform gives no way to *prevent* an activity from starting.
Blocking means "detect → cover → navigate away". The blocked app is visible for a fraction of a
second.

### 2.3 Layer 2 — The guardian launcher (the door)

`GuardianLauncherActivity` is a HOME app: the user makes it their home app through the system's
"choose home app" dialog, and can undo that in the system settings at any moment.

* **Reboot survival.** The system launches HOME at boot. If HOME is the guardian launcher, the
  launcher reads the mirrored schedule from device-protected storage and shows the lockdown
  screen instead of an app drawer. This replaces `addPersistentPreferredActivity` — with the
  user's consent in the loop.
* **A real door.** While a lockdown runs, the drawer only lists the allowed packages. The
  blocked apps are not one tap away; they are not there at all.
* Outside a lockdown it behaves like an ordinary launcher, so the user is not punished for
  switching their home app over.

### 2.4 Layer 3 — Screen pinning (the extra lock)

The platform's own pinning mode, entered with `Activity.startLockTask()` from the lockdown
screen. It blocks Home and the app switcher.

* On an unmanaged device the system asks the user to confirm the pinning dialog — once per
  session.
* The session can be left with **back + recents**. The Settings screen says exactly that, in
  those words.
* It is a toggle, off by default. It is depth, not the foundation.

### 2.5 Layer 4 — Boot survival (direct boot)

Cold boot is the classic bypass: power off, reboot, the enforcement process never ran.

* The lockdown screen, the guardian launcher and the boot receiver are declared
  `android:directBootAware="true"`.
* The receiver listens for `LOCKED_BOOT_COMPLETED` (fires *before* the user unlocks) and
  `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` / `TIMEZONE_CHANGED` / `USER_UNLOCKED`.
* The schedule **and the allowlist** live in **device-protected storage**
  (`createDeviceProtectedStorageContext()`), because the Room database lives in
  credential-encrypted storage and is unreadable before the first unlock.
* Pre-unlock the app therefore already knows which packages to block and publishes them to the
  guard. The gap between power-on and re-arm is the boot broadcast latency.
* `ACTION_USER_UNLOCKED` then re-asserts the full policy from the real database.

### 2.6 Layer 5 — Device administrator (optional, uninstall protection only)

A plain device administrator — **not** Device Owner. While it is active, Android refuses to
uninstall this app, so a lockdown cannot be removed by uninstalling the tool that enforces it.

* It needs no factory reset, no account-free device and no `adb`.
* It grants nothing else: the app uses no other `DevicePolicyManager` API.
* Deactivating it ends any running lockdown (the receiver releases the block list), so the
  escape hatch works even if the in-app exit is unreachable.
* Everything in the app works with the administrator switched off.

### 2.7 Emergency calls

* The default dialer is always on the allowlist → the user can open it and dial.
* The lockdown screen additionally offers an **Emergency** button that places the call directly
  through `TelecomManager.placeCall` (CALL_PHONE permission), falling back to the system dialer
  with the number pre-filled if the platform refuses.
* Incoming calls are never blocked: the system call UI is drawn by the platform over the
  lockdown screen.
* Emergency services numbers are configurable (default `112`, `911`, `999`, `000`, `110`).

### 2.8 What the lockdown deliberately does *not* do

| Bypass | Why it is not (fully) prevented | Mitigation |
| --- | --- | --- |
| Factory reset | Destroys every app and every setting | Documented; the schedule and stats live in-app only, so a reset wipes history |
| Disabling the accessibility service | Settings → Accessibility is user-controlled | The lockdown screen says the enforcement layer is off; the guardian launcher and pinning still stand |
| Unpinning with back + recents | The platform's pinning mode always allows this | Screen pinning is opt-in and labelled as escapable; the guardian launcher and the guard remain |
| Switching the home app back | The user may always choose another launcher | Opt-in, reversible, never forced |
| Uninstalling the app | The device administrator blocks it | Optional; the escape hatch is documented |
| `adb shell dpm remove-active-admin` | Requires USB debugging enabled by the owner | Turn off USB debugging; documented in Settings |
| Exiting the window early | By design the window is user-defined | Penalty XP for early exit, hard mode requires typing `END` |
| Second device / tablet | Out of the app's control | — |

### 2.9 Scheduling

* Windows are defined as `daysOfWeek + startMinuteOfDay + endMinuteOfDay` and may wrap
  past midnight.
* Boundaries are armed with `AlarmManager.setExactAndAllowWhileIdle`. On Android 12+ this
  needs `SCHEDULE_EXACT_ALARM`; if the user has not granted it we fall back to
  `setAndAllowWhileIdle` (inexact, ±few minutes) and surface that honestly in the UI.
* Alarms are re-armed on: boot, package replaced, time zone changed, time set, schedule
  edit, and app start. A periodic `WorkManager` job is the backstop for anything missed.

---

## 3. Short-form video blocker

Requirement: block Reels / Shorts / TikTok **while preserving normal phone use and
long-form YouTube**. Implemented as two independent, separately toggleable layers on top of the
same guard.

### 3.1 Layer A — Activity-level detection (precision layer)

The accessibility service observes `TYPE_WINDOW_STATE_CHANGED` / `WINDOWS_CHANGED` /
`WINDOW_CONTENT_CHANGED` for the configured packages only, and matches the foreground surface
against rules:

| App | Package | Rule | Long-form preserved? |
| --- | --- | --- | --- |
| YouTube | `com.google.android.youtube` | activity `…youtube.shorts.ShortsPlayerActivity` | ✅ `…common.YouTubeActivity` untouched |
| Instagram | `com.instagram.android` | activity `com.instagram.ui.reels.ReelsActivity` + Reels-tab heuristic | ✅ feed untouched |
| TikTok | `com.zhiliaoapp.musically`, `com.ss.android.ugc.aweme` | whole app is a short-form feed | n/a |
| Snapchat | `com.snapchat.android` | whole app | n/a |
| Facebook | `com.facebook.katana` | Reels heuristic | ✅ feed untouched |

On a match the service (1) logs a distraction event, (2) draws a full-screen
`TYPE_ACCESSIBILITY_OVERLAY` explaining the block, and (3) issues `GLOBAL_ACTION_BACK`
(and `GLOBAL_ACTION_HOME` if the surface survives) so the feed is actually left, not merely
covered.

**Honest limitation:** the platform gives no way to *prevent* an activity from starting.
Blocking means "detect → cover → navigate away". Where the app exposes a dedicated activity
(YouTube Shorts) the exit is immediate. Where the feed is a *fragment* inside the main
activity (Instagram Reels, Facebook Reels), detection is heuristic and can miss a variant.
Layer B closes that gap.

### 3.2 Layer B — Strict mode (whole-app block)

For apps whose short-form surface cannot be reliably distinguished (TikTok, Snapchat) or that
the user marks "strict", the **whole package** is refused by the guard, whatever surface of it
is in front.

* This used to be the Device Owner suspension — a real OS-level block. It is now the same
  mechanism as everything else: detect → cover → navigate away, plus the guardian launcher
  hiding the app entirely.
* A strict rule is a package-level block, so it needs no extra permission and no extra setup.

### 3.3 Known platform limitations (Android 16/17)

* **Android 14 (API 34):** apps may mark views as *accessibility-data-sensitive*; a
  non-tool accessibility service then cannot read those views. Instagram/YouTube/TikTok do
  not currently mark their feed views, but this is under their control.
* **Android 16 (API 36):** `dispatchGesture` is blocked for sensitive views, so gesture-based
  blocking is unreliable → we rely on global actions, not gestures.
* **Android 17 (API 37) — Advanced Protection Mode (AAPM):** when the user turns AAPM on,
  the AccessibilityService API is restricted to verified accessibility tools, which
  disables Layer A entirely. The app detects AAPM (`AdvancedProtectionManager`) and warns the
  user that Layer A is degraded. **This is the single most important known limitation of the
  design**, and it is now the most important one, because the guard is the foundation.
* **Google Play policy:** distributing this app on Play requires an accessibility
  declaration, and Play forbids using accessibility to prevent a user from disabling the
  app. This project is positioned for both sideloaded and store use; the enforcement is a
  user-facing feature, not a surveillance feature, and the app never blocks the settings
  screen that would let the user turn it off.

---

## 4. Productivity feature set

| Module | Scope | Status |
| --- | --- | --- |
| Tasks | Title, notes, priority, due date/time, estimate, tags, subtask-free, completion | shipped |
| Habits | Cadence (daily / x-per-week / specific weekdays), streak, per-day log, archive | shipped |
| Goals | Target metric + current value + unit + deadline, progress bar, milestone XP | shipped |
| Schedule | Time blocks per weekday, "day plan" timeline, links blocks to focus sessions | shipped |
| Focus tracker | Pomodoro-style timer + optional on-device screen-time (UsageStats) for distraction minutes | shipped (UsageStats deferred) |
| Gamification | XP events, levels, daily streaks, level-gated rewards (badges + accent themes) | shipped |

XP economy: task `+10`, habit `+5` (+streak bonus up to `+15`), goal milestone `+50`,
focus minute `+1`, completed lockdown window `+25`, early exit from a lockdown `−20`.
Level curve: `level = floor(sqrt(xp / 100)) + 1` (fast early levels, slow late ones).

---

## 5. Technical decisions

| Area | Decision | Why |
| --- | --- | --- |
| Language / UI | Kotlin 2.2.10, Jetpack Compose, Material 3 | Required; Compose gives the minimalist black UI cheaply |
| Min SDK | 26 (Android 8.0) | `java.time` natively, no desugaring; ≥99 % device coverage |
| Compile/Target SDK | 37 | Matches the newest platform in this SDK install |
| Architecture | Single module, package-by-feature, MVVM-lite with `StateFlow` | Keeps builds fast; the project is still small |
| DI | Hand-rolled `AppContainer` (no Hilt) | Fewer moving parts, faster builds, trivial to follow |
| Persistence | Room 2.8.5 (KSP) + DataStore Preferences 1.3.0 | Room for structured data, DataStore for settings |
| Navigation | Navigation-Compose 2.10.2, string routes | Avoids the kotlinx-serialization plugin |
| Charts | Custom Compose `Canvas` | No third-party chart dependency |
| Enforcement | `AccessibilityService` (guard) + HOME-role launcher + platform screen pinning | The only shippable enforcement primitives a normal app may use |
| Privileged surface | Plain device administrator, uninstall protection only | No factory reset, no account-free device, revocable by the user |
| Scheduling | `AlarmManager` exact alarms (30-minute inexact backstop alarm) | Exactness at boundaries, reliability in between |
| Icons | Material icons *core* only | `material-icons-extended` is deprecated and large |
| Typography | **Bundled Inter** (`res/font/inter_*.ttf`) | Verified on device: this OEM ROM maps the system "sans-serif" family to a *serif* face, which broke the intended look. Bundling guarantees identical typography everywhere |
| Theme | Pure black `#000000` background, white type, one accent per level | Required by the brief; maximum contrast |

### Build-system findings (AGP 9.4.1 + Kotlin 2.2.10)

These cost real time and are easy to trip over again:

* **AGP 9 ships a built-in Kotlin implementation.** The standard
  `org.jetbrains.kotlin.android` plugin *cannot* be applied on top of it (it fails with a
  `BaseExtension` cast error). The built-in implementation has to be used.
* **KSP + built-in Kotlin**: KSP still registers its generated sources through the classic
  `kotlin.sourceSets` DSL, which AGP 9 rejects by default. The documented escape hatch
  `android.disallowKotlinSourceSets=false` in `gradle.properties` is what makes Room + KSP work.
* **API 37 changed several `DevicePolicyManager` signatures**: `setLockTaskPackages` now takes a
  `String[]` instead of a `List`, `getUserRestrictions` returns a `Bundle` instead of a `Set`,
  `getUninstallBlocked` was removed and `RoleManager.getRoleHolders` no longer exists (the default
  dialer is now read through `TelecomManager.getDefaultDialerPackage()`).
* **`material-icons-core` is no longer pulled in transitively** by `material3` and has to be
  declared explicitly.
* **`collectAsStateWithLifecycle()`** in lifecycle 2.11 requires an explicit `initialValue` when
  the receiver is a plain `Flow` (the `StateFlow` overload does not).

### Security posture

* No network permission is requested by the enforcement code paths.
* No sensitive data leaves the device; no analytics SDK.
* The accessibility service reads **only** the packages the user opted into, and the system
  settings, the dialer and the permission controller are always on the allowlist.
* A clear, in-app disclosure screen precedes enabling the accessibility service.
* The app never blocks the system settings, so the user can always revoke any of the layers.

---

## 6. Development phases

| Phase | Content | Exit criteria |
| --- | --- | --- |
| **0 — Foundations** | Gradle setup, deps, pure-black theme, typography, app shell, navigation, Room bootstrap | App builds, navigates, empty states render |
| **1 — Lockdown** | Policy controller, schedule, windows, boot survival, emergency dial, lockdown UI + schedule editor | Lockdown arms on schedule and survives reboot |
| **2 — Short-form blocker** | Accessibility service, rule engine, overlay, strict mode, blocker UI + rules editor | YouTube Shorts blocked, long-form YouTube plays, blocker toggles independently |
| **3 — Productivity** | Tasks, habits, goals, schedule, focus sessions, XP engine | Full CRUD + XP awarding working end-to-end |
| **4 — Stats & rewards** | Dashboard, statistics, streaks, rewards gallery, polish | Dashboard shows live numbers; no dead ends |
| **5 — Hardening & docs** | Edge cases, permission audits, recovery paths, final docs | PLAN/TASKS accurate, handoff written |
| **6 — Device Owner removal** | Strip every DO API, add the guard, the guardian launcher and screen pinning, rewire the UI and the docs | Everything works on a device with an account, no `adb`, no factory reset |

Each phase ends with: build passes → `PLAN.md` + `TASKS.md` updated → handoff summary.

---

## 7. Operating the app (user-facing, mirrored in-app)

```bash
# 1. install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. open the app and switch the layers you want on
#    Phone Lockdown → Enforcement layers:
#      · Accessibility guard  → "Enable the accessibility guard" (system settings)
#      · Guardian launcher    → "Set as home app"           (system dialog)
#      · Screen pinning       → toggle in Behaviour
#      · Device administrator → optional, uninstall protection only

# 3. verify what is running
adb shell dumpsys accessibility | grep -i adhdkiller
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

# emergency release of the optional administrator (escape hatch)
adb shell dpm remove-active-admin \
  com.adhdkiller.app/com.adhdkiller.app.enforcement.admin.LockdownDeviceAdminReceiver
```

There is no `dpm set-device-owner` step any more, and there never has to be one.

---

## 8. Bug log / known issues

| # | Area | Issue | Status |
| --- | --- | --- | --- |
| 1 | Lockdown | The old Device Owner design could not be granted on a device with accounts | **Fixed** — every DO API is gone; the app installs and enforces on a stock phone |
| 2 | Blocker | Instagram/Facebook Reels live in a fragment → detection is heuristic | Mitigated by strict mode (Layer B); the shipped Instagram/Facebook keywords were narrowed to `reels_viewer`/`reel_viewer` after a false-positive risk review |
| 3 | Blocker | Android 17 Advanced Protection disables the accessibility layer | Detected at runtime via `AdvancedProtectionManager`; the app warns the user |
| 4 | Lockdown | Exact alarms need `SCHEDULE_EXACT_ALARM` on Android 12+ | Falls back to inexact alarms, surfaced in the UI |
| 5 | Lockdown | The dialer, the IME, the settings and the permission controller must never be blocked | Always on the allowlist, by construction (`AppInventory.protectedPackages`) |
| 6 | Lockdown | A window that wraps past midnight stayed active for the whole day | **Found by a unit test and fixed** — the matcher now treats the gap between the end and the start as "not active" |
| 7 | UI | Every screen was drawn on top of each other | **Found on a real device** — the navigation container had no height constraint (`fillMaxWidth` only); fixed to `fillMaxSize` |
| 8 | UI | The status bar overlapped the app header | Fixed with `statusBarsPadding()` / `navigationBarsPadding()` |
| 9 | UI | The text rendered in a serif face on a Realme/ColorOS device | The OEM maps the system "sans-serif" family to a serif face; **Inter is now bundled** so the typography is identical everywhere |
| 10 | Blocker | Seeded default rules went stale when the defaults were tightened | A versioned seed migration now upgrades untouched rules and never touches rules the user edited (covered by unit tests) |
| 11 | Ops | `adb shell dpm remove-active-admin` is refused by some OEM ROMs | The Settings screen now also offers a direct link to the system device-admin settings |
| 12 | Ops | Force-stopping the app disables its accessibility service | Android behaviour, not a bug — documented in the handoff so nobody force-stops the app during testing |
| 13 | Lockdown | Screen pinning can be left with back + recents | Platform behaviour on an unmanaged device; the toggle is labelled as escapable |
| 14 | Ops | An installation that was provisioned as Device Owner cannot be uninstalled | The Settings screen detects the leftover grant and shows the revoke command |

---

## 9. Verification performed

| Check | Result |
| --- | --- |
| `:app:compileDebugKotlin` | ✅ green |
| `:app:assembleDebug` | ✅ green, APK produced |
| `:app:testDebugUnitTest` | ✅ **37/37** tests pass (window matching, XP curve, rule matching, seed migration, guard rules) |
| Install on a real device (Realme RMX5566, Android 16 / API 36) | ✅ installs, launches, no crashes in logcat |
| Room database on device | ✅ 11 tables created, data persists across restarts |
| Accessibility service binding | ✅ `AppGuard: Rules refreshed: 6 app(s)` in logcat |
| Default rule set | ✅ seeded as designed (YouTube = activity-only, Instagram = activity + narrow keywords, TikTok/Snapchat = whole app + strict) |
| Device admin activation (`dpm set-active-admin`) | ✅ admin active, detected by the app's capability audit |
| Uninstall protection | ✅ Android refused to uninstall the app while the device admin was active |
| Device Owner | ⛔ **not used any more** — no DO API remains in the codebase |
| Guardian launcher | ⚠️ implemented, **not yet exercised on a device** (see `TASKS.md` → Phase 6) |
| Screen pinning | ⚠️ implemented, **not yet exercised on a device** (see `TASKS.md` → Phase 6) |
| Lockdown screen (no DO) | ✅ degrades gracefully: countdown, emergency dial buttons, honest explanation |
| Production APK | ⏳ not built yet (the release build type is still the template's, unsigned) |

---

## 10. Next steps

1. **Exercise the new layers on a real device**: enable the accessibility guard, set the app as
   the home app, arm a window, confirm that a blocked app is closed and that the launcher only
   lists the allowed apps, then reboot and check that the lockdown comes back.
2. **Verify the direct-boot path**: after a reboot the launcher must show the lockdown screen
   *before* the first unlock.
3. **Build a signed release APK** and set up an update channel.
4. Add the **user allowlist UI** (the data layer exists, the screen does not), the
   **streak reset** on an early lockdown exit and the **screen-time (UsageStats)** statistics.
5. Consider a **notification-suppression layer** (`NotificationListenerService`) so that a
   blocked app cannot pull the user back with a notification.
