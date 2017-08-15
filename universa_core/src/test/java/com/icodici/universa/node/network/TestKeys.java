/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import net.sergeych.tools.Do;

/**
 * Created by sergeych on 12/12/16.
 */
public class TestKeys {

    static public PrivateKey privateKey(int index) throws EncryptionError {
        byte[] src = Do.decodeBase64(binaryKeys[index]);
        return new PrivateKey(src);
    }


    static String binaryKeys[] = new String[]{
            "JgAcAQABxAAB2ZLz9pA2qlUys9oomId1YF8u8n8T98ekEv8gYAyBQfnHnhqc\n" +
                    "iPcTe4AoZb+r4h1sBgwhZ39pXXNOZDBOMd+e2UHIHYAZvi6R7lNnOm0waLCH\n" +
                    "H7rNXJLCzPHpp7vhAhwVao9pu5U3Maw6dwAVvb4XBoQs2YyMjpSApQJOPizG\n" +
                    "qf6l4D5HW1AxLbWhlKvcs+wBapb9H7266Kzvf2mK2HARi7aQHO5fA/+YGXwe\n" +
                    "TLjt+iLB2TSOvl4juz6w7nmV18QF88FP1DkMWVxyHnFDaIB9E2XCe80Qr9dh\n" +
                    "GOfJcWefvJcdsvgtJMeEYm87IGt0yI/MlpyWFzjMj7VzT+NtQUBEN8QAAdqS\n" +
                    "4PEfNqpVMrPaKJiHdWCMAtUXsa1VMki4p0wG02pCp4h+ByPqzZx7BgNZxgYM\n" +
                    "IWd/VF0zQvCZ4r05BJNtYrG9Of9XSzyGBmd9Nyjj0TKLLYnt60QZx3Wpu+E9\n" +
                    "JEHlJGm7lTcxpSASABW9qzIzB/SEIs1roH3kSStMiSIWrGyhlIXcs+wBapY6\n" +
                    "SKNwuujh5Ha/9W/+G3HDzZiv0VNZtjNcYSkFHD6RyHcjjJdbe2xEzjxnIcbN\n" +
                    "7UGLsydve5TJPsKtaDxQAjJk0JpVMzruxdhiOb+Otq9IvZtV0weA4cMUNr4N\n" +
                    "5GOL6TeprGf8TFwpJ7DpgxpZYoYMO6r62hn8pIU=\n",

            "JgAcAQABxAABzSj/76kVC9Oo6bBBDkEZIO2FPHl+QQOOkyUSW7X+wyWOq6gW\n" +
                    "/McbqGokidqWXYJfwKPauzH1GQ7oDCoOenPJEi4Jm4oAwKgZ53ngsssHynGs\n" +
                    "+2IJ2NYH61jtMUUp7O0A3lWfMgG9M0amTBGcuSKQ1IalP3cIeiMuo/2zeUat\n" +
                    "jm3GSY2o3vxjwwI40mIrzVjzGG5uPSD5socv0yEnI21utLV/opfJgUsqOIH+\n" +
                    "KlOO2NRZ9/BPrdv/wUP24Cs31rZIs8nfUap/JCXkcP/hBdWDxQ0aLaLIn5E6\n" +
                    "OvkKca4vr6/5AOCe1EJfKhx1K35PDFSxVkCumt/Ryc/NdXF9RacTgcQAAf7z\n" +
                    "J5RIYuMi9trkZJNUryi9Mtk4sK64olv5GG5uPSD5socv0yEnI21utLV/omwm\n" +
                    "ZK+IWp4IMM8KSkth1ONlCz4N1FB/IfKUVHmZPWp9Z9lgAXpC2iXkcP8i/WME\n" +
                    "6AO8s2zx7HkboeTMCoiGPKbY4Xily6cbsJUE4t7P3tWoG7sB5DwC6ornlUhY\n" +
                    "ZPkeNZH4ZMQ9p3pZk9ITurJM2flaJkj0y6ilZCPnuZm3L6SCT0vZXF1h9EMO\n" +
                    "IwtxRz7wzFMJJGVNDLjHH03bpQB865O4CkZuxUZl+f57nDgE1+vRPwdory0o\n" +
                    "zvpyQFRM0usYFfvUDfhk3MSti/SsBTg8VpYLF8c=\n",

            "JgAcAQABxAABwOesjSMPkIiKWfcmcKXVtBSjD1ZpBWpfniY3oJbxO8aqHmpr\n" +
                    "HggJ+NPMBLmGzY6jEyYDpzv3jqS69XSTJmES+CGwTayjyllPp0yJvIysEsoY\n" +
                    "BwrXWFHojnSep8QUoDr6d1WfJ6nnsO+AYZ32AVZylUBGHbT7iGDzJ0zpuBOe\n" +
                    "/ZlmGOFVuuVzpvTbbJ/0iXm/uU0QPF/bz9rl6m86qJ4o7R9qZGb2s1wp6Ft2\n" +
                    "DGjhcD6ie4dhxOpVrO64zn7asMFCwt251CReK9YETWO+FMWgraBP8TnECSX6\n" +
                    "18N6bP/dISRIVfEnflxFJgeOFf0phTcJqR1l/7pAYscuhXcHoQbLt8QAAdee\n" +
                    "gnl3AvS5gRLooTef1tzoZkid4SVhSO6Vyua2i5go97k53D6ie4dhxOpzwpde\n" +
                    "e4yf/I/BQsLdudQkXisFTyTwCFqzQL916WYlFiAxY9UPt3iYTLmSSFYDkEry\n" +
                    "Pb4VYtiN43vP7M28ynzp+CZ35cbIAjsUurvRSTOzbAcWVozRhDl/9s44hF/p\n" +
                    "/gVy5fY9oTzh4sQnHIUbE6nE42B3d34IQdE3MeqhQBttn1OHTQxW+DASDt9d\n" +
                    "pR+5tdhep+rusR57XSsIGRQKefcmrYTZQSz3KdYidUN3yflgGtuVLmu91iIX\n" +
                    "IginhKtFCDcFQg9Bp83ZvSl0RAmBGn7qKAjlfR8=\n",

            "JgAcAQABxAAB6X7cH9NdSxJ1rR/7QeRmDCWM0qNIJkQnI/T8kIAFt2VElm+7\n" +
                    "XeOEpN7tJC85dddWN6hegqW5FrJ8Ug2w8wBuseb/nZpEPeXzKjnAGpd7vrx3\n" +
                    "qfrvQirjCKyVE6OyseLGG1RXvcMTseqdCLAJz/a00SdgqRjK5zH6BhCJiRzV\n" +
                    "8tBsycopGrtPDbHbiSpgmYqvk63nLAxUrD6K/ZdfIN2P7HYekN9Um16L8e9U\n" +
                    "Ro8oqxTAv5kVLr04pA0GajBXl6jUa5Kp/xawSJmOeWY7Hpoi3u2zUa/sMs7O\n" +
                    "RivG9Hbvmj/S89wCjyFd0etLsdT1DH5bnZqWY34pFNuSqOvUKF8AdcQAAfjR\n" +
                    "0ILGcT0oRWN+oa5veOJy0icrk+KpCtDOcDSBLB6glU2HuS75WDJhlWDKcjBC\n" +
                    "m+JdpDRvc+6ISiDs3uUwoMz49mOkGriGJgMwUAnn+o2k+4aL6f2xfOLpGOio\n" +
                    "kKwGXg86zQLFD20qqToxfrZFvzjmVtM9msuNxeJjJtt/2tx6iMogaql8B6Cq\n" +
                    "JLTYuKdb+aJPp8oGNit2ofsp7nbzSKSAXWAX3d25H8HAhJ+xDCJ3r0gmRCcj\n" +
                    "9PydN2XsqHGWb7G9Rs4H0HgXndP9/fHjyiPLa/15BuiluRay4VJnmhFR0Tjr\n" +
                    "EL+nURLBubWit2VY/I0GxfDMdlwz3qi00lLW7ss="
    };

    public static PublicKey publicKey(int index) throws EncryptionError {
        return privateKey(index).getPublicKey();
    }
};
