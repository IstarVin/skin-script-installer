# Plan: ML Skin Script Installer — Android App

## Stack
- Kotlin + Jetpack Compose (minSdk 30 / Android 11+)
- Hilt (DI), Room + KSP (persistence), Coroutines/Flow (async)
- Shizuku 13.1.5 via AIDL UserService (privileged file access to Android/data/com.mobile.legends/)
- SAF (Storage Access Framework) for folder import
- Internal storage (filesDir) for imported scripts + backups

## Key architecture decisions
- Scripts stored in `filesDir/scripts/<id>/` (internal)
- Backups stored in `filesDir/backups/<installationId>/<relative_path>` (internal)
- Shizuku service handles read/write to ML's Android/data path using ParcelFileDescriptor bidirectional pattern:
    - Backup: service.openFileForRead(mlPath) → app streams to filesDir
    - Install: app opens source file → pfd passed to service.writeFile(pfd, mlDest)
    - This avoids the Binder 1MB buffer limit without needing external storage

## IFileService AIDL methods
- destroy() = 16777114 (required by Shizuku)
- openFileForRead(path): ParcelFileDescriptor = 1
- writeFile(in ParcelFileDescriptor source, destPath): boolean = 2
- deleteFile(path): boolean = 3
- listFiles(path): List<String> = 4
- mkdirs(path): boolean = 5
- exists(path): boolean = 6

## Room Entities
- SkinScript: id, name, importedAt, storagePath(filesDir/scripts/<id>/)
- Installation: id, scriptId, installedAt, restoredAt?, status(installed|restored)
- InstalledFile: id, installationId, destPath(ML absolute), wasOverwrite, backupPath?

## ML target path
`/storage/emulated/0/Android/data/com.mobile.legends/files/dragon2017/assets/`

## Phases
1. Project setup + dependencies
2. Data layer (Room)
3. Shizuku AIDL service
4. Script import (SAF + structure validation from Python)
5. Install + backup logic
6. Restore logic
7. UI (ScriptList, ScriptDetail, Settings screens)
