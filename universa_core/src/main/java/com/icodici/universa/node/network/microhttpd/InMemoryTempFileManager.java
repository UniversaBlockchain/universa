/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.network.microhttpd;

import net.sergeych.utils.LogPrinter;
import org.nanohttpd.protocols.http.tempfiles.ITempFile;
import org.nanohttpd.protocols.http.tempfiles.ITempFileManager;
import org.nanohttpd.util.IFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of {@link ITempFileManager}, creating {@link InMemoryTempFile} instances.
 * Each of them gets the unique (UUID-based) name.
 */
class InMemoryTempFileManager implements ITempFileManager {

    private static LogPrinter log = new LogPrinter("MHTP");

    private final List<ITempFile> tempFiles;

    static class InMemoryTempFileManagerFactory implements IFactory<ITempFileManager> {
        @Override
        public ITempFileManager create() {
            return new InMemoryTempFileManager();
        }
    }

    /**
     * Constructor.
     */
    public InMemoryTempFileManager() {
        this.tempFiles = new ArrayList<ITempFile>();
    }

    @Override
    public void clear() {
        // Loop over a copy, in case if ever called concurrently
        for (final ITempFile file : new ArrayList<>(this.tempFiles)) {
            try {
                file.delete();
            } catch (Exception e) {
                log.wtf("Cannot remove file " + file.getName(), e);
            }
        }
        this.tempFiles.clear();
    }

    @Override
    public ITempFile createTempFile(String filename_hint) throws Exception {
        final InMemoryTempFile tempFile = new InMemoryTempFile();
        this.tempFiles.add(tempFile);
        return tempFile;
    }
}
