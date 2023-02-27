package com.icodici.crypto;

import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.JsonTool;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class KeyAddressTest {

    static AbstractKey key1 = TestKeys.privateKey(2).getPublicKey();

    @Test
    public void isMatchingKey() throws KeyAddress.IllegalAddressException {
        testMatch(true);
        testMatch(false);
    }

    private void testMatch(boolean use384) throws KeyAddress.IllegalAddressException {
        KeyAddress a = key1.address(use384, 7);
        KeyAddress b = new KeyAddress(a.toString());
        assertEquals(7, b.getTypeMark());
        assertEquals(7, a.getTypeMark());
        assertTrue(b.isMatchingKey(key1));
        assertTrue(a.isMatchingKeyAddress(b));
        assertTrue(b.isMatchingKeyAddress(a));

        assertTrue(key1.isMatchingKey(key1));
        assertTrue(key1.isMatchingKeyAddress(a));
        assertTrue(key1.isMatchingKeyAddress(b));


        byte[] pack = a.getPacked();
        pack[7] ^= 71;
        try {
            new KeyAddress(pack);
            fail("must throw error on spoiled address");
        } catch (KeyAddress.IllegalAddressException e) {
        }
    }

    @Test
    public void serialize() throws IOException {
        KeyAddress a = key1.address(true, 8);
        KeyAddress b = key1.address(false, 9);
        Boss.Reader r = new Boss.Reader(Boss.dump(a, b).toArray());
        KeyAddress x = r.read();
        KeyAddress y = r.read();
        assertEquals(8, x.getTypeMark());
        assertEquals(9, y.getTypeMark());
        assertTrue(x.isMatchingKey(key1));
        assertTrue(y.isMatchingKeyAddress(b));
    }

    void printKeyVector(int size) {
        PrivateKey prk = new PrivateKey(size);
        PublicKey puk = prk.getPublicKey();
        System.out.println("RSA " + size + "\n\n" + Base64.encodeLines(prk.pack()) + "\n");
        System.out.println("long " + puk.getLongAddress().toString() + "\n");
        System.out.println("shrt " + puk.getShortAddress().toString() + "\n");
        System.out.println("==end===");
    }

    /**
     * Comatibility testing for extending KeyAddress space for longer RSA keys
     */
    class KAVerctor {
        int size;
        PrivateKey privateKey;
        PublicKey publicKey;

        KeyAddress shortAddress;
        KeyAddress longAddress;

        KAVerctor(String src) throws EncryptionError, KeyAddress.IllegalAddressException {
            String parts[] = src.split("\n\n");
            if (!parts[0].startsWith("RSA ")) throw new IllegalArgumentException("bad alrogythm");
            size = Integer.valueOf(parts[0].substring(4));
            privateKey = new PrivateKey(Base64.decodeLines(parts[1]));
            publicKey = privateKey.getPublicKey();
            if (parts.length > 2 && parts[2].startsWith("long "))
                longAddress = new KeyAddress(parts[2].substring(5));
            if (parts.length > 3 && parts[3].startsWith("shrt "))
                shortAddress = new KeyAddress(parts[3].substring(5));
        }

        Binder toBinder() {
            return Binder.fromKeysValues(
                    "size", size,
                    "packedKey", privateKey.packToBase64String(),
                    "longAddressString", publicKey.getLongAddress().toString(),
                    "shortAddressString", publicKey.getShortAddress().toString()
            );
        }
    }

    KAVerctor[] kaVerctors() throws Exception {
        String[] vectors = keyAddressVectorsSource.split("==end===\n");
        KAVerctor result[] = new KAVerctor[vectors.length];
        int i = 0;
        for (String x : vectors) {
            result[i++] = new KAVerctor(x);
        }
        return result;
    }

    @Test
    public void comatibility() throws Exception {
//        printKeyVector(2048);
//        printKeyVector(4096);
//        printKeyVector(8192);
        KAVerctor[] vectors = kaVerctors();
        assertEquals(vectors[0].size, 2048);
        assertEquals(vectors[1].size, 4096);
        assertEquals(vectors[2].size, 8192);
        assertNotNull(vectors[1].longAddress);
        assertNotNull(vectors[1].shortAddress);
        assertNotNull(vectors[0].longAddress);
        assertNotNull(vectors[0].shortAddress);
        assertNull(vectors[2].longAddress);
        assertNull(vectors[2].shortAddress);
        for( KAVerctor v: vectors) {
            if( v.longAddress != null ) {
                assertEquals(v.publicKey.getShortAddress(), v.shortAddress);
                assertEquals(v.publicKey.getLongAddress(), v.longAddress);
//                System.out.println("Checked "+v.size+" for address compatibility");
            }
            else {
                v.publicKey.getShortAddress();
                v.publicKey.getLongAddress();
//                System.out.println("Checked "+v.size+" for KeyAddress creation");
            }
        }
//        Binder [] pp = new Binder[3];
//        for( int i=0; i < 3; i++ ) pp[i] = vectors[i].toBinder();
//        String jstr = JsonTool.toJsonString(pp);
//        System.out.println(jstr);
    }

    @Test
    public void zeroesTest() {
        KeyAddress z0 = KeyAddress.shortZero;
        KeyAddress z1 = KeyAddress.longZero;
        System.out.println(z0.toString());
//        Bytes.dump(z0.getPacked());
        System.out.println(z1.toString());
//        Bytes.dump(z1.getPacked());
        for( PrivateKey k: TestKeys.privateKeys) {
            PublicKey pk = (PublicKey) key1.getPublicKey();
            assertFalse(pk.getLongAddress().isMatchingKeyAddress(z1));
            assertFalse(pk.getShortAddress().isMatchingKeyAddress(z0));
            assertFalse(pk.isMatchingKeyAddress(z0));
            assertFalse(pk.isMatchingKeyAddress(z1));
        }
    }


    private String keyAddressVectorsSource =
            "RSA 2048\n" +
                    "\n" +
                    "JgAcAQABvID9CDGXtrLxsi45pqDPNUJ2TDXzRJrYcwTxjLF+9ddQvQ7ZoMFh8G43Oro2LHg2\n" +
                    "ZKiztIgi2Ygc15OmTXpEIUhvI9O3DkvnMLNvQ5NUYL7P5ktYxsd+DsBbAqlmgYrgpIVC5aer\n" +
                    "1L1LcS+5M+6ikWq+UX6aFE9eS0WoaQTSfxWSv7yA1C9VcVEi48pDFBqlJJ5lE200mP+essyK\n" +
                    "b5EwamD6L/N6Odt1rnfqJsDOzDtQKXl8wgu/aY/CmBY75yUE4ywQRw2mUCCqY1LhUQ41xwIN\n" +
                    "j1dD2xzWJ7qYZ/0CFmFIkTmv4Hcr8IZQhFnvdZaW0Zk0Bx9tAiliorxy2aFuUMT+JG0=\n" +
                    "\n" +
                    "long JnKv298KvPgmiKfPRx8h5vVGYjPTfKs8iuwJbhwatQNgsSd4NAsuwn9kVUJ1YgvJvgWKgmBw\n" +
                    "\n" +
                    "shrt ZHYYuENFT9e1UTktZ1b9KPrVrqdRCoXAxRLrfKBuLarEUUzESF\n" +
                    "\n" +
                    "==end===\n" +
                    "RSA 4096\n" +
                    "\n" +
                    "JgAcAQABxAAB3kozmthDEUUZ9YoZDQh+D/EPRS7z6AWjeNNLBCtcN/SLABaWgNhb800miZIM\n" +
                    "JpY2BNz3+SNblc3POc+Wrk5ZkPg47scxtFUkU0x6Pg9JvasoJSXQMqpO6k0kpbQtfIr32wm5\n" +
                    "XStErDCWkHzS0OAwHCeG4llqEnCM7nVXIxSEXpa3fOZHhbAd9IWM4E+HbkyPhWN1EAUp6a0a\n" +
                    "2IU09TaAgTZcUYQOhMPK6cM4CrJJ3ubs/rgxgHzh45E5BlBclRBcmNJYxDQ4AcTbnVx7xRYW\n" +
                    "9eLd609LTk3c6Ufttnrd9+koeclXolsLI/7lKWRJnpWWBgzvZBZ4WNHc3w50buAaO8QAAdRi\n" +
                    "qVxJ1cOr3+CAxxr2D3Pmf1Yt1uNE22Jq+lzWX3KohMvaEjTRH5DcuGQpjvg5rBkFQObWadhb\n" +
                    "YdPPWZQVaYmbRWKAPaX8xlQJhPf/zG3O1WjgWLutnC8F7XfcdpiaoWlmgdbbx5iMQ4+Vlwix\n" +
                    "TrXbGZwTFZ94UHB6tkUt/0OBKrptkvfv3qBkE0d99M3CpTvMIpSDOUoiYvkjHyqfzodHzYqb\n" +
                    "j+i9r4TUBaUElGrv2mgifHTqbUMfFQNP/85jVgosFD28InfcwwfAl9d7DfLxqSYgELYrsEmD\n" +
                    "ZxkhMvTIwZw6C59GY2gcU80RZmfndeZBPWAsvp/5IWt57dUU3dU=\n" +
                    "\n" +
                    "long bkmb2isfMoXFFs5wFzY3TsfJAkdrRCmNAaFU2EBAxMwoDtSAwST8eqHtesh9NW8qwLdNhVoa\n" +
                    "\n" +
                    "shrt 26X6QPQxJvz7rWarU1KonQ5JkAU4BKKCJoUyNpnwKBn4JgAH5Sw\n" +
                    "\n" +
                    "==end===\n" +
                    "RSA 8192\n" +
                    "\n" +
                    "JgAcAQABxAAC99KCimkzv77oDlxlFN8S4VER1S+v9N2hZUnZZr43/RLIbC+9W2t9bEor4iPN\n" +
                    "o8nowZ+q7OA9wXJmfpI3ocdadhYWOyLIsCIGScvgJufDCEMkYtIt/lJBgbpKLzWWohD10pFO\n" +
                    "3CiBG55zKcuy2wD3S1Yq7Ac9fMVMzg6hNpf2wvhoqwCpBY0kJTfBTW29eq7WcDaSUb2o2JPe\n" +
                    "q1uK0pUfUcvX2UcfGk0ABDRD8F7FZ/FVudCjOLW1gGU/5UTSgyp0gjMhPFEQWfLOGzkgj3wx\n" +
                    "Oldfcof6IJVbQOXrJQbdOqwr2WQ0vsWrgZWTeKF/eLjxJvqgqnTcj1JPdJL3Fz9KkEa/Tu8A\n" +
                    "hSVCmJlxp24MRC08D3zSP7D6peOltEfIi0xdrqDVCH+TkeL7JV0Ro5K1D/GZAK94Cnq3xXAX\n" +
                    "20lJ8yZYExZuj8XYuP1wHAfcRxXTwW6R/qahgUjwMPB59zH03FlBLsohuzq6UBl7YKvcQibT\n" +
                    "XMyp7Es7Zcky9zpfEJxa+XXWYVnyycWr3oee2B/5A6YGKnqzXnRkwAhOi8/fofysva/4cbih\n" +
                    "IJ0D9yjj1C2weFx/h8ngevZjlkiVRAgpQHA/2j8PjDNibM4uXPFaLW80DXjMjr64peTsXG7d\n" +
                    "t/VFEn6CVIpUwLTDTf9JQWVQRo4jtvNm7BZz10zO2PP+q4nEAALuEsSqRqYVnMv7p8uym7uY\n" +
                    "2HJU+bX2TLEGg0NYp8Pv+JCd5jtjDcoysOfyv550cxBiTyECmvwgG//6P+fJNOy6+56TPVRz\n" +
                    "M3jP3DqXMMfbAeJpWgIPf8whB/y0IbY5rE3IvitRJRDwYTSeDgDSSwyua5UmG/woF9T5q/tM\n" +
                    "nEjWMs/1zl9oPxtLwA8Ewna+mOIHVo93H3B0tZyHQkOGBi3WGt+e6+ZGzBl1HXO74W5+qCPC\n" +
                    "Wxxe7Hd0sEn2YzRfRWRcU0oVJrouafwfvOnLeOl3IkDlHAFlmcHgHEM8KsKx7zrim/CFZH2H\n" +
                    "RSeS/QmcMBiV9y2yO2JS6BdV4sRnYtwBEzkXByqGXW1sMsw+KWuNfOPOsOGgMXXxfdrLL0BB\n" +
                    "fgYjOtUvxOn0Gxw1kcWyyD0DWVeyTshnR21dwZq7qS6RdiBjzDKvDffmmaanSJcDRFHiuBlo\n" +
                    "+DTnIJgai8bcHOtJfd5BLUZm/lawDAnI+jGF0H/lMbz/1VH3FTrlCgxk5RrpwoxxwIuo2P5s\n" +
                    "zpFEmP3MwnN2XPZesU4VovULFzWuvkf1a9QrxDwZrdskAjhQYXsFRVjI/9kPSVA2LwT1lFgq\n" +
                    "7nvLwhYC/pHkgtTzgLFP8RnZF3qnN6WAWPUN1utca7N2zKJxI+JtV639xEG28TNCyg4lwihy\n" +
                    "GTRAuKlg9P3tmw==\n" +
                    "\n";

}