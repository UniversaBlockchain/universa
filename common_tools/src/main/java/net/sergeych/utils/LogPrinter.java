/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.utils;

//import android.util.Log;

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
        if (params.length > 0)
            message = String.format(message, params);
//        switch (type) {
//            case 'e':
//                Log.e(tag, message);
//                break;
//            case 'i':
//                Log.i(tag,message);
//                break;
//            case 'w':
//                Log.w(tag, message);
//                break;
//            default:
//                Log.d(tag, message);
//                break;
//        }
        outputLog(tag, message);
    }

    public void outputLog(String tag, String message) {
        System.out.printf("%s %s\n", tag, message);
//        Log.d(tag, message);
    }

    public void d(String format, Object... objects) {
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
}
