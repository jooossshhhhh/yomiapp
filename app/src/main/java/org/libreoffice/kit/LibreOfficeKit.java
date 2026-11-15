/* -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This file is part of the LibreOffice project.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.kit;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;

public final class LibreOfficeKit {
    private static final String LOGTAG = LibreOfficeKit.class.getSimpleName();
    private static AssetManager mgr;
    private static volatile boolean initializeDone = false;

    private LibreOfficeKit() {
    }

    public static void initializeLibrary() {
    }

    private static native boolean initializeNative(
            String dataDir,
            String cacheDir,
            String apkFile,
            AssetManager mgr
    );

    public static native ByteBuffer getLibreOfficeKitHandle();

    public static native void putenv(String string);

    public static native void redirectStdio(boolean state);

    /**
     * Initializes LibreOfficeKit from any Android Context (Activity or Service).
     *
     * @return true if initialization succeeded or was already completed.
     */
    public static synchronized boolean init(Context context, String fontConfigPath) {
        if (initializeDone) {
            return true;
        }

        Context appContext = context.getApplicationContext();
        mgr = appContext.getResources().getAssets();

        ApplicationInfo applicationInfo = appContext.getApplicationInfo();
        String dataDir = applicationInfo.dataDir;
        String cacheDir = appContext.getCacheDir().getAbsolutePath();
        String apkFile = appContext.getPackageResourcePath();

        Log.i(LOGTAG, "Initializing LibreOfficeKit, dataDir=" + dataDir);
        redirectStdio(true);

        File loTmpDir = new File(cacheDir, "lo_tmp");
        if (!loTmpDir.exists()) {
            loTmpDir.mkdirs();
        }
        putenv("TMPDIR=" + loTmpDir.getAbsolutePath());
        putenv("TEMP=" + loTmpDir.getAbsolutePath());

        if (fontConfigPath != null && !fontConfigPath.isEmpty()) {
            putenv("FONTCONFIG_FILE=" + fontConfigPath);
        }

        if (!initializeNative(dataDir, cacheDir, apkFile, mgr)) {
            Log.e(LOGTAG, "Initialize native failed!");
            return false;
        }

        if (fontConfigPath != null && !fontConfigPath.isEmpty()) {
            // Re-assert FONTCONFIG_FILE in case initializeNative sets a default path.
            putenv("FONTCONFIG_FILE=" + fontConfigPath);
        }

        initializeDone = true;
        return true;
    }

    static {
        NativeLibLoader.load();
    }
}

final class NativeLibLoader {
    private static boolean done = false;

    private NativeLibLoader() {
    }

    static synchronized void load() {
        if (done) {
            return;
        }
        System.loadLibrary("nspr4");
        System.loadLibrary("plds4");
        System.loadLibrary("plc4");
        System.loadLibrary("nssutil3");
        System.loadLibrary("freebl3");
        System.loadLibrary("sqlite3");
        System.loadLibrary("softokn3");
        System.loadLibrary("nss3");
        System.loadLibrary("nssckbi");
        System.loadLibrary("nssdbm3");
        System.loadLibrary("smime3");
        System.loadLibrary("ssl3");

        System.loadLibrary("c++_shared");
        System.loadLibrary("lo-native-code");
        done = true;
    }
}

