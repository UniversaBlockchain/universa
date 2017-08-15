/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.util.*;

/**
 * The capsule is a main safe container, safe box to store and transmit content over the attesta
 * network and components.
 * <p>
 * It consists of two arbitrary container, public one and private one. Private one could be acceed
 * only by the parties having in disposal corresponding key. Private data are encrypted and signed
 * using most secure EtA algorythm. For private part either public keys or symmetric keys are
 * allowed in any combination and quantity.
 * <p>
 * Public part id available to anybody and is, normally signed or sealed. Siging and sealing is done
 * only with private keys. Sealed capsule has signers key mentioned in the signed part, so it is
 * impossible to add seal later unless it was intended by adding public keys is already mentioned in
 * the public data.
 * <p>
 * Regular signatures can be added later to the capsule, with own timestamp. These have less
 * significance than seals, though.
 * <p>
 * Capsule are encoded with bit-efficient binary typed format {@link Boss} and are transfered as
 * packed binary paks.
 * <p>
 * Created by sergeych on 15.12.16.
 */
public class Capsule {

    private boolean partiallySigned = false;
    private boolean decryptionFailed;

    /**
     * Try to decrypt the capsule with password. E.g. if the encrypted part contains any password-
     * derived key information, the password will be used to try to derive the key. It is advisable
     * to not to sign password capsules unless is really need, as AE used with tha password ensures
     * the data are not modified after encryption.
     *
     * @param password
     *         to unencrypt the capsule
     * @param packed
     *         ninary packed capsule
     *
     * @throws EncryptionError
     *         if it failed to decrypt
     * @throws BadSignatureException
     *         if the signature present but the data is broken or tampered with.
     */
    public Capsule(final String password, byte[] packed) throws EncryptionError,
            BadSignatureException {
        KeySource src = new KeySource() {
            @Override
            public Collection<AbstractKey> findKey(KeyInfo keyInfo) {
                ArrayList<AbstractKey> list = new ArrayList<>();
                if (keyInfo.isPassword())
                    list.add(keyInfo.derivePassword(password));
                return list;
            }
        };
        load(packed, src, true, false);
    }

    public boolean isPartiallySigned() {
        return partiallySigned;
    }

    public boolean decryptionFailed() {
        return decryptionFailed;
    }

    public void setPrivateData(Object... keysAndValues) {
        setPrivateData(new Binder(keysAndValues));
    }

    public class FormatException extends EncryptionError {
        public FormatException() {
            super("bad coffer format");
        }

        public FormatException(String message) {
            super(message);
        }

        public FormatException(String message, Throwable cause) {
            super(message, cause);
        }

        Capsule getCoffer() {
            return Capsule.this;
        }
    }

    public class BadSignatureException extends FormatException {
        public BadSignatureException() {
            super("bad coffer signature");
        }

        public BadSignatureException(String message) {
            super(message);
        }

        public BadSignatureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public class DecryptionFailedException extends EncryptionError {
        public DecryptionFailedException(String reason) {
            super(reason);
        }

        public DecryptionFailedException(String reason, Throwable cause) {
            super(reason, cause);
        }
    }

    private boolean signed = false;

    /**
     * Sets to true only after unpacking binary coffer which was signed - if it was signed properly.
     *
     * @return true if the data is signed and can be trusted - if you trust at leat one of the
     * signers.
     */
    public boolean isSigned() {
        return signed;
    }

    public Collection<AbstractKey> getSigningKeys() {
        ArrayList<AbstractKey> kk = new ArrayList<>();
        for(Binder b: signers.values())
            kk.add((AbstractKey) b.get("key"));
        return kk;
    }

    private Binder publicData = new Binder();
    private Binder privateData = new Binder();
    private Collection<AbstractKey> encryptingKeys = new ArrayList<>();
//    private Collection<AbstractKey> signingKeys = new ArrayList<>();

    public Map<String, Binder> getSigners() {
        return Collections.unmodifiableMap(signers);
    }

    private HashMap<String, Binder> signers = new HashMap<>();

    public Capsule() {
    }

    public Capsule(byte[] packed, KeySource keySource) throws BadSignatureException,
            IOException {
        this();
        load(packed, keySource, false, false);
    }

    public Capsule(byte[] packed, KeySource keySource, boolean allowPartiallySigned, boolean
            allowOnlyPublic) throws
            BadSignatureException, IOException {
        this();
        load(packed, keySource, allowPartiallySigned, allowOnlyPublic);
    }

    public Capsule load(byte[] packedCoffer, KeySource keySource, boolean allowPartiallySigned,
                        boolean allowOnlyPublic)
            throws BadSignatureException, EncryptionError {
        try {
            Binder payload = unpackPayload(packedCoffer, allowPartiallySigned);
            Binder secret = payload.getBinder("private");
            decryptionFailed = false;
            if (!secret.isEmpty()) {
//                Boss.trace(secret);
                privateData = null;
                byte[] encryptedData = secret.getBinary("data");
                // Iterate over all keys in source
                outer:
                for (Binder b : secret.getBinders("keys")) {
                    // Iterate over all suitable keys in ring
                    // Note that exception while reading key info is fatal and is thrown.
                    // only exceptions while decrypting are ignored
                    for (AbstractKey k : keySource.findKey(new KeyInfo(b.getBinary("keyInfo")))) {
                        byte[] encryptedKey = b.getBinary("key");
                        try {
                            SymmetricKey sk = new SymmetricKey(k.decrypt(encryptedKey));
                            privateData = Boss.unpack(sk.etaDecrypt(encryptedData));
                            break outer;
                        } catch (Exception e) {
                            // our keys do not match... continuing
                        }
                    }
                }
                if (privateData == null) {
                    if (!allowOnlyPublic)
                        throw new DecryptionFailedException("can't decrypt private data");
                    decryptionFailed = true;
                }
            }
            return this;
        } catch (BadSignatureException | DecryptionFailedException e) {
            throw e;
        } catch (IllegalArgumentException | IOException e) {
            throw new Capsule.FormatException("failed to read capsule", e);
        }
    }

    @NonNull
    private Binder unpackPayload(byte[] packedCoffer, boolean allowPartiallySigned) throws
            EncryptionError {
        signed = false;
        Binder outer = Boss.unpack(packedCoffer);

        Collection<Binder> signatures = outer.getBinders("signatures");
        final byte[] source = outer.getBinary("content");
        Binder payload = Boss.unpack(source);

        if (!payload.get("type").equals("capsule"))
            throw new FormatException("not capsule/unknown type");

        checkSignatures(source, signatures, payload, allowPartiallySigned);
        publicData = payload.getBinder("public");
        return payload;
    }

    private void checkSignatures(byte[] src, Collection<Binder> signatures, Binder payload,
                                 boolean allowPartiallySigned)
            throws EncryptionError {
        signed = false;
        partiallySigned = false;
        clearSigners();

        if (signatures == null || signatures.isEmpty())
            return;

        for (Binder b : payload.getBinders("signers")) {
            PublicKey k = new PublicKey();
            k.unpack(b.getBinary("key"));

            Binder result = new Binder();
            result.put("key", k);
            final String id = b.getStringOrThrow("id");
            result.put("id", id);
            result.put("data", b.getBinder("data"));
            signers.put(id, result);
        }
        if (signers.size() != signatures.size() && !allowPartiallySigned)
            throw new BadSignatureException("signatures do not match signers");
        for (Binder sig : signatures) {
            AbstractKey k = (AbstractKey) signers.get(sig.getStringOrThrow("key")).get("key");
            if (!k.verify(src, sig.getBinary("signature"), HashType.SHA512))
                throw new BadSignatureException("signature is broken at " + sig.getStringOrThrow("key"));
//            signingKeys.add(k);
        }

        if (signers.isEmpty()) {
            partiallySigned = false;
            signed = false;
        } else {
            signed = signers.size() == signatures.size();
            partiallySigned = !signed;
        }
    }

    public void setPublicData(Binder publicData) {
        this.publicData = publicData;
    }

    public Binder getPublicData() {
        return publicData;
    }

    public void setPrivateData(Binder privateData) {
        this.privateData = privateData;
    }

    public Binder getPrivateData() {
        return privateData;
    }

    public void clearSigners() {
        signers.clear();
//        signingKeys.clear();
    }

    public void addSigners(Collection<AbstractKey> signers) {
        for (AbstractKey k : signers)
            addSigner(k, null);
    }

    public void addSigners(AbstractKey... keys) {
        addSigners(Do.collection(keys));
    }

    /**
     * Add single signer key with associated data. Capsule format lets add any extra data to the
     * signer key (e.g. role, restrictions and so on). Each signer receives an ID to refer which is
     * returned.
     *
     * @param key
     * @param signerData
     *
     * @return assigned ID of the signer
     */
    public String addSigner(AbstractKey key, Binder signerData) {
        // Compatibility: add to both ends
        String id = String.valueOf(signers.size());
//        signingKeys.add(key);
        signers.put(id, new Binder("id", id, "key", key, "data", signerData));
        return id;
    }

    /**
     * @param key
     *         to retreive information of
     *
     * @return signer information block, not null
     *
     * @throws IllegalArgumentException
     *         if key is not listed in signers
     */
    Binder getSigner(AbstractKey key) {
        for (Binder b : signers.values()) {
            if (b.get("key").equals(key))
                return b;
        }
        throw new IllegalArgumentException("key is not found");
    }

    String getSignerId(AbstractKey key) {
        return getSigner(key).getStringOrThrow("id");
    }

    AbstractKey getSignerKey(String signerKeyId) {
        return (AbstractKey) signers.get(signerKeyId).get("key");
    }

    /**
     * Retreive extra information of the signer.
     *
     * @param key
     *
     * @return any extra data associated to the key. Empty {@link Binder} instance if there is no
     * associated information
     *
     * @throws IllegalArgumentException
     *         if key is not found
     */
    public Binder getSignerData(AbstractKey key) {
        return getSigner(key).getBinder("data");
    }

    public Binder getSignerData(String signerKeyId) {
        return signers.get(signerKeyId).getBinder("data");
    }

    public void clearKeys() {
        encryptingKeys.clear();
    }

    public void addKeys(Collection<AbstractKey> keys) {
        encryptingKeys.addAll(keys);
    }

    public void addKeys(AbstractKey... keys) {
        addKeys(Do.collection(keys));
    }

    public byte[] pack() throws EncryptionError {
        Binder payload = preparePayload();
        HashMap<String, AbstractKey> sigIds = prepareSigners(payload);
        // Outer container with signatures
        Binder outer = new Binder();
        final byte[] content = Boss.pack(payload);
        outer.put("content", content);
        if (!sigIds.isEmpty()) {
            ArrayList<Binder> signatures = outer.set("signatures", new ArrayList<>());
            for (Map.Entry<String, AbstractKey> e : sigIds.entrySet())
                signatures.add(new Binder("key",
                                          e.getKey(),
                                          "signature",
                                          e.getValue().sign(content, HashType.SHA512)
                               )
                );
        }
        return Boss.pack(outer);
    }

    @NonNull
    private HashMap<String, AbstractKey> prepareSigners(Binder payload) {
        HashMap<String, AbstractKey> sigIds = new HashMap<>();
        if (signers != null && !signers.isEmpty()) {
            ArrayList<Binder> s = payload.set("signers", new ArrayList<>());
            int i = 0;
            for (Binder b : signers.values()) {
                final String id = b.getStringOrThrow("id");
                final AbstractKey key = (AbstractKey) b.get("key");
                sigIds.put(id, key);
                Binder signerData = new Binder(
                        "id", id,
                        "key", key.getPublicKey().pack(),
                        "data", b.getBinder("data")
                );
                s.add(signerData);
            }
        }
        return sigIds;
    }

    @NonNull
    private Binder preparePayload() throws EncryptionError {
        Binder payload = new Binder();
        // Private data
        if (hasPrivate()) {
            if (encryptingKeys == null || encryptingKeys.isEmpty()) {
                throw new IllegalStateException("missing encryption keys");
            }
            SymmetricKey mainKey = new SymmetricKey();
            byte[] packedMainKey = mainKey.pack();

            Binder data = payload.set("private", new Binder());
            ArrayList keys = data.set("keys", new ArrayList<Binder>());
            for (AbstractKey k : encryptingKeys) {
                if( k == null )
                    throw new IllegalStateException("null is forbidden in encryption keys");
                Binder b = new Binder("keyInfo", k.packedInfo(),
                                      "key", k.encrypt(packedMainKey));
                keys.add(b);
            }
            // Private data are packed with random filler. The way it is donne
            // fill will simply not read
            byte[] packedPrivate = Boss.dumpToArray(privateData,
                                                    CTRTransformer.randomBytes(0, 117));
            data.put("data", mainKey.etaEncrypt(packedPrivate));
        }
        // public data
        if (hasPublic()) {
            payload.put("public", publicData);
        }
        payload.put("type", "capsule");
        return payload;
    }

    private boolean hasPublic() {
        return publicData != null && !publicData.isEmpty();
    }

    private boolean hasPrivate() {
        return privateData != null && !privateData.isEmpty();
    }

    public void setPublicData(Object... keysAndValues) {
        setPublicData(new Binder(keysAndValues));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Capsule))
            return false;
        Capsule other = (Capsule) obj;
        if (publicData != null) {
            if (!publicData.equals(other.publicData))
                return false;
        }
        if (privateData != null) {
            if (!privateData.equals(other.privateData))
                return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Capsule(");
        if (hasPublic()) {
            b.append("public:" + Arrays.toString(publicData.entrySet().toArray()));
        }
        if (hasPrivate()) {
            if (hasPublic())
                b.append(",");
            b.append("private:" + Arrays.toString(privateData.entrySet().toArray()));
        }
        return b.toString() + ")";
    }

    public interface KeySource {
        public Collection<AbstractKey> findKey(KeyInfo keyInfo);
    }
}
