package net.sergeych.tools;

import com.sun.jna.Library;

/**
 * Binding to the native journald library.
 *
 * @author Lucas Satabin
 *
 */
public interface SystemdJournal extends Library {

    int sd_journal_print(int priority, String format, Object... args);

    int sd_journal_send(String format, Object... args);

    int sd_journal_perror(String message);
}
