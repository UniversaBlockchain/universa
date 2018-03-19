package com.icodici.crypto;

import com.icodici.crypto.digest.Crc32;
import com.icodici.crypto.digest.Digest;
import com.icodici.crypto.digest.Sha3_256;
import com.icodici.crypto.digest.Sha3_384;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Safe58;

import java.io.IOException;
import java.util.Arrays;

/**
 * The address is a short (more or less) representation of any {@link AbstractKey} that implements {@link
 * AbstractKey#updateDigestWithKeyComponents(Digest)} method. The address contains verification code, key type
 * information and the hash itself. To check that some key matches the address, create {@link KeyAddress} instance from
 * a string or packed bytes and call {@link #isMatchingKey(AbstractKey)} to check it is ok.
 * <p>
 * It is possible to keep any 4 bits of extra data, {@link #getTypeMark()}, which will also be protected by the control
 * code.
 * <p>
 * The inner structure is:
 * <p>
 * First byte: bits 0x-1..0x08 - key type mask (15 types except code 0), bits 0x10..0x80 - mark byte, [0..15]
 * <p>
 * bytes [1..48]:{@link com.icodici.crypto.digest.Sha3_384} digest of the key in question
 * <p>
 * bytes [49..53]: {@link com.icodici.crypto.digest.Crc32} control code of all previous bytes
 *
 * Packed to string the long address takes 72 characters, short version only 51.
 */
public class KeyAddress implements KeyMatcher {

    private final int keyMask;
    private final byte[] keyDigest;
    private boolean _isLong;
    private byte[] packed;
    private int typeMark;


    public KeyAddress() {
        keyMask = 0;
        keyDigest = null;
    }

    /**
     * Build new KeyAddrAddress for a given key
     *
     * @param key
     *         to calculate address from
     * @param typeMark
     *         any small number between 0 and 15 inclusive to be stored with address (will also be protected)
     * @param useSha3_384
     *         use longer but more solid hash which is more resistent to wuantum attacks
     */
    public KeyAddress(AbstractKey key, int typeMark, boolean useSha3_384) {
        this.typeMark = typeMark;
        if ((typeMark & 0xF0) != 0)
            throw new IllegalArgumentException("type mark must be in [0..15] range");

        keyMask = mask(key);

        Digest digest = useSha3_384 ? new Sha3_384() : new Sha3_256();
        _isLong = useSha3_384;

        packed = new byte[1 + 4 + digest.getLength()];
        packed[0] = (byte) (((keyMask << 4) | typeMark) & 0xFF);

        keyDigest = key.updateDigestWithKeyComponents(digest).digest();

        System.arraycopy(keyDigest, 0, packed, 1, keyDigest.length);
        Digest crc = new Crc32();
        crc.update(packed, 0, 1 + digest.getLength());
        System.arraycopy(crc.digest(), 0, packed, 1 + digest.getLength(), 4);
    }


    /**
     * Unpack an address. After construction, use {@link #getTypeMark()} and {@link #isMatchingKey(AbstractKey)} methods
     * to retrieve information.
     *
     * @param packedSource
     *         binary data holding the address
     * @throws IllegalAddressException
     *         if the adress is malformed (control code does not match or wrong type code)
     */
    public KeyAddress(byte[] packedSource) throws IllegalAddressException {
        packed = packedSource;
        typeMark = packedSource[0] & 0x0F;
        keyMask = (packedSource[0] & 0xFF) >> 4;

        if (keyMask == 0)
            throw new IllegalAddressException("keyMask is 0");

        _isLong = packedSource.length == 53;
        Digest digest = _isLong ? new Sha3_384() : new Sha3_256();

        int digestLength1 = 1 + digest.getLength();
        keyDigest = Arrays.copyOfRange(packed, 1, digestLength1);
        Digest crc = new Crc32();
        crc.update(packed, 0, digestLength1);
        if (!Arrays.equals(crc.digest(),
                           Arrays.copyOfRange(packed, digestLength1, digestLength1 + 4)))
            throw new IllegalAddressException("control code failed, address is broken");
    }

    /**
     * Unpack the string-encoded key (it actually uses {@link Safe58} to encode). See {@link
     * KeyAddress#KeyAddress(byte[])} for more.
     *
     * @param packedString
     *         string with encoded address, use {@link #toString()} to obtain packed string representation
     * @throws IllegalAddressException
     *         if the address is malformed (control code is not valid or bad type code)
     */
    public KeyAddress(String packedString) throws IllegalAddressException {
        this(Safe58.decode(packedString));
    }

    public KeyAddress(Binder binder) {
        //TODO: implement
        keyMask = 0;
        keyDigest = new byte[0];
    }

    /**
     * Check that the key matches this address, e.g. has of the same type and the digest of its components is the same.
     * The key components digest is calculated by the key self in the {@link AbstractKey#updateDigestWithKeyComponents(Digest)}
     *
     * @param key to check
     * @return true if the key matches (what means, it is the key corresponding to this address)
     */
    @Override
    public boolean isMatchingKey(AbstractKey key) {
        KeyAddress other = new KeyAddress(key, 0, _isLong);
        if (other.keyMask != keyMask)
            return false;
        if (!Arrays.equals(keyDigest, other.keyDigest))
            return false;
        return true;
    }

    /**
     * Check that the address matches key information. It DOES NOT check the typeMark {@link #getTypeMark()}! If the
     * type mark is important, check it by hand.
     *
     * @param other
     * @return
     */
    @Override
    public boolean isMatchingKeyAddress(KeyAddress other) {
        if( _isLong != other._isLong )
            throw new IllegalArgumentException("can't match addresses of different length");
        return other.keyMask == keyMask && Arrays.equals(keyDigest, other.keyDigest);
    }

    /**
     * @return true if long version SHA3-384) was used for this address
     */
    public final boolean isLong() { return _isLong; }

    /**
     * @return packed binary address
     */
    public final byte[] getPacked() {
        return packed;
    }

    /**
     * @return the type mark (between 0 and 15 inclusive)
     */
    public final int getTypeMark() {
        return typeMark;
    }

    /**
     * Get the packed string representaion. Uses {@link Safe58} to encode data provided by the {@link #getPacked()}
     * @return
     */
    @Override
    public String toString() {
        return Safe58.encode(packed);
    }

    /**
     * Each supported key has a mask that represents its type. The key that has no known mask can't be processed
     * bt the address.
     *
     * @param k key to calculate mask of.
     * @return
     */
    protected static int mask(AbstractKey k) {
        KeyInfo i = k.info();
        switch (i.getAlgorythm()) {
            case RSAPublic:
            case RSAPrivate:
                if( ((PublicKey)k).getPublicExponent() == 0x10001) {
                    int l = i.getKeyLength();
                    if (l == 2048 / 8)
                        return 0x01;
                    if (l == 4096 / 8)
                        return 0x02;
                }
                break;
        }
        throw new IllegalArgumentException("key can't be masked for address: " + i);
    }

    public static class IllegalAddressException extends Exception {
        public IllegalAddressException() {
        }

        public IllegalAddressException(String message) {
            super(message);
        }

        public IllegalAddressException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof KeyAddress) {
            KeyAddress ka = (KeyAddress) obj;

            return Arrays.equals(ka.getPacked(), getPacked());
        }
        return super.equals(obj);
    }

    static {
        DefaultBiMapper.registerAdapter(KeyAddress.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object, BiSerializer serializer) {
                return Binder.of("uaddress", serializer.serialize(((KeyAddress)object).getPacked())); //TODO: serialization is necessary
            }

            @Override
            public Object deserialize(Binder binder, BiDeserializer deserializer) {
                try {
                    return new KeyAddress(binder.getBinaryOrThrow("uaddress"));
                } catch (IllegalAddressException e) {
                    e.printStackTrace();
                    throw new IllegalArgumentException("can't reconstruct KeyAddress");
                }
            }

            @Override
            public String typeName() {
                return "KeyAddress";
            }
        });
    }
}
