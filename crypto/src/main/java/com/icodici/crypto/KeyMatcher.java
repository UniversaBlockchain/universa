package com.icodici.crypto;

/**
 * The interface that could be used to macth both keys and addresses
 */
public interface KeyMatcher {
    boolean isMatchingKey(AbstractKey key);

    boolean isMatchingKeyAddress(KeyAddress other);
}
