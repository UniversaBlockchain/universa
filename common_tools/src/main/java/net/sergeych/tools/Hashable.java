package net.sergeych.tools;

import java.io.IOException;
import java.util.Map;

/**
 * Object that has hash representation and can be reconstructed from the hash
 *
 * Created by sergeych on 15/04/16.
 */
@SuppressWarnings("unused")
public interface Hashable {
    class Error extends IOException {
        public Error(String reason) {
            super(reason);
        }
        public Error(String text, Exception reason) {
            super(text, reason);
        }
    }

    Map<String,Object> toHash() throws IllegalStateException;

    /**
     * Load object state from the hash
     * @param hash with data
     * @throws Error if the data are not suitable to load the object state
     */
    void updateFromHash(Map<String, Object> hash) throws Error;
}
