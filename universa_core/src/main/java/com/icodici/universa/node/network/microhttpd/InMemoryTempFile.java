/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.network.microhttpd;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.nanohttpd.protocols.http.tempfiles.ITempFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of {@link ITempFile}, keeping the file in memory.
 * The {@link InMemoryTempFile#getName} can be used to retrieve the file
 * using {@link InMemoryTempFile#getFileByName} method.
 */
public class InMemoryTempFile implements ITempFile, AutoCloseable {

    private final static ConcurrentHashMap<String, InMemoryTempFile> files = new ConcurrentHashMap<>();

    private final ByteArrayOutputStream fstream = new ByteArrayOutputStream(1024);
    private final String fileName = UUID.randomUUID().toString() + ".tempfile";

    public InMemoryTempFile() {
        files.put(this.fileName, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete() throws Exception {
        files.remove(this.fileName);
        fstream.reset();
        fstream.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return fileName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputStream open() throws Exception {
        return fstream;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        delete();
    }

    public ByteArrayOutputStream getOutputByteStream() {
        return fstream;
    }

    public ByteArrayInputStream getInputByteStream() {
        return new ByteArrayInputStream(fstream.toByteArray());
    }

    @Nullable
    public static InMemoryTempFile getFileByName(@NonNull String name) {
        assert name != null;
        return files.get(name);
    }
}
