/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import net.sergeych.boss.Boss;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Notifications are binary-effective packable units to transfer between nodes with v2 UDP protocols.
 * <p>
 * Each notification should inherit from {@link Notification} and register self with uniqie integer code in static
 * constructor using {@link #registerClass(int, Class)}. It also must provide provate nonparametric constructor and
 * implement abstract methods {@link #writeTo(Boss.Writer)}, {@link #readFrom(Boss.Reader)} and {@link #getTypeCode()}.
 * <p>
 * Notifications could be packed together in a compact form. Use {@link #pack(Collection)} and {@link #unpack(NodeInfo,
 * byte[])}.
 */
abstract public class Notification {

    protected Notification() {
    }

    public NodeInfo getFrom() {
        return from;
    }

    private transient NodeInfo from;

    public Notification(NodeInfo from) {
        this.from = from;
    }

    /**
     * Write self to boss writer
     *
     * @param writer is {@link Boss.Writer} to write to
     *
     * @throws IOException with read exceptions
     */
    abstract protected void writeTo(Boss.Writer writer) throws IOException;

    /**
     * Read self from boss reader
     *
     * @param reader is {@link Boss.Reader} to read from
     *
     * @throws IOException with read exceptions
     */
    abstract protected void readFrom(Boss.Reader reader) throws IOException;

    /**
     * return the code the class had registered self with using {@link #registerClass(int, Class)} in the static
     * constructor. Note that the class that did not register self can't be used by the Universa system
     *
     * @return int unique notification type code
     */
    abstract protected int getTypeCode();

    static private Map<Integer, Class<? extends Notification>> classes = new HashMap<>();

    /**
     * Register class with a type code (same as its instace must return with {@link #getTypeCode()} to use with UDP
     * notifications.
     *
     * @param code  unique type code (per class)
     * @param klass is inherited {@link Notification} class
     */
    static protected void registerClass(int code, Class<? extends Notification> klass) {
        classes.put(code, klass);
    }

    /**
     * Pack collection of notifications to binary array. Note that all notification packed together should be from the
     * same node, e.g. having the same {@link NodeInfo} (see {@link #getFrom()}, so from data is not packed to this
     * array and snould be transfered outside it.
     *
     * @param notifications notificatins to pack
     *
     * @return
     */
    static byte[] pack(Collection<Notification> notifications) {
        Boss.Writer writer = new Boss.Writer();
        try {
            for (Notification n : notifications) {
                write(writer, n);
            }
            return writer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("failed to pack notification", e);
        }
    }

    public static void write(Boss.Writer writer, Notification n) throws IOException {
        writer.write(n.getTypeCode());
        n.writeTo(writer);
    }

    /**
     * Unpack notifications from the binary form and link it with sending node (this information should be transferred
     * aside from the packed notification to not to produce redundant copies of it, all notifications from the same
     * node are packed together). Use {@link #pack(Collection)} to get packed binary.
     *
     * @param from node that has send notifications
     * @param packed representation
     * @return
     * @throws IOException
     */
    static List<Notification> unpack(NodeInfo from, byte[] packed) throws IOException {
        ArrayList<Notification> notifications = new ArrayList<>();
        Boss.Reader r = new Boss.Reader(packed);
        try {
            while (true) {
                // boss reader throws EOFException
                Notification n = read(from, r);
                if( n != null )
                    notifications.add(n);
            }
        } catch (EOFException x) {
            // normal, all data decoded
        } catch (InvocationTargetException | IllegalAccessException | InstantiationException | NullPointerException | NoSuchMethodException e) {
            throw new IOException("Failed to decoded notification", e);
        }
        return notifications;
    }

    public static Notification read(NodeInfo from, Boss.Reader r) throws IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        int code = r.readInt();
        Class<? extends Notification> nclass = classes.get(code);
        if( nclass != null ) {
            Constructor c = nclass.getDeclaredConstructor();
            c.setAccessible(true);
            Notification n = (Notification) c.newInstance();
            n.readFrom(r);
            n.from = from;
            return n;
        }
        else {
            System.out.println("*** unknown notification class code: "+code);
            return null;
        }
    }

    static {
        // preload
        ItemNotification.init();
        ParcelNotification.init();
        ResyncNotification.init();
        CallbackNotification.init();
        UBotSessionNotification.init();
        UBotStorageNotification.init();
    }

}
