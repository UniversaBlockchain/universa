package net.sergeych.tools;

import net.sergeych.utils.Bytes;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class FilenameToolTest {

    @Test
    public void test() throws Exception {
        String str1 = "/home/user/first.last/test";
        String str2 = "/home/user/first.last/test_1";
        String str3 = "/home/user/first.last/test_1.unicon";

        FilenameTool ft = new FilenameTool(str1);
        ft.addSuffixToBase("_1");
        assertEquals(str2,ft.toString());

        ft.setExtension("unicon");
        assertEquals(str3,ft.toString());


        str1 = "/home/user/firstlast/test";
        str2 = "/home/user/firstlast/test_1";
        str3 = "/home/user/firstlast/test_1.unicon";

        ft = new FilenameTool(str1);
        ft.addSuffixToBase("_1");
        assertEquals(str2,ft.toString());

        ft.setExtension("unicon");
        assertEquals(str3,ft.toString());

        str1 = "/home/user/firstlast/test.yml";
        str2 = "/home/user/firstlast/test_1.yml";
        str3 = "/home/user/firstlast/test_1.unicon";

        ft = new FilenameTool(str1);
        ft.addSuffixToBase("_1");
        assertEquals(str2,ft.toString());

        ft.setExtension("unicon");
        assertEquals(str3,ft.toString());

        str1 = "test.yml";
        str2 = "test_1.yml";
        str3 = "test_1.unicon";

        ft = new FilenameTool(str1);
        ft.addSuffixToBase("_1");
        assertEquals(str2,ft.toString());

        ft.setExtension("unicon");
        assertEquals(str3,ft.toString());

        str1 = ".bashrc";
        str2 = ".bashrc_1";
        str3 = ".bashrc_1.sh";

        ft = new FilenameTool(str1);
        ft.addSuffixToBase("_1");
        assertEquals(str2,ft.toString());

        ft.setExtension("sh");
        assertEquals(str3,ft.toString());

        str1 = "~/.bashrc";
        str2 = "~/.bashrc_1";
        str3 = "~/.bashrc_1.sh";

        ft = new FilenameTool(str1);
        ft.addSuffixToBase("_1");
        assertEquals(str2,ft.toString());

        ft.setExtension("sh");
        assertEquals(str3,ft.toString());

        str1 = "~/.bashrc.sh";
        str2 = "~/.bashrc_1.sh";
        str3 = "~/.bashrc_1.bak";

        ft = new FilenameTool(str1);
        ft.addSuffixToBase("_1");
        assertEquals(str2,ft.toString());

        ft.setExtension("bak");
        assertEquals(str3,ft.toString());
    }

}
