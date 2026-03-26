
## Plan: ML Skin Script Installer — Android App

**What & Why**: A native Android app that replaces the Python/ADB script. The user imports skin script folders, the app manages them, and installs/restores them to the Mobile Legends assets path. Since `Android/data/com.mobile.legends/` is restricted on Android 11+, **Shizuku** is used for elevated file access via an AIDL UserService.

---

### Phase 1 — Project Setup

1. Create new Android project: Kotlin, Jetpack Compose, `minSdk 30`
2. Add dependencies to `build.gradle.kts`:
    - Compose BOM + Navigation Compose
    - Room + KSP processor
    - Hilt + KSP processor
    - `dev.rikka.shizuku:api:13.1.5` + `dev.rikka.shizuku:provider:13.1.5`
    - Coroutines + kotlinx-serialization
3. Add `ShizukuProvider` to `AndroidManifest.xml`
4. Set up Hilt `@HiltAndroidApp` on Application class

---

### Phase 2 — Data Layer (Room)

5. Define three Room entities:
    - `SkinScript` — `id, name, importedAt, storagePath` (e.g. `filesDir/scripts/<id>/`)
    - `Installation` — `id, scriptId, installedAt, restoredAt?, status` (`installed` | `restored`)
    - `InstalledFile` — `id, installationId, destPath` (absolute ML path), `wasOverwrite: Boolean`, `backupPath?`
6. Create DAOs for each entity + `AppDatabase`
7. `ScriptRepository` — wraps DAOs, exposes `Flow<List<SkinScript>>` and mutation methods

---

### Phase 3 — Shizuku AIDL Service

8. Define `IFileService.aidl` with these methods (*parallel with step 5*):
    - `void destroy() = 16777114` — required by Shizuku to kill the service process
    - `ParcelFileDescriptor openFileForRead(String path) = 1` — service opens privileged ML path → app streams it to backup
    - `boolean writeFile(in ParcelFileDescriptor source, String destPath) = 2` — app opens source file → service writes it to ML path (avoids Binder 1 MB limit)
    - `boolean deleteFile(String path) = 3`
    - `List<String> listFiles(String path) = 4`
    - `boolean mkdirs(String path) = 5`
    - `boolean exists(String path) = 6`
9. `FileService : IFileService.Stub` implementation (runs as Shizuku shell process)
10. `ShizukuManager` class: binder lifecycle listeners, permission check/request, `bindUserService` / `unbindUserService`

---

### Phase 4 — Script Import

11. `ImportScriptUseCase`:
    - Opens SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE`)
    - Reads folder tree via `DocumentFile` API
    - **Ports the Python `validate_and_fix_script_structure` logic**: check for `Android/` root; if missing, scan for `Art` folder and wrap it in proper `Android/data/com.mobile.legends/files/dragon2017/assets/` path
    - Copies files to `filesDir/scripts/<uuid>/`
    - Inserts `SkinScript` record into Room

---

### Phase 5 — Install & Backup

12. `InstallScriptUseCase` (*depends on 3 + 4*):
    - Walk all files under `filesDir/scripts/<id>/Android/data/com.mobile.legends/files/dragon2017/assets/`
    - For each file:
        - Check if destination exists via `ShizukuService.exists(mlPath)`
        - If yes → **backup**: call `service.openFileForRead(mlPath)`, stream into `filesDir/backups/<installationId>/<relPath>`; record `InstalledFile(wasOverwrite=true, backupPath=...)`
        - Open source file, get `ParcelFileDescriptor`, call `service.writeFile(pfd, mlPath)`; record `InstalledFile(wasOverwrite=false)`
    - Ensure parent dirs exist via `service.mkdirs(...)`
    - Emit `StateFlow<InstallProgress>` (current file index + total) for UI progress bar
    - On completion → insert `Installation(status=installed)`

---

### Phase 6 — Restore

13. `RestoreScriptUseCase` (*depends on 5*):
    - Load all `InstalledFile` rows for given `installationId`
    - For each file where `wasOverwrite=false` → `service.deleteFile(destPath)`
    - For each file where `wasOverwrite=true` → open `backupPath`, call `service.writeFile(pfd, destPath)`, then delete backup file
    - Update `Installation(status=restored, restoredAt=now)`
    - Delete backup directory for this installation from `filesDir/backups/<installationId>/`

---

### Phase 7 — UI (Jetpack Compose)

14. **Script List Screen** — `LazyColumn` of imported scripts; each card shows name, imported date, and a status chip (`Installed` / `Not Installed` / `Restored`); FAB opens file picker
15. **Script Detail Screen** — name, file tree preview, `Install` button (disabled if already installed), `Restore` button (only if `status=installed`), install progress bar
16. **Settings Screen** — Shizuku status indicator (`Running` / `Not Running`), `Grant Permission` button, current ML path (possibly editable for future hero support)

---

### Relevant Files to Create

- `app/src/main/aidl/…/IFileService.aidl` — AIDL interface
- `app/src/main/java/…/service/FileService.kt` — Shizuku UserService impl
- `app/src/main/java/…/service/ShizukuManager.kt`
- `app/src/main/java/…/data/db/` — Room entities, DAOs, `AppDatabase.kt`
- `app/src/main/java/…/data/repository/ScriptRepository.kt`
- `app/src/main/java/…/domain/` — `ImportScriptUseCase.kt`, `InstallScriptUseCase.kt`, `RestoreScriptUseCase.kt`
- `app/src/main/java/…/ui/screens/` — List, Detail, Settings screens + ViewModels
- `app/src/main/AndroidManifest.xml` — `ShizukuProvider` + `MANAGE_EXTERNAL_STORAGE` permission request

---

### Verification

1. Import a raw-directory skin script → appears in the list with correct name
2. Import a malformed script (no `Android/` root but has `Art/` inside) → app normalizes structure silently
3. Install a script → `adb shell ls /sdcard/Android/data/com.mobile.legends/files/dragon2017/assets/` shows the new files
4. Close and reopen the app → `Installed` status persists
5. Install a second script that overlaps files → overwritten files are backed up in `filesDir/backups/`
6. Restore → original files are back, new-only files are deleted
7. Shizuku not running → app shows an actionable error, install button is disabled

---

### Decisions / Scope

- **In scope**: import, install, restore, Shizuku-based file access, Room tracking
- **Out of scope (for now)**: network download of scripts, multi-script install batching, per-file restore granularity
- **App package name** to be decided (used in AIDL + Manifest provider authority)
- Scripts are stored in app internal storage (`filesDir`) — not user-accessible, but safe from accidental deletion

---

### Open Questions
1. **Shizuku not installed** — should the app offer a fallback (e.g. `MANAGE_EXTERNAL_STORAGE`) or just hard-require Shizuku? The `MANAGE_EXTERNAL_STORAGE` permission can also grant access to `Android/data/` on some ROMs but is unreliable on stock Android 11+.
    - Answer: Hard require
2. **Script metadata** — should the app show a thumbnail or hero name parsed from the folder name, or is raw folder name display sufficient?
    - Answer: Raw folder name is fine for v1, can add parsing later if needed