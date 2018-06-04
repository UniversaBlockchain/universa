package net.sergeych.tools;

import net.sergeych.utils.Bytes;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class FileToolTest {

    @Test
    public void checkFileRenaming() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir");
        byte[] contents = Bytes.random(64).getData();
        byte[] contents_1 = Bytes.random(64).getData();
        byte[] contents_2 = Bytes.random(64).getData();
        byte[] contents_3 = Bytes.random(64).getData();
        String path = tempDir + "/" +   "test_file_33.unicon";
        String path_1 = tempDir + "/" + "test_file_33_1.unicon";
        String path_2 = tempDir + "/" + "test_file_33_2.unicon";
        String path_2_1 = tempDir + "/" + "test_file_33_2_1.unicon";
        String path_3 = tempDir + "/" + "test_file_33_3.unicon";
        silent(() -> {Files.delete(Paths.get(path));return 0;});
        silent(() -> {Files.delete(Paths.get(path_1));return 0;});
        silent(() -> {Files.delete(Paths.get(path_2));return 0;});
        silent(() -> {Files.delete(Paths.get(path_2_1));return 0;});
        silent(() -> {Files.delete(Paths.get(path_3));return 0;});
        assertEquals(path, FileTool.writeFileContentsWithRenaming(path, contents));
        assertEquals(path_1, FileTool.writeFileContentsWithRenaming(path, contents_1));
        assertEquals(path_2, FileTool.writeFileContentsWithRenaming(path, contents_2));
        assertEquals(path_3, FileTool.writeFileContentsWithRenaming(path, contents_3));
        assertArrayEquals(contents, Files.readAllBytes(Paths.get(path)));
        assertArrayEquals(contents_1, Files.readAllBytes(Paths.get(path_1)));
        assertArrayEquals(contents_2, Files.readAllBytes(Paths.get(path_2)));
        assertArrayEquals(contents_3, Files.readAllBytes(Paths.get(path_3)));
        assertEquals(path_2_1, FileTool.moveFileWithRenaming(path_1, path_2));
        assertEquals(false, Files.exists(Paths.get(path_1)));
        assertArrayEquals(contents_2, Files.readAllBytes(Paths.get(path_2)));
        assertArrayEquals(contents_1, Files.readAllBytes(Paths.get(path_2_1)));
    }

    private static <T> T silent(Callable<T> block) {
        try {
            return block.call();
        } catch (Exception ex) {
        }
        return null;
    }

}
