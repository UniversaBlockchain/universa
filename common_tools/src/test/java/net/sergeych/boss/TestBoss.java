/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

/**
 *
 */
package net.sergeych.boss;

import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

/**
 * @author sergeych
 */
public class TestBoss {

    /**
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testIntegers() {
        assertEquals(7, ((Number) Boss.load(Bytes.fromHex("38"))).intValue());
        assertEquals(Bytes.fromHex("38"), Boss.dump(7));

        assertEquals(17, (int) Boss.load(Bytes.fromHex("88")));
        assertEquals(Bytes.fromHex("88"), Boss.dump(17));

        assertEquals(99, (int) Boss.load(Bytes.fromHex("B8 63")));
        assertEquals("B8 63", Boss.dump(99).toHex());

        assertEquals(331, (int) Boss.load(Bytes.fromHex("C0 4B 01")));
        assertEquals("C0 4B 01", Boss.dump(331).toHex());

        assertEquals(-7, (int) Boss.load(Bytes.fromHex("3A")));
        assertEquals("3A", Boss.dump(-7).toHex());

        assertEquals(-17, (int) Boss.load(Bytes.fromHex("8A")));
        assertEquals("8A", Boss.dump(-17).toHex());

        assertEquals(-99, (int) Boss.load(Bytes.fromHex("BA 63")));
        assertEquals("BA 63", Boss.dump(-99).toHex());

        assertEquals(-331, (int) Boss.load(Bytes.fromHex("C2 4B 01")));
        assertEquals("C2 4B 01", Boss.dump(-331).toHex());

        assertEquals(13457559825L,
                     (long) Boss.load(Bytes.fromHex("D8 11 11 22 22 03")));
        assertEquals("D8 11 11 22 22 03", Boss.dump(13457559825L).toHex());

        assertEquals(-13457559825L,
                     (long) Boss.load(Bytes.fromHex("DA 11 11 22 22 03")));
        assertEquals("DA 11 11 22 22 03", Boss.dump(-13457559825L).toHex());

        assertEquals(4919112987704430865L,
                     (long) Boss.load(Bytes.fromHex("F0 11 11 22 22 33 33 44 44")));
        assertEquals("F0 11 11 22 22 33 33 44 44",
                     Boss.dump(4919112987704430865L).toHex());

        assertEquals(-4919112987704430865L,
                     (long) Boss.load(Bytes.fromHex("F2 11 11 22 22 33 33 44 44")));
        assertEquals("F2 11 11 22 22 33 33 44 44",
                     Boss.dump(-4919112987704430865L).toHex());

        assertEquals(new BigInteger("97152833356252188945"),
                     Boss.load(Bytes.fromHex("F8 89 11 11 22 22 33 33 44 44 05")));
        assertEquals("F8 89 11 11 22 22 33 33 44 44 05",
                     Boss.dump(new BigInteger("97152833356252188945")).toHex());

        assertEquals(new BigInteger("-97152833356252188945"),
                     Boss.load(Bytes.fromHex("FA 89 11 11 22 22 33 33 44 44 05")));
        assertEquals("FA 89 11 11 22 22 33 33 44 44 05",
                     Boss.dump(new BigInteger("-97152833356252188945")).toHex());

        assertEquals("B0", Boss.dump(22).toHex());
        assertEquals("B8 17", Boss.dump(23).toHex());

        assertEquals("B0", Boss.dump(22).toHex());
        assertEquals(22, (int) Boss.load(Bytes.fromHex("B0")));
        assertEquals(23, (int) Boss.load(Bytes.fromHex("B8 17")));

        for (int i = 0; i < 800; i++) {
            assertEquals(i, (int) Boss.load(Boss.dump(i)));
            assertEquals(-i, (int) Boss.load(Boss.dump(-i)));
        }
        assertEquals("B8 1E", Boss.dump(30).toHex());
        assertEquals("B8 1F", Boss.dump(31).toHex());
    }

    @Test
    public void testStringsAndBinaries() {
        assertEquals("Hello", Boss.load(Bytes.fromHex("2B 48 65 6C 6C 6F")));
        assertEquals("2B 48 65 6C 6C 6F", Boss.dump("Hello").toHex());

        Bytes bb = Bytes.fromHex("00 01 02 03 04 05");
        Bytes rr = (Bytes) Boss.load(Bytes.fromHex("34 00 01 02 03 04 05"));
        assertEquals(bb.toHex(), rr.toHex());

        Bytes encoded = Boss.dump(rr);
        assertEquals("34 00 01 02 03 04 05", encoded.toHex());

        byte[] ba = new byte[]{0, 1, 2, 3, 4, 5};
        assertEquals("34 00 01 02 03 04 05", Boss.dump(ba).toHex());

        // Should pach utf8
        assertEquals("Абвгд", Boss.load(Boss.dump("Абвгд")));
    }

    @Test
    public void testConstants() {
        assertEquals(0, (int) Boss.load(Bytes.fromHex("00")));
        assertEquals("00", Boss.dump(0).toHex());

        assertEquals(true, Boss.load(Bytes.fromHex("61")));
        assertEquals("61", Boss.dump(true).toHex());

        assertEquals(false, Boss.load(Bytes.fromHex("69")));
        assertEquals("69", Boss.dump(false).toHex());

        assertEquals(1.0, Boss.load(Bytes.fromHex("11")), 1e-6);
        assertEquals("11", Boss.dump(1.0).toHex());

        assertEquals(-1.0, Boss.load(Bytes.fromHex("21")), 1e-6);
        assertEquals("21", Boss.dump(-1.0).toHex());

        assertEquals(0.0, (double) Boss.load(Bytes.fromHex("09")), 1e-6);
        assertEquals("09", Boss.dump(0.0).toHex());
    }

    @Test
    public void testArrays() {
        List data = asList(0, true, false, 1.0, -1.0, "hello!");
        assertEquals(data, Boss.load(
                Bytes.fromHex("36 00 61 69 11 21 33 68 65 6C 6C 6F 21")));
        ArrayList<Object> list = new ArrayList<Object>();
        for (Object x : data)
            list.add(x);
        assertEquals("36 00 61 69 11 21 33 68 65 6C 6C 6F 21", Boss.dump(data)
                .toHex());
        assertEquals("36 00 61 69 11 21 33 68 65 6C 6C 6F 21", Boss.dump(list)
                .toHex());

        ArrayList<Integer> iarray = new ArrayList<Integer>();
        iarray.add(10);
        iarray.add(20);
        iarray.add(1);
        iarray.add(2);
        assertEquals(iarray, Boss.load(Bytes.fromHex("26 50 A0 08 10")));
        assertEquals("26 50 A0 08 10", Boss.dump(iarray).toHex());

        byte[] ba = new byte[]{0, 1, 2, 3, 4, 5};
        byte[][] bb = new byte[][]{ba, ba};

        List x = Boss.load(Boss.dump(bb));
        assertEquals(2, x.size());
        assertArrayEquals(ba, ((Bytes) x.get(0)).toArray());
        assertArrayEquals(ba, ((Bytes) x.get(1)).toArray());
    }

    @Test
    public void testHashes() {
        Boss.Dictionary res = (Boss.Dictionary) Boss
                .load(Bytes
                              .fromHex("1F 1B 6F 6E 65 1B 74 77 6F 2B 47 72 65 61 74 61 B8 AC 69"));
        assertEquals(res.size(), 3);
        assertEquals(res.get("one"), "two");
        assertEquals(res.get("Great"), true);
        assertEquals(res.get(172), false);

        res = (Boss.Dictionary) Boss.load(Boss.dump(res));
        assertEquals(res.size(), 3);
        assertEquals(res.get("one"), "two");
        assertEquals(res.get("Great"), true);
        assertEquals(res.get(172), false);
    }

    @Test
    public void testCache() {
        List<Byte> res = Boss.load(
                Bytes.fromHex("36 2B 48 65 6C 6C 6F 2B 57 6F 72 6C 64 15 15 15 1D"));
        Object[] source = new Object[]{"Hello", "World", "Hello", "Hello",
                "Hello", "World"};
        assertArrayEquals(source, res.toArray());
        assertEquals("36 2B 48 65 6C 6C 6F 2B 57 6F 72 6C 64 15 15 15 1D", Boss
                .dump(source).toHex());

        List sub = asList("Hello", 1, 2);
        List a = asList(10, null, sub, "Hello", null, sub, sub);

        Bytes packed = Boss.dump(a);
        assertEquals("3E 50 05 1E 2B 48 65 6C 6C 6F 08 10 1D 05 15 15",
                     packed.toHex());

        assertEquals(a, Boss.load(packed));

        @SuppressWarnings("unchecked")
        Map<String, String> smap = (Map<String, String>) Boss.load(Bytes.fromHex("0F 2B 68 65 6C 6C 6F 2B 77 6F 72 6C 64"));
        assertEquals(1, smap.size());
        assertEquals("world", smap.get("hello"));
        assertEquals("0F 2B 68 65 6C 6C 6F 2B 77 6F 72 6C 64", Boss.dump(smap).toHex());
    }

    @Test
    public void testDate() {
        Boss.setUseOldDates(true);
        Date date = (Date) Boss.load(Bytes.fromHex("79 2A 24 0E 10 85"));
        assertEquals(date != null, true);
        assertEquals(date.getTime() / 1000, 1375965738L);
        assertEquals("79 2A 24 0E 10 85", Boss.dump(date).toHex());
        Boss.setUseOldDates(false);
        ZonedDateTime d = Boss.load(Bytes.fromHex("79 2A 24 0E 10 85"));
        assertEquals(true, date != null);
        assertEquals(1375965738L, d.toEpochSecond());
        assertEquals("79 2A 24 0E 10 85", Boss.dump(date).toHex());
    }

    @Test
    public void testDouble() {
        assertEquals(17.37e-111, (double) Boss.load(Bytes.fromHex("39 3C BD FC B1 F9 E2 24 29")), 1e-6);
        assertEquals("39 3C BD FC B1 F9 E2 24 29", Boss.dump(17.37e-111).toHex());
    }

    @Test
    public void testOutputStreamMode() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Boss.Writer w = new Boss.Writer(bos);
        w.setStreamMode();
        w.writeObject("String too long");
        w.writeObject("String too long");
        w.writeObject("String too long");
//		assertEquals(w.getCache().size(), 1);
        w.writeObject("test1");
        w.writeObject("test2");
        w.writeObject("test3");
        w.writeObject("test4");
        w.writeObject("test5");
        w.writeObject("test6");

        w.writeObject("test4"); // #2
        w.writeObject("test5");
        w.writeObject("test6");
        w.writeObject("test4"); // #3
        w.writeObject("test5");
        w.writeObject("test6");
        w.writeObject("test7");
//
//		System.out.println("Res: " + new Bytes(bos).toBase64() );
//		new Bytes(bos).dump();

        Boss.Reader r = new Boss.Reader(new ByteArrayInputStream(
                bos.toByteArray()));
        assertEquals("String too long", r.read());
        assertEquals("String too long", r.read());
        assertEquals("String too long", r.read());
        assertEquals("test1", r.read());
        assertEquals("test2", r.read());
        assertEquals("test3", r.read());

        assertEquals("test4", r.read());
        assertEquals("test5", r.read());
        assertEquals("test6", r.read());

//			Lg.i(" >>>> %s", x);

        assertEquals("test4", r.read());
        assertEquals("test5", r.read());
        assertEquals("test6", r.read());

        assertEquals("test4", r.read());
        assertEquals("test5", r.read());
        assertEquals("test6", r.read());

        assertEquals("test7", r.read());
    }

    @Test
    public void testBytes() {
        Bytes src = Bytes.fromHex("01 02 03 04 05 06 07 08 09 10");
        Bytes p1 = src.part(-3);
        assertEquals("08 09 10", p1.toHex());
        p1 = src.part(8);
        assertEquals("09 10", p1.toHex());
        try {
            p1 = src.part(-20);
            fail("Exception must be thrown");
        } catch (IndexOutOfBoundsException e) {
        }
        assertEquals("02 03", src.part(1, 2).toHex());
        assertEquals("09 10", src.part(-2, 20).toHex());
        assertEquals("08 09", src.part(-3, 2).toHex());
    }

    @Test
    public void testStreamModeMixed14() throws Exception {
        String src = "U1RoZSBzdHJpbmcNgVNUaGUgc3RyaW5nU1RoZSBzdHJpbmc=\n";
        Boss.Reader in = new Boss.Reader(Bytes.fromBase64(src).toArray());
        String a = in.read();
        String b = in.read();
        String c = in.read();
        String d = in.read();
        assertEquals("The string", a);
        assertEquals(a, b);
        assertEquals(a, c);
        assertEquals(a, d);
        assertTrue(a == b);
        assertTrue(a != c);
        assertTrue(c != d);
    }

    @Test
    public void cachingNestedMaps() throws Exception {
        Binder a = Binder.fromKeysValues("foo", "bar");
        Binder b = Binder.fromKeysValues("foo", "bar");
        b.put("bar", "buzz");
        Binder root = Binder.fromKeysValues("a", b, "b", b, "c", b);
        Binder res = Boss.unpack(Boss.pack(root));
        assertEquals("buzz", res.getBinderOrThrow("c").getStringOrThrow("bar"));
    }

//	@Test
//	public void testBadCase1() {
//		Bytes src = Bytes.fromBase64("L0t0aW1lc3RhbXB5IFdfEYVDaG9zdG5hbWUzZG8tMDAxU3N0YXJ0ZWRfYXR5\nbExdEYVbY29ubmVjdGlvbnPwo25vdGlmaWNhdGlvbnNfcGFzc2VkOA==");
//		src.dump();
//		Boss.Dictionary d = (Boss.Dictionary) Boss.load(src);
//		assertTrue(d != null);
//		System.out.println(": "+d);
//		System.out.println(": "+Boss.load(src.part(0x32)));
//		System.out.println(": "+Boss.load(src.part(0x3e)));
//	}
}
