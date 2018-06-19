package net.sergeych.tools;

import net.sergeych.utils.Bytes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Helper tool for writing files with overwrite protection.
 */
public class FilenameTool {

    private final List<String> parts;


    public static List<String> getFilenameParts(String filename) {
        String extension = "";
        String path = "";
        int extPos = filename.lastIndexOf(".");
        int pathPos = filename.lastIndexOf(File.separator);

        if(extPos < pathPos)
            extPos = -1;

        if (extPos >= 0)
            extension = filename.substring(extPos+1);

        if (pathPos >= 0)
            path = filename.substring(0,pathPos);

        String base = filename.substring(path.isEmpty() ? 0 : path.length() + 1,filename.length()-(extension.isEmpty() ? 0 : extension.length()+1));

        //handing names starting with .
        if(base.isEmpty() && !extension.isEmpty()) {
            base = "."+extension;
            extension = "";
        }
        return Arrays.asList(path,base, extension);
    }

    public FilenameTool(String filename) {
        parts = getFilenameParts(filename);
    }

    public FilenameTool addSuffixToBase(String suffix) {
        parts.set(1,parts.get(1)+suffix);
        return this;
    }

    public FilenameTool setExtension(String newExtension) {
        parts.set(2,newExtension);
        return this;
    }

    @Override
    public String toString() {
        String path = parts.get(0);
        String base = parts.get(1);
        String ext = parts.get(2);
        StringBuilder sb = new StringBuilder("");
        if(!path.isEmpty()) {
            sb.append(path);
            sb.append(File.separator);
        }

        sb.append(base);

        if(!ext.isEmpty()) {
            sb.append(".");
            sb.append(ext);
        }
        return sb.toString();
    }
}
