# AuraLauncher — Commercial-grade feature pass

Straight answer to "is this ready to sell": **closer, not fully there.** This pass
adds the highest-impact things a paying user would actually notice missing — a
dock, notification badges, drawer sort/view options, and full backup/restore. What
genuinely remains out of reach of any single build session (vs. Nova Launcher's
years of dedicated development) is listed honestly at the bottom, not glossed over.

## What's new in this pass

**Dock** — a persistent row of apps (3-6 slots, configurable in Settings) that
stays visible across all pages, exactly like every real launcher's dock. Add apps
to it via long-press → "Add to dock" in the drawer. Long-press a dock icon to
remove it. Built on the same grid table as the pages (under a reserved page
number), so no new database table or migration was needed.

**Notification badges** — a small red dot appears on any icon (home grid, dock, or
drawer) whose app currently has an active notification. Requires granting
Notification Access (Settings → Notifications → grant access) — same mechanism
HaptiKit and StatusBar+ use, no way around that permission on non-root Android.
Deliberately a dot, not a number: an accurate unread count needs parsing every
app's own notification format, which is far more fragile than just knowing
"something's pending."

**Drawer: sort modes + grid/list toggle** — A-Z, Most Used (tracked via real
launch counts), or Recently Installed (via `PackageManager`'s actual install
timestamp, not guessed). Toggle between the existing list view and a new 4-column
icon grid view via the icon in the drawer's top bar.

**Full backup & restore** — Settings → Backup & restore. Exports your entire
layout (grid, dock, folders, focus modes, icon overrides, and every setting) to a
single JSON file via the system "Save As" dialog; restore reads it back the same
way. One honest limitation stated plainly in-app: hosted widgets are NOT restored
automatically, because a widget's binding ID is issued per-install by
`AppWidgetHost` and can't be transferred to a fresh install or another device —
silently "restoring" old IDs would just create broken widget cells, so instead the
app tells you to re-add widgets manually after a restore.

**Hide status bar** — optional immersive mode, swipe down from the top to reveal
it temporarily. **Home screen search bar** — optional, tap to jump into the
drawer with the keyboard ready, Pixel-launcher style.

## New/changed files
```
AuraLauncher/app/src/main/java/com/auralauncher/app/
├── notifications/AuraNotificationListenerService.kt  ← NEW
├── prefs/AppUsageTracker.kt                           ← NEW
├── backup/BackupService.kt                            ← NEW
├── settings/LauncherSettingsManager.kt                ← REWRITTEN (dock/sort/view/badges/statusbar/JSON)
├── data/LauncherDao.kt                                ← MODIFIED (one-shot reads + wipe queries for backup)
├── data/LauncherRepository.kt                         ← MODIFIED (dock methods, usage tracking hook, backup wrappers)
├── MainActivity.kt                                    ← REWRITTEN (notification access, status bar, backup file pickers)
├── AndroidManifest.xml                                ← MODIFIED (registers the notification listener service)
└── ui/
    ├── components/NotificationBadge.kt                ← NEW
    ├── HomeScreen.kt                                   ← REWRITTEN (dock, badges, search bar)
    ├── AppDrawerScreen.kt                               ← REWRITTEN (sort, grid/list, dock-adding, badges)
    └── SettingsScreen.kt                                ← REWRITTEN (all new toggles)
```

## Setup
Copy all files above to their listed paths. No new Gradle dependencies (JSON uses
Android's built-in `org.json`, window-inset APIs are already in `core-ktx`). **No
database migration** — dock reuses the existing grid table, everything else new is
SharedPreferences-backed. Just rebuild.

## What's still a genuine, honest gap vs. Nova/Smart Launcher
Naming these plainly rather than letting the feature list imply more than what's
built:
- **Gesture customization is still just swipe up/down.** Nova supports pinch,
  two-finger swipe, double-tap, and per-app icon swipe actions — a meaningfully
  bigger gesture-detection system than what's here.
- **No web or contact search** in the drawer — app-name search and shortcuts only.
- **No scroll/transition animation packs** — page swipes use Compose's default
  pager motion, not a curated set of alternate transitions.
- **Page count is still fixed at 3**, not user-configurable.
- **No nested folders**, no folder background/color customization.
- **Icon pack support covers shape + real appfilter.xml artwork, but not a pack's
  "request missing icons" flow** or calendar-icon (auto-updating date icon) entries.
- **No cloud sync** — backup/restore is manual, file-based only.
- These aren't oversights; they're the difference between "a solid, genuinely
  useful launcher" (what this now is) and "a decade of a paid team's refinement"
  (what Nova/Smart Launcher actually represent). Closing all of them fully is a
  much bigger, ongoing effort than any single pass — happy to keep working through
  this list in priority order if you tell me which matters most to you.

## Status: substantially closer to commercial quality — not a claim of full parity
