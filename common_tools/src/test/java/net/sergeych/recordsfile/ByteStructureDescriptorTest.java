package net.sergeych.recordsfile;

import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ByteStructureDescriptorTest {
    @Test
    public void packInt() throws Exception {
        ByteStructureDescriptor d = new ByteStructureDescriptor();

        d.addField("b1", 1)
                .addField("b2", 1)
                .addField("sh1", 2)
                .addField("sh2", 2)
                .addField("i1", 4)
                .addField("i2", 4);

        assertEquals(14, d.getSize());

        byte[] res = d.pack(Binder.fromKeysValues(
                "b1", 1,
                "b2", 255,
                "sh1", 1,
                "sh2", 0xF0F0,
                "i1", 0x10203040,
                "i2", 0xF0F1F2F3
        ));
        assertEquals("01 FF 00 01 F0 F0 10 20 30 40 F0 F1 F2 F3", Bytes.toHex(res));
//        Bytes.dump(res);
        Binder r = d.unpack(res);
        assertEquals(1, r.getIntOrThrow("b1"));
        assertEquals(255, r.getIntOrThrow("b2"));
        assertEquals(1, r.getIntOrThrow("sh1"));
        assertEquals(0xf0f0, r.getIntOrThrow("sh2"));
        assertEquals(0x10203040, r.getIntOrThrow("i1"));
        assertEquals(0xF0f1f2f3, r.getIntOrThrow("i2"));

        res = d.pack(Binder.fromKeysValues(
                "b1", '0',
                "b2", 255,
                "sh1", '1',
                "sh2", 0xF0F0,
                "i1", '2',
                "i2", 771001L
        ));
        r = d.unpack(res);
        assertEquals('0', r.getIntOrThrow("b1"));
        assertEquals(255, r.getIntOrThrow("b2"));
        assertEquals('1', r.getIntOrThrow("sh1"));
        assertEquals(0xF0F0, r.getIntOrThrow("sh2"));
        assertEquals('2', r.getIntOrThrow("i1"));
        assertEquals(771001, r.getIntOrThrow("i2"));
    }

    @Test
    public void unpackNumbers() throws Exception {
        ByteStructureDescriptor d = new ByteStructureDescriptor();

        d.addByteField("b")
                .addShortField("sh")
                .addIntField("i")
                .addLongField("l");

        assertEquals(15, d.getSize());

        byte[] res = d.pack(Binder.fromKeysValues(
                "b", 'S',
                "sh", 31170,
                "i", 2000000,
                "l", 0x10FFFFffffL
        ));
//        Bytes.dump(res);
        Binder r = d.unpack(res);
        assertEquals('S', r.getIntOrThrow("b"));
        assertEquals(31170, r.getIntOrThrow("sh"));
        assertEquals(2000000, r.getIntOrThrow("i"));
        assertEquals(0x10FFFFffffL, r.getLongOrThrow("l"));
    }

    @Test
    public void packBytes() throws Exception {
        ByteStructureDescriptor d = new ByteStructureDescriptor();
        d.addField("type", 1)
                .addBinaryField("bb",12);

        byte[] res = d.pack(Binder.fromKeysValues(
                "type", 17,
                "bb", "Hello world!".getBytes()
        ));
        assertEquals("11 48 65 6C 6C 6F 20 77 6F 72 6C 64 21", Bytes.toHex(res));
        Binder r = d.unpack(res);
        assertEquals(17, r.getIntOrThrow("type"));
        assertEquals("Hello world!", r.getBytesOrThrow("bb").toString());

    }

    @Test
    public void packSmallString() throws Exception {
        ByteStructureDescriptor d = new ByteStructureDescriptor();
        d.addField("type", 1)
                .addStringField("bb",19);
        byte[] res = d.pack(Binder.fromKeysValues(
                "type", 32,
                "bb", "Hello world!"
        ));
//        Bytes.dump(res);
        assertEquals("20 0C 48 65 6C 6C 6F 20 77 6F 72 6C 64 21 00 00 00 00 00 00", Bytes.toHex(res));
        Binder r = d.unpack(res);
        assertEquals(32, r.getIntOrThrow("type"));
        assertEquals("Hello world!", r.getStringOrThrow("bb"));

    }
}