package net.sergeych.farcall;

import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Farcall command representation. Each farcall command consist of name and optional parameters
 * array and/or hash.
 */
public class Command {
    private final String name;
    private ArrayList<Object> params;
    private Map<String, Object> keyParams;

    @SuppressWarnings("unchecked")
    Command(Map<String, Object> input) throws ProtocolException {
        try {
            name = (String) input.get("cmd");
            if (name == null || name.isEmpty())
                throw new ProtocolException("command name can't be empty");
            Object rawArgs = input.get("args");
            if( rawArgs instanceof Object[]) {
                params = new ArrayList<>();
                for(Object x: (Object[])rawArgs) {
                    params.add(x);
                }
            }
            else
                params = (ArrayList<Object>) rawArgs;
            if (params == null)
                params = new ArrayList<>();
            keyParams = (Map<String, Object>) input.get("kwargs");
            if (keyParams == null)
                keyParams = new HashMap<>();
        }
        catch(ClassCastException e) {
            throw new ProtocolException("bad command record");
        }
    }

    @SuppressWarnings("unused")
    public ArrayList<Object> getParams() {
        return params;
    }

    @SuppressWarnings("unused")
    public String getName() {
        return name;
    }

    @SuppressWarnings("unused")
    public Map<String, Object> getKeyParams() {
        return keyParams;
    }

    @SuppressWarnings("unchecked unused")
    public <T> T getParam(String keyName) {
        return (T) keyParams.get(keyName);
    }

    @SuppressWarnings("unused unchecked")
    public <T> T getParam(String keyName, T defaultValue) {
        T result = (T) keyParams.get(keyName);
        return result == null ? defaultValue : result;
    }

    @SuppressWarnings("unused unchecked")
    public <T> T getParam(int n) {
        return (T) params.get(n);
    }

    @SuppressWarnings("unused unchecked")
    public <T> T getParam(int n, T defaultValue) {
        T result = (T) params.get(n);
        return result == null ? defaultValue : result;
    }

    @SuppressWarnings("unused")
    public int paramsSize() {
        return params.size();
    }

    /**
     * Check the command name
     * @param someName
     * @return getName().equals(someName)
     */
    public boolean is(String someName) {
        return name.equals(someName);
    }
}
