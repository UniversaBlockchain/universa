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

    public static Binder createScriptBinder(byte[] jsFileContent, String jsFileName, JSApiScriptParameters scriptParameters, boolean putContentIntoContract) {
        BiSerializer biSerializer = new BiSerializer();
        HashId scriptHashId = HashId.of(jsFileContent);
        Binder scriptBinder = new Binder();
        scriptBinder.set("file_name", jsFileName);
        scriptBinder.set("__type", "file");
        scriptBinder.set("hash_id", biSerializer.serialize(scriptHashId));
        if (putContentIntoContract)
            scriptBinder.set("file_content", jsFileContent);
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

    public static Binder findScriptBinderByFileName(Binder data, String jsFileName) {
        BiDeserializer biDeserializer = new BiDeserializer();
        if (data == null)
            return null;
        List<Binder> res = new ArrayList<>();
        data.forEach((k, v) -> {
            if (v instanceof Binder) {
                Binder vBinder = (Binder) v;
                String fileName = vBinder.getString("file_name", null);
                if (jsFileName.equals(fileName))
                    res.add(vBinder);
            }
        });
        return res.size()>0 ? res.get(0) : null;
    }

    public static List<String> getFileNamesFromScriptBinder(Binder data) {
        List<String> res = new ArrayList<>();
        data.forEach((k, v) -> {
            if (v instanceof Binder) {
                Binder vBinder = (Binder) v;
                String fileName = vBinder.getString("file_name", null);
                res.add(fileName);
            }
        });
        return res;
    }

    public static String unpackJSString(Binder scriptBinder, byte[] jsFileContent) {
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
