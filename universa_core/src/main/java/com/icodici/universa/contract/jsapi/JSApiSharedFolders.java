package com.icodici.universa.contract.jsapi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JSApiSharedFolders {

    private JSApiExecOptions execOptions;

    public JSApiSharedFolders(JSApiExecOptions execOptions) {
        this.execOptions = execOptions;
    }

    public byte[] readAllBytes(String fileName) throws IOException {
        for (String sharedFolderPath : execOptions.sharedFolders) {
            Path path = Paths.get(sharedFolderPath);
            path = path.resolve(fileName);
            if (path.normalize().startsWith(sharedFolderPath)) {
                if (path.toFile().exists()) {
                    byte[] res = Files.readAllBytes(path);
                    return res;
                }
            }
        }
        throw new IOException("file '"+fileName+"' not found in shared folders");
    }

    public void writeNewFile(String fileName, byte[] data) throws IOException {
        if (execOptions.sharedFolders.size() > 0) {
            String sharedFolderPath = execOptions.sharedFolders.get(0);
            Path path = Paths.get(sharedFolderPath);
            path = path.resolve(fileName);
            if (path.normalize().startsWith(sharedFolderPath)) {
                if (!path.toFile().exists()) {
                    path.toFile().getParentFile().mkdirs();
                    path.toFile().createNewFile();
                    Files.write(path, data);
                    return;
                }
            }
            throw new IOException("unable to write file '"+fileName+"'");
        } else {
            throw new IOException("no shared folder provided");
        }
    }

    public void rewriteExistingFile(String fileName, byte[] data) throws IOException {
        for (String sharedFolderPath : execOptions.sharedFolders) {
            Path path = Paths.get(sharedFolderPath);
            path = path.resolve(fileName);
            if (path.normalize().startsWith(sharedFolderPath)) {
                if (path.toFile().exists()) {
                    path.toFile().delete();
                    path.toFile().createNewFile();
                    Files.write(path, data);
                    return;
                }
            }
        }
        throw new IOException("file '"+fileName+"' not found in shared folders");
    }

}
