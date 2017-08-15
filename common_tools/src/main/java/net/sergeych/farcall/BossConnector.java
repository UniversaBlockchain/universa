/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.farcall;

import net.sergeych.boss.Boss;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Map;

/**
 * Created by sergeych on 13.04.16.
 */
public class BossConnector extends BasicConnector implements Connector {

    private final Boss.Reader bossIn;
    private final Boss.Writer bossOut;

    public BossConnector(InputStream in, OutputStream out) throws IOException {
        super(in,out);
        bossIn = new Boss.Reader(in);
        bossOut = new Boss.Writer(out);
        bossOut.setStreamMode();
    }

    @Override
    public void send(Map<String, Object> data) throws IOException {
        if( closed.get() )
            throw new IOException("connection closed");
        bossOut.write(data);
    }

    @Override
    public Map<String, Object> receive() throws IOException {
        try {
            return bossIn.readMap();
        }
        catch(EOFException | SocketException ignored) {
            return null;
        }
    }

}
