package com.icodici.universa.contract.jsapi;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
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

    public static Binder createScriptBinder(byte[] jsFileContent, String jsFileName, boolean isCompressed) {
        BiSerializer biSerializer = new BiSerializer();
        HashId scriptHashId = HashId.of(jsFileContent);
        Binder scriptBinder = new Binder();
        scriptBinder.put("file_name", jsFileName);
        scriptBinder.put("__type", "file");
        scriptBinder.put("hash_id", biSerializer.serialize(scriptHashId));
        scriptBinder.put("compression", (isCompressed ? JSApiCompressionEnum.ZIP : JSApiCompressionEnum.RAW).toString());
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

    private static String unpackJSString_fromZipBak(byte[] jsFileContent) {
        try {
            Inflater decompressor = new Inflater();
            decompressor.setInput(jsFileContent);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!decompressor.finished()) {
                int count = decompressor.inflate(buf);
                bos.write(buf, 0, count);
            }
            bos.close();
            return new String(bos.toByteArray());
        } catch (Exception e) {
            throw new IllegalArgumentException("unable to unzip client javascript: " + e);
        }
    }

    private static String unpackJSString_fromZip(byte[] jsFileContent) {
        try {
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jsFileContent));
            ZipEntry ze = zis.getNextEntry();
            ByteArrayOutputStream scriptBytes = new ByteArrayOutputStream();
            byte[] buf = new byte[10];
            if (ze != null) {
                int count;
                while ((count = zis.read(buf)) >= 0)
                    scriptBytes.write(buf, 0, count);
            }
            zis.closeEntry();
            zis.close();
            return new String(scriptBytes.toByteArray());
        } catch (Exception e) {
            throw new IllegalArgumentException("unable to unzip client javascript: " + e);
        }
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
            String jsString = unpackJSString(scriptBinder, jsFileContent);
            jse.eval(jsString);
            return jse.get("result");
        } else {
            throw new IllegalArgumentException("error: cant exec javascript, script hash not found in contract.");
        }
    }

}
