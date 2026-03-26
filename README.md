# Skin Script Installer

Android app for importing, organizing, installing, and restoring Mobile Legends skin script folders on Android 11+ devices.

The app replaces the older Python + ADB workflow with a native Kotlin/Jetpack Compose application. It uses:

- **SAF (Storage Access Framework)** to import folders from user-selected locations
- **Shizuku** to access the restricted `Android/data/com.mobile.legends/...` path
- **Room** to track imported scripts, installations, and backup metadata
- **Internal app storage** to safely store imported scripts and restore backups

## Features

- Import a skin script folder with the system folder picker
- Normalize several common script folder layouts during import
- Browse imported scripts in a Compose UI
- Preview an imported script's file tree
- Install files into the Mobile Legends assets directory through Shizuku
- Back up overwritten files before installation
- Restore the previous state for a completed installation
- Track script/install/restore state locally with Room
- Manage Shizuku connection and permission status from the Settings screen

## Target path

The app installs files into the following Mobile Legends assets directory:

```text
/storage/emulated/0/Android/data/com.mobile.legends/files/dragon2017/assets/
```

## Requirements

### Device/runtime requirements

- Android **11+** (`minSdk = 30`)
- **Shizuku** installed and running
- Shizuku permission granted to this app
- A valid skin script folder to import

Because `Android/data/...` is restricted on modern Android versions, this app is designed around **Shizuku** rather than direct file access.

## Quick start

1. Install and start **Shizuku** on your device using the method supported by your setup.
2. Launch **Skin Script Installer**.
3. Open **Settings**.
4. Confirm that:
   - Shizuku is running
   - Permission is granted
   - File Service is connected
5. Go back to the script list.
6. Tap **+** and pick a skin script folder.
7. Open the imported script.
8. Tap **Install**.
9. If needed later, return to the same script and tap **Restore**.

## Import behavior

The import flow mirrors the Python reference in `docs/ref/skin_script.py`.

During import, the app accepts a few script layouts and stores them in internal app storage under a normalized structure.

### Supported input layouts

#### 1. Full Mobile Legends path already present
If the selected folder already contains:

```text
Android/data/com.mobile.legends/files/dragon2017/assets
```

it is copied as-is.

#### 2. Nested folder that contains an `Art` directory
If the selected folder does **not** contain the full `Android/...` path, the importer recursively searches for an `Art` folder.

When it finds one, it copies the **parent of `Art`** into the expected Mobile Legends assets path.

#### 3. Raw content folder
If neither of the above is found, the app treats the selected folder as raw asset content and wraps it inside the expected Mobile Legends assets path.

## Install and restore flow

### Install

When you install a script, the app:

1. Finds all files under the imported script's normalized assets directory
2. Connects to the privileged Shizuku file service
3. Checks whether each target file already exists on the device
4. Backs up any file that would be overwritten
5. Writes imported files into the Mobile Legends assets directory
6. Records installation metadata in Room

### Restore

When you restore a script, the app:

- deletes files that were newly added during install
- restores files that had been overwritten from the backup copy
- marks the installation as restored in the local database
- removes the backup directory for that installation

## Internal storage layout

Imported data is kept in the app's internal storage.

```text
filesDir/
├── scripts/<script-id>/
│   └── Android/data/com.mobile.legends/files/dragon2017/assets/...
└── backups/<installation-id>/
    └── <relative-path-of-overwritten-files>
```

Room database file:

```text
skin_script_installer.db
```

## App architecture

### UI

- **Jetpack Compose** UI
- Navigation between:
  - Script list
  - Script detail
  - Settings

### Domain layer

- `ImportScriptUseCase`
- `InstallScriptUseCase`
- `RestoreScriptUseCase`

### Data layer

- Room entities:
  - `SkinScript`
  - `Installation`
  - `InstalledFile`
- `ScriptRepository` as the main data access abstraction

### Privileged file access

- `ShizukuManager` handles binder lifecycle, permission, and service binding
- `IFileService.aidl` defines privileged file operations
- `FileService` performs read/write/delete/mkdir/exists operations under Shizuku

## Project structure

```text
SkinScriptInstaller/
├── app/
│   ├── src/main/aidl/com/istarvin/skinscriptinstaller/IFileService.aidl
│   ├── src/main/java/com/istarvin/skinscriptinstaller/
│   │   ├── data/
│   │   ├── di/
│   │   ├── domain/
│   │   ├── service/
│   │   └── ui/
│   └── build.gradle.kts
├── docs/
│   ├── plan.md
│   ├── plan2.md
│   └── ref/skin_script.py
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## Tech stack

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Hilt
- Room + KSP
- Coroutines / Flow
- Shizuku
- AndroidX DocumentFile (SAF)

## Development setup

### Prerequisites

- Android Studio
- Android SDK for the project's configured API levels
- JDK **21** for the Gradle daemon/toolchain in this repo

### Build debug APK

```bash
cd "/home/aj/AndroidStudioProjects/SkinScriptInstaller"
./gradlew :app:assembleDebug
```

### Helpful Gradle commands

```bash
cd "/home/aj/AndroidStudioProjects/SkinScriptInstaller"
./gradlew tasks
./gradlew test
./gradlew :app:assembleDebug
```

## Main screens

### Script list

- Shows imported scripts
- Shows current status (`Not Installed`, `Installed`, `Restored`)
- Lets you import a new folder
- Lets you delete an imported script from local storage

### Script detail

- Shows import/install timestamps
- Shows install/restore state
- Shows a file tree preview
- Lets you install or restore
- Shows install/restore progress

### Settings

- Shows whether Shizuku is available
- Shows whether permission is granted
- Shows whether the Shizuku file service is connected
- Lets you request permission or reconnect the file service

## Limitations and notes

- The app currently targets the **Mobile Legends** assets path shown above.
- Shizuku is effectively required for the intended workflow on Android 11+.
- Imported scripts and backups are stored in internal app storage, so they are not meant for direct user file management.
- The importer is designed to handle common folder structures, but malformed or incomplete scripts may still fail to install correctly.
- This repository currently does **not** include a license file.

## Reference docs

- `docs/plan.md` - implementation plan and scope
- `docs/plan2.md` - architecture summary
- `docs/ref/skin_script.py` - original Python workflow that inspired the import logic

## Current project status

At the current repository state, a debug build succeeds with:

```bash
./gradlew :app:assembleDebug
```

If you want, the next improvement could be adding screenshots, an APK installation section, or a contributor-focused development guide.
