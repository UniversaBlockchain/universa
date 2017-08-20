package net.sergeych.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool to report messages, errors, progress notifications the same way in console applications, GUI applications and
 * services. Create instance and call {@link #notice(String)}, {@link #progress(String)} and {@link #error(String,
 * String, String)}. Supports collecting and reporting collected data, {@link #setQuiet(boolean)} mode, {@link
 * #reportJson()} and one level of verbisity: {@link #setVerboseMode(boolean)}.
 */
public class Reporter {

    public List<Binder> getErrors() {
        return errors;
    }

    /**
     * See {@link #setQuiet(boolean)}
     *
     * @return
     */
    public boolean isQuiet() {
        return quiet;
    }

    /**
     * Set quiet mode: all information is only collected. To report it later at some poing, use {@link #report()} or
     * {@link #reportJson()}. If the work is not finished, consider {@link #clear()} before collecting more data.
     * <p>
     * By default, reporter collects data and print itout messages to the stdout.
     *
     * @param quiet
     */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    private boolean quiet = false;
    private boolean verboseMode = false;

    private static final List<String> messages = new ArrayList<>();
    private static final List<Binder> errors = new ArrayList<>();

    /**
     * Message to show in the console mode (non json mode). In json mode the message will be ignored.
     *
     * @param text
     */
    public synchronized void notice(String text) {
        if (!quiet)
            System.out.println(text);
    }

    public synchronized void progress(String text) {
        if (!quiet)
            System.out.print(text + "\r");
    }

    public void verbose(String text) {
        if (verboseMode)
            message(text);
    }

    /**
     * message will be shown immediately or buffered and returned later in json mode.
     *
     * @param text
     */
    public synchronized void message(String text) {
        messages.add(text);
        if (!quiet)
            System.out.println(text);
    }

    /**
     * Report error. Unless in JSON mode, will be printed immediately. Errors are 3-field objects with string fields
     * "code", "object" and "text", which is usslay enough for most usages.
     *
     * @param code
     * @param object
     * @param text
     */
    public synchronized void error(String code, String object, String text) {
        errors.add(Binder.fromKeysValues(
                "code", code,
                "object", object,
                "message", text
        ));
        if (!quiet) {
            String msg = "** ERROR: " + code;
            if (object != null && !object.isEmpty())
                msg += ": " + object;
            if (text != null && !text.isEmpty())
                msg += ": " + text;
            System.out.println(msg);
        }
    }

    /**
     * Get collected report as a Binder object.
     *
     * @return
     */
    public Binder report() {
        return Binder.fromKeysValues(
                "messages", messages,
                "errors", errors
        );
    }

    /**
     * Get collected report as JSON string
     *
     * @return
     */
    public synchronized String reportJson() {
        return JsonTool.toJsonString(report());
    }

    public synchronized String getMessage(int smartIndex) {
        if (smartIndex < 0)
            smartIndex = messages.size() + smartIndex;
        if (smartIndex < 0)
            smartIndex = 0;
        if (smartIndex >= messages.size())
            smartIndex = messages.size() - 1;
        if (smartIndex < 0)
            return null;
        return messages.get(smartIndex);
    }

    public synchronized void clear() {
        messages.clear();
        errors.clear();
        quiet = false;
    }

    public boolean isVerboseMode() {
        return verboseMode;
    }

    public void setVerboseMode(boolean verboseMode) {
        this.verboseMode = verboseMode;
    }
}
