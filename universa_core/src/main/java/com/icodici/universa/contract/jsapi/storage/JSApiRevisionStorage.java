package com.icodici.universa.contract.jsapi.storage;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.jsapi.JSApiHelpers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JSApiRevisionStorage {

    HashId currentId;
    HashId parentId;

    public JSApiRevisionStorage(HashId currentId, HashId parentId) {
        this.currentId = currentId;
        this.parentId = parentId;
    }

    public byte[] readAllBytes(String fileName) throws IOException {
        String revisionStoragePath = JSApiStorage.getRevisionStoragePath() + JSApiHelpers.hashId2hex(this.currentId);
        Path path = Paths.get(revisionStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(revisionStoragePath)) {
            if (path.toFile().exists()) {
                byte[] res = Files.readAllBytes(path);
                return res;
            }
        }
        throw new IOException("file '"+fileName+"' not found in revision storage");
    }

    public byte[] readAllBytesFromParent(String fileName) throws IOException {
        if (this.parentId == null)
            throw new IOException("file '"+fileName+"' parentId is null");
        String revisionStoragePath = JSApiStorage.getRevisionStoragePath() + JSApiHelpers.hashId2hex(this.parentId);
        Path path = Paths.get(revisionStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(revisionStoragePath)) {
            if (path.toFile().exists()) {
                byte[] res = Files.readAllBytes(path);
                return res;
            }
        }
        throw new IOException("file '"+fileName+"' not found in parent revision storage");
    }

    public void writeNewFile(String fileName, byte[] data) throws IOException {
        String revisionStoragePath = JSApiStorage.getRevisionStoragePath() + JSApiHelpers.hashId2hex(this.currentId);
        Path path = Paths.get(revisionStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(revisionStoragePath)) {
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
        String revisionStoragePath = JSApiStorage.getRevisionStoragePath() + JSApiHelpers.hashId2hex(this.currentId);
        Path path = Paths.get(revisionStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(revisionStoragePath)) {
            if (path.toFile().exists()) {
                path.toFile().delete();
                path.toFile().createNewFile();
                Files.write(path, data);
                return;
            }
        }
        throw new IOException("file '"+fileName+"' not found in revision storage");
    }

}
