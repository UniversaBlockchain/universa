package net.sergeych.tools;

import com.sun.jna.Native;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class SystemdJournalWriter  {
    private static SystemdJournal journalLibrary;

    static {
        try {
            journalLibrary = Native.loadLibrary("systemd",
                    SystemdJournal.class);
        } catch (Exception e) { // UnsatisfiedLinkError
            journalLibrary = null;
        }
    }


    public static void writeException(Throwable throwable) {
        if(journalLibrary != null) {
            List<Object> params = new ArrayList<>();
            params.add("EXCEPTION");
            StringWriter stacktrace = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stacktrace));
            params.add("STACKTRACE=%s");
            params.add(stacktrace.toString());
            params.add(null);
            journalLibrary.sd_journal_send("MESSAGE=%s", params.toArray());
        } else {
            throwable.printStackTrace();
        }
    }
}
