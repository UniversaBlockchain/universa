package com.icodici.universa.contract.jsapi;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JSApiHelpers {

    public static String fileName2fileKey(final String name) {
        String[] nameParts = name.split(" ");
        String name2 = String.join("_", nameParts);
        String[] nameParts2 = name2.split("\\.");
        return String.join("_", nameParts2);
    }

    public static Binder createScriptBinder(byte[] jsFileContent, String jsFileName, JSApiScriptParameters scriptParameters) {
        BiSerializer biSerializer = new BiSerializer();
        HashId scriptHashId = HashId.of(jsFileContent);
        Binder scriptBinder = new Binder();
        scriptBinder.set("file_name", jsFileName);
        scriptBinder.set("__type", "file");
        scriptBinder.set("hash_id", biSerializer.serialize(scriptHashId));
        scriptBinder.putAll(scriptParameters.toBinder());
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

    private static String unpackJSString(Binder scriptBinder, byte[] jsFileContent) {
        JSApiCompressionEnum compression = JSApiCompressionEnum.valueOf(scriptBinder.getStringOrThrow("compression"));
        switch (compression) {
            case RAW:
                return new String(jsFileContent);
            case ZIP:
                return unpackJSString_fromZip(jsFileContent);
        }
        throw new IllegalArgumentException("missing script parameter field: compression");
    }

    private static String unpackJSString_fromZip(byte[] jsFileContent) {
        try {
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jsFileContent));
            ZipEntry ze = zis.getNextEntry();
            ByteArrayOutputStream scriptBytes = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            if (ze != null) {
                int count;
                while ((count = zis.read(buf)) >= 0)
                    scriptBytes.write(buf, 0, count);
            } else {
                throw new IllegalArgumentException("unable to unzip client javascript");
            }
            zis.closeEntry();
            zis.close();
            return new String(scriptBytes.toByteArray());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("unable to unzip client javascript: " + e);
        }
    }

    public static Object execJS(Binder definitionScripts, Binder stateScripts, JSApiExecOptions execOptions,
            byte[] jsFileContent, Contract currentContract, String... params)
            throws Exception {
        HashId jsFileHashId = HashId.of(jsFileContent);
        Binder scriptBinder = JSApiHelpers.findScriptBinder(definitionScripts, jsFileHashId);
        if (scriptBinder == null)
            scriptBinder = JSApiHelpers.findScriptBinder(stateScripts, jsFileHashId);
        if (scriptBinder != null) {
            JSApiScriptParameters scriptParameters = JSApiScriptParameters.fromBinder(scriptBinder);
            ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(s -> false);
            jse.put("jsApi", new JSApi(currentContract, execOptions));
            String[] stringParams = new String[params.length];
            for (int i = 0; i < params.length; ++i)
                stringParams[i] = params[i].toString();
            jse.put("jsApiParams", stringParams);
            String jsString = unpackJSString(scriptBinder, jsFileContent);
            List<Exception> exceptionsFromEval = new ArrayList<>();
            Thread evalThread = new Thread(()-> {
                try {
                    jse.eval(jsString);
                } catch (Exception e) {
                    exceptionsFromEval.add(e);
                }
            });
            evalThread.start();
            if (scriptParameters.timeLimitMillis == 0) {
                evalThread.join();
            } else {
                try {
                    evalThread.join(scriptParameters.timeLimitMillis);
                    if (evalThread.isAlive())
                        throw new InterruptedException("error: client javascript time limit is up (limit=" + scriptParameters.timeLimitMillis + "ms)");
                } catch (InterruptedException e) {
                    throw new InterruptedException("error: client javascript was interrupted (limit=" + scriptParameters.timeLimitMillis + "ms)");
                }
            }
            if (exceptionsFromEval.size() > 0) {
                Exception e = exceptionsFromEval.get(0);
                throw e;
            }
            return jse.get("result");
        } else {
            throw new IllegalArgumentException("error: cant exec javascript, script hash not found in contract.");
        }
    }

    public static Object jo2Object(ScriptObjectMirror jo) {
        Object res;
        if (jo.isArray()) {
            res = new ArrayList<Object>();
            jo2Object(jo, res);
        } else {
            res = new HashMap<String, Object>();
            jo2Object(jo, res);
        }
        return res;
    }

    public static void jo2Object(ScriptObjectMirror jo, Object dest) {
        jo.forEach((k, v) -> {
            Object val = v;
            if (v instanceof ScriptObjectMirror)
                val = jo2Object((ScriptObjectMirror)v);
            if (dest instanceof ArrayList)
                ((ArrayList)dest).add(val);
            else if (dest instanceof HashMap)
                ((HashMap)dest).put(k, val);
        });
    }

    public static String hashId2hex(HashId hashId) {
        return Bytes.toHex(hashId.getDigest()).replaceAll(" ", "");
    }

    public static HashId hex2hashId(String hex) {
        return HashId.withDigest(Bytes.fromHex(hex).toArray());
    }

}
