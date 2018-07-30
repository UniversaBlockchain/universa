package com.icodici.universa.contract.jsapi;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.List;

public class JSApiHelpers {

    public static String fileName2fileKey(final String name) {
        String[] nameParts = name.split(" ");
        String name2 = String.join("_", nameParts);
        String[] nameParts2 = name2.split("\\.");
        return String.join("_", nameParts2);
    }

    public static Binder createScriptBinder(byte[] jsFileContent, String jsFileName) {
        BiSerializer biSerializer = new BiSerializer();
        HashId scriptHashId = HashId.of(jsFileContent);
        Binder scriptBinder = new Binder();
        scriptBinder.put("file_name", jsFileName);
        scriptBinder.put("__type", "file");
        scriptBinder.put("hash_id", biSerializer.serialize(scriptHashId));
        return scriptBinder;
    }

    public static Binder findScriptBinder(Binder data, HashId hashIdToSearch) {
        BiDeserializer biDeserializer = new BiDeserializer();
        if (data == null)
            return null;
        List<Binder> res = new ArrayList<>();
        data.forEach((k, v) -> {
            if (v instanceof Binder) {
                Binder vBinder = (Binder) v;
                HashId hashId = biDeserializer.deserialize(vBinder.getOrDefault("hash_id", null));
                if (hashIdToSearch.equals(hashId))
                    res.add(vBinder);
            }
        });
        return res.size()>0 ? res.get(0) : null;
    }

    public static Object execJS(Binder definitionScripts, Binder stateScripts, byte[] jsFileContent,
            Contract currentContract, String... params)
            throws ScriptException, IllegalArgumentException {
        HashId jsFileHashId = HashId.of(jsFileContent);
        Binder scriptBinder = JSApiHelpers.findScriptBinder(definitionScripts, jsFileHashId);
        if (scriptBinder == null)
            scriptBinder = JSApiHelpers.findScriptBinder(stateScripts, jsFileHashId);
        if (scriptBinder != null) {
            ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(s -> false);
            jse.put("jsApi", new JSApi(currentContract));
            String[] stringParams = new String[params.length];
            for (int i = 0; i < params.length; ++i)
                stringParams[i] = params[i].toString();
            jse.put("jsApiParams", stringParams);
            jse.eval(new String(jsFileContent));
            return jse.get("result");
        } else {
            throw new IllegalArgumentException("error: cant exec javascript, script hash not found in contract.");
        }
    }

}
