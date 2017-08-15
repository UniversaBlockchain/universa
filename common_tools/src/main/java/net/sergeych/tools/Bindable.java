package net.sergeych.tools;

import java.io.IOException;

/**
 * Created by sergeych on 12.01.17.
 */
public interface Bindable {
    Binder toBinder();

    <T> T updateFrom(Binder source) throws IOException;
}
