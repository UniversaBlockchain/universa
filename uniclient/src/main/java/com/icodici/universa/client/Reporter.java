package com.icodici.universa.client;

import net.sergeych.tools.Binder;
import net.sergeych.tools.JsonTool;

import java.util.ArrayList;
import java.util.List;

public class Reporter {

    public List<Binder> getErrors() {
        return errors;
    }

    public boolean isJsonMode() {
        return jsonMode;
    }

    public void setJsonMode(boolean jsonMode) {
        this.jsonMode = jsonMode;
    }

    private boolean jsonMode = false;

    private static final List<String> messages = new ArrayList<>();
    private static final List<Binder> errors = new ArrayList<>();

    /**
     * Message to show in the console mode (non json mode). In json mode the message will be ignored.
     *
     * @param text
     */
    public void notice(String text) {
        if (!jsonMode)
            System.out.println(text);
    }

    /**
     * message will be shown immediately or buffered and returned later in json mode.
     *
     * @param text
     */
    public void message(String text) {
        messages.add(text);
        if (!jsonMode)
            System.out.println(text);
    }

    /**
     * Report error. Unless in JSON mode, will be printed immediately.
     *
     * @param code
     * @param object
     * @param text
     */
    public void error(String code, String object, String text) {
        errors.add(Binder.fromKeysValues(
                "code", code,
                "object", object,
                "message", text
        ));
        if (!jsonMode) {
            String msg = "** ERROR: " + code;
            if (object != null && !object.isEmpty())
                msg += " in " + object;
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
    public String reportJson() {
        return JsonTool.toJsonString(report());
    }

    public String getMessage(int smartIndex) {
        if (smartIndex < 0)
            smartIndex = messages.size() + smartIndex;
        if (smartIndex < 0)
            smartIndex = 0;
        if (smartIndex >= messages.size())
            smartIndex = messages.size() - 1;
        if( smartIndex < 0)
            return null;
        return messages.get(smartIndex);
    }

    public void clear() {
        messages.clear();
        errors.clear();
        jsonMode = false;
    }
}
