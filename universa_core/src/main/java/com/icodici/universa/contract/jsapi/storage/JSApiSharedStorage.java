package com.icodici.universa.contract.jsapi.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JSApiSharedStorage {

    public byte[] readAllBytes(String fileName) throws IOException {
        String sharedStoragePath = JSApiStorage.getSharedStoragePath();
        Path path = Paths.get(sharedStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(sharedStoragePath)) {
            if (path.toFile().exists()) {
                byte[] res = Files.readAllBytes(path);
                return res;
            }
        }
        throw new IOException("file '"+fileName+"' not found in shared storage");
    }

    public void writeNewFile(String fileName, byte[] data) throws IOException {
        String sharedStoragePath = JSApiStorage.getSharedStoragePath();
        Path path = Paths.get(sharedStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(sharedStoragePath)) {
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
        String sharedStoragePath = JSApiStorage.getSharedStoragePath();
        Path path = Paths.get(sharedStoragePath);
        path = path.resolve(fileName);
        if (path.normalize().startsWith(sharedStoragePath)) {
            if (path.toFile().exists()) {
                path.toFile().delete();
                path.toFile().createNewFile();
                Files.write(path, data);
                return;
            }
        }
        throw new IOException("file '"+fileName+"' not found in shared storage");
    }

}
