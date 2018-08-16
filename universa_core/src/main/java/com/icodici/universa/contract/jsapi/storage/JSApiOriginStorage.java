package com.icodici.universa.contract.jsapi.storage;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.jsapi.JSApiHelpers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JSApiOriginStorage {

    HashId originId;

    public JSApiOriginStorage(HashId originId) {
        this.originId = originId;
    }

    public byte[] readAllBytes(String fileName) throws IOException {
        String originStoragePath = JSApiStorage.getOriginStoragePath() + JSApiHelpers.hashId2hex(this.originId);
        Path path = Paths.get(originStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(originStoragePath)) {
            if (path.toFile().exists()) {
                byte[] res = Files.readAllBytes(path);
                return res;
            }
        }
        throw new IOException("file '"+fileName+"' not found in origin storage");
    }

    public void writeNewFile(String fileName, byte[] data) throws IOException {
        String originStoragePath = JSApiStorage.getOriginStoragePath() + JSApiHelpers.hashId2hex(this.originId);
        Path path = Paths.get(originStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(originStoragePath)) {
            if (!path.toFile().exists()) {
                path.toFile().getParentFile().mkdirs();
                path.toFile().createNewFile();
                Files.write(path, data);
                return;
            }
        }
        throw new IOException("unable to write file '"+fileName+"'");
    }

    public void rewriteExistingFile(String fileName, byte[] data) throws IOException {
        String originStoragePath = JSApiStorage.getOriginStoragePath() + JSApiHelpers.hashId2hex(this.originId);
        Path path = Paths.get(originStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(originStoragePath)) {
            if (path.toFile().exists()) {
                path.toFile().delete();
                path.toFile().createNewFile();
                Files.write(path, data);
                return;
            }
        }
        throw new IOException("file '"+fileName+"' not found in origin storage");
    }

}
