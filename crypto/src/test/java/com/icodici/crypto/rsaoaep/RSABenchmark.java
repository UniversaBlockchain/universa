package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.HashType;
import net.sergeych.tools.Hashable;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Test performance of RSAEngine implementation.
 * Reference data:
 * <p>
 * {@link org.spongycastle.crypto.engines.RSAEngine} running on 2.8 GHz Intel Core i7:
 * Encryption: 0.74091035 ms
 * Decryption: 42.92876898 ms
 * Signing: 43.26741285 ms
 * Checking signature: 0.79823066 ms
 * <p>
 * {@link com.icodici.crypto.rsaoaep.scrsa.NativeRSACoreEngine} running on 2.8 GHz Intel Core i7:
 * Encryption: 0.23073088 ms
 * Decryption: 8.78989374 ms
 * Signing: 8.92703047 ms
 * Checking signature: 0.3433173 ms
 */
public class RSABenchmark {

    private static final int warmupRepetition = 10;
    private static final int repetitions = 100;

    private final RSAOAEPPrivateKey randomPrivateKey;
    private final RSAOAEPPublicKey randomPublicKey;

    private RSABenchmark() {
        System.out.printf("Initializing RSA... ");
        randomPrivateKey = new RSAOAEPPrivateKey();

        (randomPrivateKey).generate(
                4096,
                BigIntegers.asUnsignedByteArray(BigInteger.valueOf(65537)),
                1,
                HashType.SHA512,
                HashType.SHA512);

        randomPublicKey = (RSAOAEPPublicKey) randomPrivateKey.getPublicKey();

        System.out.printf(" done!\n");
    }

    void run() throws EncryptionError {
        final SecureRandom rng = new SecureRandom();
        final byte[] message = new byte[randomPrivateKey.getMaxBlockSize()];

        long totalSpentInEncryption = 0l;
        long totalSpentInDecryption = 0l;
        long totalSpentInSigning = 0l;
        long totalSpentInCheckingSignature = 0l;

        for (int i = 0; i < warmupRepetition + repetitions; i++) {
            boolean warmingUp = (i < warmupRepetition);
            if (!warmingUp) {
                System.out.printf("%s of %s\n", i - warmupRepetition + 1, repetitions);
            }

            // Setup; non-measured
            {
                randomPrivateKey.resetDecryptor();
                randomPublicKey.resetEncryptor();
                // Create random message
                rng.nextBytes(message);
            }
            // Iteration; measured
            {
                final long startTime = System.nanoTime();
                final byte[] encrypted = randomPublicKey.encrypt(message);
                final long afterEncryptionTime = System.nanoTime();
                final byte[] decrypted = randomPrivateKey.decrypt(encrypted);
                final long afterDecryptionTime = System.nanoTime();
                final byte[] signed = randomPrivateKey.sign(message, HashType.SHA512);
                final long afterSigningTime = System.nanoTime();
                final boolean signatureValid = randomPublicKey.checkSignature(message, signed, HashType.SHA512);
                final long afterCheckSignatureTime = System.nanoTime();

                if (!Arrays.equals(decrypted, message) || !signatureValid) {
                    throw new AssertionError(String.format("Cryptography problem for message %1s, private key %2s, public key %3s",
                            Hex.toHexString(message),
                            hashableToString(randomPrivateKey),
                            hashableToString(randomPublicKey))
                    );
                }

                if (!warmingUp) {
                    totalSpentInEncryption += (afterEncryptionTime - startTime);
                    totalSpentInDecryption += (afterDecryptionTime - afterEncryptionTime);
                    totalSpentInSigning += (afterSigningTime - afterDecryptionTime);
                    totalSpentInCheckingSignature += (afterCheckSignatureTime - afterSigningTime);
                }
            }
        }

        // Done!

        System.out.printf("%s iterations, each iteration took:\n" +
                        "          Encryption: %s ms\n" +
                        "          Decryption: %s ms\n" +
                        "             Signing: %s ms\n" +
                        "  Checking signature: %s ms\n",
                repetitions,
                // Per-operation time = (totalSpentInOperation / repetitions) / 1000000
                new BigDecimal(totalSpentInEncryption).divide(new BigDecimal(repetitions)).divide(new BigDecimal(1000000)),
                new BigDecimal(totalSpentInDecryption).divide(new BigDecimal(repetitions)).divide(new BigDecimal(1000000)),
                new BigDecimal(totalSpentInSigning).divide(new BigDecimal(repetitions)).divide(new BigDecimal(1000000)),
                new BigDecimal(totalSpentInCheckingSignature).divide(new BigDecimal(repetitions)).divide(new BigDecimal(1000000))
        );
    }

    public static String hashableToString(Hashable hashable) {
        return hashable.toHash().entrySet()
                .stream()
                .map(entry -> {
                    final Object v = entry.getValue();
                    return String.format("%s=%s", entry.getKey(), (v instanceof byte[]) ? Hex.toHexString((byte[]) v) : v.toString());
                })
                .collect(Collectors.joining(", "));
    }

    public static void main(String[] args) {
        try {
            new RSABenchmark().run();
        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }
    }
}
