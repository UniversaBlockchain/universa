package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.PrivateKey;
import net.sergeych.boss.Boss;
import net.sergeych.utils.Bytes;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.security.SecureRandom;
import java.util.ArrayList;

/**
 * The class capable of serialization and deserialization of {@link RSAOAEPPrivateKey} to BOSS format and back,
 * to support the .unikey file format.
 */
public class UnikeyFactory {

    /**
     * Given the .unikey-format byte array with the private key, create the {@link RSAOAEPPrivateKey}.
     */
    @Nullable
    static RSAOAEPPrivateKey rsaOaepPKFromUnikey(@NonNull byte[] bytes) {
        assert bytes != null;

        try {
            final ArrayList unpackedFromBoss = Boss.load(bytes);

            assert ((Integer) unpackedFromBoss.get(0)) == 0;

            final byte[]
                    e = ((Bytes) unpackedFromBoss.get(1)).toArray(),
                    p = ((Bytes) unpackedFromBoss.get(2)).toArray(),
                    q = ((Bytes) unpackedFromBoss.get(3)).toArray();

            return new RSAOAEPPrivateKey(
                    e, p, q,
                    RSAOAEPPrivateKey.DEFAULT_OAEP_HASH, RSAOAEPPrivateKey.DEFAULT_MGF1_HASH,
                    new SecureRandom());
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Given the .unikey-format byte array with the private key, create the {@link PrivateKey}.
     */
    @Nullable
    public static PrivateKey fromUnikey(@NonNull byte[] bytes) {
        assert bytes != null;
        try {
            return new PrivateKey(bytes);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Given the {@link RSAOAEPPrivateKey}, create the .unikey-format byte array.
     */
    @NonNull
    static byte[] toUnikey(@NonNull RSAOAEPPrivateKey privateKey) {
        assert privateKey != null;
        return privateKey.pack();
    }

    /**
     * Given the {@link RSAOAEPPrivateKey}, create the .unikey-format byte array.
     */
    @NonNull
    public static byte[] toUnikey(@NonNull PrivateKey privateKey) {
        assert privateKey != null;
        return privateKey.pack();
    }
}
