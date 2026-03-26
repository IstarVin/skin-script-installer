package com.istarvin.skinscriptinstaller;

import android.os.ParcelFileDescriptor;

interface IFileService {
    void destroy() = 16777114;
    ParcelFileDescriptor openFileForRead(String path) = 1;
    boolean writeFile(in ParcelFileDescriptor source, String destPath) = 2;
    boolean deleteFile(String path) = 3;
    List<String> listFiles(String path) = 4;
    boolean mkdirs(String path) = 5;
    boolean exists(String path) = 6;
}

