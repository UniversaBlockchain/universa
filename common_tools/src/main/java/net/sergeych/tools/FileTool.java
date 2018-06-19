package net.sergeych.tools;

import net.sergeych.utils.Bytes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper tool for writing files with overwrite protection.
 */
public class FileTool {

    /**
     * Wtires byte[] contents to specified file. If this file already exists - tries to create the same file with suffix _1, _2, etc.
     * @param path full file path to write to
     * @param contents byte[]
     * @return new file name if success, null if fails
     */
    public static String writeFileContentsWithRenaming(String path, byte[] contents) {

        try {
            if (!writeFileContents(path, contents)) {
                for (int iSuf = 1; iSuf <= 9000; ++iSuf) {
                    String newFilename = new FilenameTool(path).addSuffixToBase("_"+iSuf).toString();
                    if (writeFileContents(newFilename, contents)) {
                        return newFilename;
                    }
                }
            } else {
                return path;
            }
        } catch (Exception e) {
            //do nothing
        }
        //to many files like {path}, or exception. try to create random unique filename
        String randomFilename = new FilenameTool(path).addSuffixToBase("_" + Bytes.random(32).toHex().replaceAll(" ", "")).toString();
        try {
            if (writeFileContents(randomFilename, contents))
                return randomFilename;
        } catch (Exception e) {
            //do nothing
        }
        //can't save file
        return null;
    }

    /**
     * Gets contents of one file and calls {{@link #writeFileContentsWithRenaming(String, byte[])}} to write it on new filepath.
     * If writes done successfully - remove source file.
     * @param pathFrom full file path to get contents
     * @param pathTo full file path to write to
     * @return new file name if success, null if fails
     */
    public static String moveFileWithRenaming(String pathFrom, String pathTo) {
        try {
            byte[] contents = Files.readAllBytes(Paths.get(pathFrom));
            String newFilename = writeFileContentsWithRenaming(pathTo, contents);
            if (newFilename != null)
                Files.delete(Paths.get(pathFrom));
            return newFilename;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean writeFileContents(String path, byte[] contents) throws Exception {
        File file = new File(path);
        if (file.createNewFile()) {
            FileOutputStream outputStream = new FileOutputStream(file, false);
            outputStream.write(contents);
            outputStream.close();
            return true;
        }
        return false;
    }
}
