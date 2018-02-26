package com.icodici.crypto;

import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DigestsTest {

    /**
     * @see <a href="https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Standards-and-Guidelines/documents/examples/SHA1.pdf">Reference vectors</a>.
     */
    private Map<String, String> vectorsSha1 = new HashMap<String, String>() {{
        put("abc",
                "A9993E36 4706816A BA3E2571 7850C26C 9CD0D89D");
        put("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
                "84983E44 1C3BD26E BAAE4AA1 F95129E5 E54670F1");
    }};

    /**
     * @see <a href="https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Standards-and-Guidelines/documents/examples/SHA256.pdf">Reference vectors</a>.
     */
    private Map<String, String> vectorsSha256 = new HashMap<String, String>() {{
        put("abc",
                "BA7816BF 8F01CFEA 414140DE 5DAE2223 B00361A3 96177A9C B410FF61 F20015AD");
        put("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
                "248D6A61 D20638B8 E5C02693 0C3E6039 A33CE459 64FF2167 F6ECEDD4 19DB06C1");
    }};

    /**
     * @see <a href="https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Standards-and-Guidelines/documents/examples/SHA512.pdf">Reference vectors</a>.
     */
    private Map<String, String> vectorsSha512 = new HashMap<String, String>() {{
        put("abc",
                "DDAF35A1 93617ABA CC417349 AE204131" +
                        "12E6FA4E 89A97EA2 0A9EEEE6 4B55D39A 2192992A 274FC1A8" +
                        "36BA3C23 A3FEEBBD 454D4423 643CE80E 2A9AC94F A54CA49F");
        put("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu",
                "8E959B75 DAE313DA 8CF4F728 14FC143F\n" +
                        "8F7779C6 EB9F7FA1 7299AEAD B6889018 501D289E 4900F7E4\n" +
                        "331B99DE C4B5433A C7D329EE B6DD2654 5E96E55B 874BE909");
    }};

    /**
     * @see <a href="https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Standards-and-Guidelines/documents/examples/SHA512_256.pdf">Reference vectors</a>.
     */
    private Map<String, String> vectorsSha512_256 = new HashMap<String, String>() {{
        put("abc",
                "53048E26 81941EF9 9B2E29B7 6B4C7DAB E4C2D0C6 34FC6D46 E0E2F131 07E7AF23");
        put("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu",
                "3928E184 FB8690F8 40DA3988 121D31BE 65CB9D3E F83EE614 6FEAC861 E19B563A");
    }};

    /**
     * @see <a href="https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Standards-and-Guidelines/documents/examples/SHA3-256_Msg0.pdf">1</a>.
     */
    private Map<String, String> vectorsSha3_256 = new HashMap<String, String>() {{
        put("",
                "A7FFC6F8BF1ED76651C14756A061D662F580FF4DE43B49FA82D80A4B80F8434A");
        put("abc",
                "3a985da74fe225b2 045c172d6bd390bd 855f086e3e9d525b 46bfe24511431532");
        put("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
                "41c0dba2a9d62408 49100376a8235e2c 82e1b9998a999e21 db32dd97496d3376");
        put("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu",
                "916f6061fe879741 ca6469b43971dfdb 28b1a32dc36cb325 4e812be27aad1d18");
    }};

    /**
     * @see <a href="https://ru.wikisource.org/wiki/%D0%93%D0%9E%D0%A1%D0%A2_%D0%A0_34.11%E2%80%942012#%D0%9F%D1%80%D0%B8%D0%BB%D0%BE%D0%B6%D0%B5%D0%BD%D0%B8%D0%B5_%D0%90_(%D1%81%D0%BF%D1%80%D0%B0%D0%B2%D0%BE%D1%87%D0%BD%D0%BE%D0%B5)_%D0%9A%D0%BE%D0%BD%D1%82%D1%80%D0%BE%D0%BB%D1%8C%D0%BD%D1%8B%D0%B5_%D0%BF%D1%80%D0%B8%D0%BC%D0%B5%D1%80%D1%8B">GOST</a>;
     * note the vectors are reversed; and both the message and the hash vectors are byte forms.
     */
    private Map<String, String> vectorsSha3_256_gost = new HashMap<String, String>() {{
        put("32313039383736353433323130393837363534333231303938373635343332" +
                        "3130393837363534333231303938373635343332313039383736353433323130",
                "00557be5e584fd52a449b16b0251d05d27f94ab76cbaa6da890b59d8ef1e159d");
        put("fbe2e5f0eee3c820fbeafaebef20fffbf0e1e0f0f520e0ed20e8ece0ebe5f0f2f120fff0eeec20f1" +
                        "20faf2fee5e2202ce8f6f3ede220e8e6eee1e8f0f2d1202ce8f0f2e5e220e5d1",
                "508f7e553c06501d749a66fc28c6cac0b005746d97537fa85d9e40904efed29d");
    }};

    /**
     * @see <a href="https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Standards-and-Guidelines/documents/examples/SHA3-256_Msg0.pdf">1</a>.
     */
    private Map<String, String> vectorsStreebog_256 = new HashMap<String, String>() {{
        // Samples from https://en.wikipedia.org/wiki/Streebog
        put("",
                "3f539a213e97c802cc229d474c6aa32a825a360b2a933a949fd925208d9ce1bb");
        put("The quick brown fox jumps over the lazy dog",
                "3e7dea7f2384b6c5a3d0e24aaa29c05e89ddd762145030ec22c71a6db8b2c1f4");
        put("The quick brown fox jumps over the lazy dog.",
                "36816a824dcbe7d6171aa58500741f2ea2757ae2e1784ab72c5c3c6c198d71da");
        // Samples from https://tools.ietf.org/html/rfc6986
        // Samples from https://github.com/adegtyarev/streebog/tree/master/examples
        put("",
                "3f539a213e97c802cc229d474c6aa32a825a360b2a933a949fd925208d9ce1bb");
        put("012345678901234567890123456789012345678901234567890123456789012",
                "9d151eefd8590b89daa6ba6cb74af9275dd051026bb149a452fd84e5e57b5500");
        put("\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00" +
                        "\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00" +
                        "\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00" +
                        "\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00\00",
                "df1fda9ce83191390537358031db2ecaa6aa54cd0eda241dc107105e13636b95");
    }};

    /**
     * @see <a href="https://csrc.nist.gov/projects/cryptographic-standards-and-guidelines/example-values">Reference vectors</a>.
     */
    @Test
    public void testShaFamily() throws Exception {
        for (final Map.Entry<String, String> vector : vectorsSha1.entrySet()) {
            assertArrayEquals(vector.getKey(), Hex.decode(vector.getValue()), new Sha1().digest(vector.getKey()));
        }
        for (final Map.Entry<String, String> vector : vectorsSha256.entrySet()) {
            assertArrayEquals(vector.getKey(), Hex.decode(vector.getValue()), new Sha256().digest(vector.getKey()));
        }
        for (final Map.Entry<String, String> vector : vectorsSha512.entrySet()) {
            assertArrayEquals(vector.getKey(), Hex.decode(vector.getValue()), new Sha512().digest(vector.getKey()));
        }
        for (final Map.Entry<String, String> vector : vectorsSha512_256.entrySet()) {
            assertArrayEquals(vector.getKey(), Hex.decode(vector.getValue()), new Sha512_256().digest(vector.getKey()));
        }
        for (final Map.Entry<String, String> vector : vectorsSha3_256.entrySet()) {
            assertArrayEquals(vector.getKey(), Hex.decode(vector.getValue()), new Sha3_256().digest(vector.getKey()));
        }

        assertEquals(64, new Sha1().getChunkSize());
        assertEquals(64, new Sha256().getChunkSize());
        assertEquals(128, new Sha512().getChunkSize());
        assertEquals(128, new Sha512_256().getChunkSize());
        assertEquals(136, new Sha3_256().getChunkSize());

        assertEquals(20, new Sha1().getLength());
        assertEquals(32, new Sha256().getLength());
        assertEquals(64, new Sha512().getLength());
        assertEquals(32, new Sha512_256().getLength());
        assertEquals(32, new Sha3_256().getLength());
    }

    @Test
    public void testGostFamily() throws Exception {
        for (final Map.Entry<String, String> vector : vectorsStreebog_256.entrySet()) {
            assertArrayEquals(vector.getKey(), Hex.decode(vector.getValue()), new Gost3411_2012_256().digest(vector.getKey()));
        }

        for (final Map.Entry<String, String> vector : vectorsSha3_256_gost.entrySet()) {
            final byte[] key = Hex.decode(vector.getKey());
            final byte[] value = Hex.decode(vector.getValue());
            final byte[] keyRev = new byte[key.length];
            final byte[] valueRev = new byte[value.length];
            for (int i = 0; i < key.length; i++) {
                keyRev[key.length - i - 1] = key[i];
            }
            for (int i = 0; i < value.length; i++) {
                valueRev[value.length - i - 1] = value[i];
            }
            assertArrayEquals(Hex.toHexString(keyRev), valueRev, new Gost3411_2012_256().digest(keyRev));
        }

        assertEquals(64, new Gost3411_2012_256().getChunkSize());

        assertEquals(32, new Gost3411_2012_256().getLength());
    }
}
