# AuraLauncher

A home-screen launcher: pages of apps and widgets, a dock, folders, an app drawer with search and categories, icon packs and shapes, focus modes, notification dots, and backup/restore. Kotlin, Jetpack Compose, Room.

## Build
Open the folder in Android Studio and run, or from a terminal:
```
gradlew assembleDebug
```
After installing, press Home and choose AuraLauncher (or use the *Set as default* banner).

## Using it
| Do this | Where | What happens |
|---|---|---|
| Swipe up | home | App drawer (configurable in Settings → Gestures) |
| Swipe down | home | Notification shade (configurable) |
| Tap an app | anywhere | Opens it |
| Hold an app, let go | home, dock, drawer | Actions: app shortcuts, add to home/dock, remove, hide, app info, uninstall |
| Hold an app, then drag | home | Move it; drop on another app to make a folder, on a folder to add it |
| Hold empty space | home | Add widget · Wallpaper · Home settings |
| Tap a folder | home | Open it; ✎ renames, 🗑 deletes, hold an app to take it out |

In the **drawer**, type to search (Enter opens the top result). The search also finds app shortcuts such as "Compose email". Toggle list or grid, sort by name, usage or install date, and browse by category tabs.

**Settings** (hold empty space → Home settings): grid columns, rows and pages, icon size, labels, search bar, status bar, dock size, gestures, drawer layout, notification dots, icon theme (shape for all icons + installed icon packs), focus modes, hidden apps, and backup/restore.

## Notes
- The home screen shows your real wallpaper through the window, so nothing needs a storage permission.
- Newly installed apps appear immediately; uninstalled apps are removed from the home screen, dock and folders automatically.
- Shrinking the grid or page count moves any icons that no longer fit into free cells instead of hiding them.
- Notification dots need Notification access (Settings → Notifications).
- Widgets aren't included in backups: Android ties each widget to the install that created it, so re-add them after restoring.
- Swipe-down-for-notifications uses a hidden Android API, so some heavily customised OEM ROMs ignore it.
