/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.utils;

//import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Log reporting stub. Platform-dependency crutches. Fuck Java for the lack of conditional
 * compilation!
 *
 * @author sergeych
 */
public class LogPrinter {

    private static boolean showDebugMessages = false;
//    private static boolean showDebugMessages = true;
    private String tag;

    static public void showDebug(boolean show) {
        showDebugMessages = show;
    }

    public LogPrinter(String tag) {
        this.tag = tag;
    }

    public void i(String format, Object... objects) {
        log('i', tag, format, objects);
    }

    public void log(char type, String tag, String message, Object... params) {
        if( type == 'd' && !showDebugMessages )
            return;
        if( params.length == 0 )
            outputLog(tag, message);
        else
            outputLog(tag, String.format(message, params));
    }

    public void log(char type,String tag,Callable<String> source) {
        es.submit( () -> {
            if (type == 'd' && !showDebugMessages)
                return;
            try {
                log(type, tag, source.call());
            } catch (Exception e) {
                wtf("Exception in log source callable: ", e);
            }
        });
    }


    public void e(Callable<String> msg) {
        log('e', tag, msg);
    }

    public void log(char type, String tag, String message) {
        es.submit( () -> {
            if (type == 'd' && !showDebugMessages)
                return;
            outputLog(tag, message);
        });
    }

    public void outputLog(String tag, String message) {
        System.out.printf("%s %s\n", tag, message);
//        Log.d(tag, message);
    }

    public void d(String text) {
        if( !showDebugMessages )
            return;
        log('d', tag, text);
    }
    public void d(Callable<String> source) {
        if( showDebugMessages )
            log('d', tag, source);
    }
    public void d(String format, Object... objects) {
        if( showDebugMessages )
            log('d', tag, format, objects);
    }

    public void w(String format, Object... objects) {
        log('w', tag, format, objects);
    }

    public void e(String format, Object... objects) {
        log('e', tag, format, objects);
    }

    /** Respect to android guys. What The Terrible Failure!
     *
     * @param message
     * @param t
     */
    public void wtf(String message, Throwable t) {
//        Log.wtf(tag, message, t);
        log('f', tag, message + ": "+t);
        t.printStackTrace();
    }

    private static ExecutorService es = Executors.newSingleThreadExecutor();
}
