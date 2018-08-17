package com.icodici.universa.contract.jsapi.storage;

import java.io.File;

public class JSApiStorage {

    private static String getStorageRootPath() {
        return System.getProperty("user.home") + File.separator + ".universaStorage" + File.separator;
    }

    public static String getSharedStoragePath() {
        return getStorageRootPath() + "shared" + File.separator;
    }

    public static String getOriginStoragePath() {
        return getStorageRootPath() + "origin" + File.separator;
    }

    public static String getRevisionStoragePath() {
        return getStorageRootPath() + "revision" + File.separator;
    }

}
