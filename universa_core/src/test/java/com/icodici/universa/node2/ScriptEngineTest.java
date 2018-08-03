package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.jsapi.JSApiCompressionEnum;
import com.icodici.universa.contract.jsapi.JSApiHelpers;
import com.icodici.universa.contract.jsapi.JSApiScriptParameters;
import com.icodici.universa.node.network.TestKeys;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import org.junit.Test;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ScriptEngineTest {

    public interface CustomInterface {
        void funcA();
        String funcB(String prm);
    }

    public class CustomClass {
        public void funcA(String prm) {
            System.out.println("CustomClass.funcA("+prm+")");
        }
        public void funcB(HashId hashId) {
            System.out.println("CustomClass.funcB("+hashId.toBase64String()+")");
        }
    }

    class ClassFilter_restrictAll implements ClassFilter {
        @Override
        public boolean exposeToScripts(String s) {
            return false;
        }
    }

    class ClassFilter_allowSomething implements ClassFilter {
        private Set<String> allowedClasses = null;

        public ClassFilter_allowSomething() {
            allowedClasses = new HashSet<>();
            allowedClasses.add("com.icodici.universa.contract.Contract");
            allowedClasses.add("com.icodici.universa.HashId");
            allowedClasses.add("net.sergeych.tools.Do");
        }

        @Override
        public boolean exposeToScripts(String s) {
            if (allowedClasses.contains(s))
                return true;
            return false;
        }
    }

    @Test
    public void putJavaObjectIntoJS() throws Exception {
        try {
            ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_restrictAll());
            jse.put("obj", new CustomClass());
            jse.eval("obj.funcA('text1');");
        } catch (ScriptException e) {
            assertTrue(false);
        }
    }

    @Test
    public void createJavaObjectWithJS() throws Exception {
        try {
            ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_allowSomething());
            jse.put("obj", new CustomClass());
            jse.eval("obj.funcA('text1');");
            jse.eval("var id = com.icodici.universa.HashId.createRandom();");
            jse.eval("obj.funcB(id);");
        } catch (ScriptException e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void createJavaObjectWithJS_restricted() throws Exception {
        try {
            //ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_restrictAll());
            ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(s -> false);
            jse.put("obj", new CustomClass());
            jse.eval("obj.funcA('text1');");
            jse.eval("var id = com.icodici.universa.HashId.createRandom();");
            jse.eval("obj.funcB(id);");
            assertTrue(false);
        } catch (RuntimeException e) {
            System.out.println("restricted access: " + e);
            assertTrue(true);
        }
    }

    @Test
    public void calcSomethingWithJS() throws Exception {
        ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_restrictAll());
        jse.put("a", 33);
        jse.put("b", 44);
        jse.eval("var c = '' + (a + b);");
        String c = (String)jse.get("c");
        assertEquals("77", c);
    }

    @Test
    public void createContractWithJS() throws Exception {
        ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_allowSomething());
        jse.eval("var contract = new com.icodici.universa.contract.Contract();");
        jse.eval("contract.getDefinition().getData().put('someKey', 'someValue');");
        jse.eval("contract.seal()");
        Contract contract = (Contract)jse.get("contract");
        System.out.println("contract id: " + contract.getId());
        System.out.println("contract someKey: " + contract.getDefinition().getData().getString("someKey"));
        assertEquals("someValue", contract.getDefinition().getData().getString("someKey"));
    }

    @Test
    public void implementJavaInterfaceWithJS() throws Exception {
        ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_restrictAll());
        jse.eval("var customInterface = new Object(); customInterface.funcA = function() {print('custom funcA() hit!');}");
        jse.eval("customInterface.funcB = function(prm) {print('custom funcB() hit! prm='+prm); return 'js_'+prm;}");
        CustomInterface customInterfaceInstance = ((Invocable)jse).getInterface(jse.get("customInterface"), CustomInterface.class);
        customInterfaceInstance.funcA();
        String res = customInterfaceInstance.funcB("java");
        assertEquals("js_java", res);
    }

    private String[] prepareTestFile() throws Exception {
        String textToWrite2file = Bytes.random(32).toBase64();
        System.out.println("prepareTestFile with content: " + textToWrite2file);
        String fileName = "javax.test.file.txt";
        String tmpdir = System.getProperty("java.io.tmpdir");
        String strPath = tmpdir + "/" + fileName;
        Files.deleteIfExists(Paths.get(strPath));
        File f = new File(strPath);
        f.createNewFile();
        Files.write(Paths.get(strPath), textToWrite2file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        String readedText4check = new String(Files.readAllBytes(Paths.get(strPath)));
        assertEquals(readedText4check, textToWrite2file);
        return new String[]{strPath, textToWrite2file};
    }

    @Test
    public void openFileByJavaClass_success() throws Exception {
        String res[] = prepareTestFile();
        String path = res[0];
        String content = res[1];
        ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine();
        jse.put("path", path);
        jse.eval("load('nashorn:mozilla_compat.js');");
        jse.eval("importPackage('java.nio.file');");
        jse.eval("var readed = new java.lang.String(Files.readAllBytes(java.nio.file.Paths.get(path)));");
        jse.eval("print('path: ' + path);");
        jse.eval("print('content: ' + readed);");
        String readed = (String)jse.get("readed");
        assertEquals(readed, content);
    }

    @Test
    public void openFileByJavaClass_restricted() throws Exception {
        String res[] = prepareTestFile();
        String path = res[0];
        String content = res[1];
        ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_restrictAll());
        jse.put("path", path);
        jse.eval("load('nashorn:mozilla_compat.js');");
        jse.eval("importPackage('java.nio.file');");
        try {
            jse.eval("var readed = new java.lang.String(Files.readAllBytes(java.nio.file.Paths.get(path)));");
            assert false;
        } catch (ScriptException e) {
            System.out.println("ScriptException: " + e);
        }
        jse.eval("print('path: ' + path);");
        jse.eval("print('content: ' + readed);");
        String readed = (String)jse.get("readed");
        assertNotEquals(readed, content);
    }

    @Test
    public void openFileByJS_success() throws Exception {
        String res[] = prepareTestFile();
        String path = res[0];
        String content = res[1];
        ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine();
        jse.put("path", path);
        jse.eval("print(typeof String);");
        try {
            jse.eval("var reader = new FileReader();");
            jse.eval("print('path: ' + path);");
            assert false;
        } catch (ScriptException e) {
            System.out.println("ScriptException: " + e);
        }
    }

    @Test
    public void openFileByContract() throws Exception {
        String res[] = prepareTestFile();
        String path = res[0];
        String content = res[1];
        ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_allowSomething());
        jse.put("path", path);
        jse.eval("" +
                "function foo() {" +
                "   try {" +
                "       var contract = com.icodici.universa.contract.Contract.fromSealedFile(path);" +
                "   } catch (err) {" +
                "       print('exception: ' + err);" +
                "   }" +
                "}"
        );
        jse.eval("foo();");
        jse.eval("print('path: ' + path);");
    }

    @Test
    public void openFileByDo_success() throws Exception {
        String res[] = prepareTestFile();
        String path = res[0];
        String content = res[1];
        ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_allowSomething());
        //ScriptEngine jse = new NashornScriptEngineFactory().getScriptEngine(new ClassFilter_restrictAll());
        jse.put("path", path);
        jse.eval("load('nashorn:mozilla_compat.js');");
        jse.eval("importPackage('net.sergeych.tools');");
        jse.eval("function bin2string(array){var result = '';for(var i = 0; i < array.length; ++i){result+=(String.fromCharCode(array[i]));}return result;}");
        jse.eval("" +
                "function foo() {" +
                "   try {" +
                "       var bytes = Do.read(path);" +
                "       var res = bin2string(bytes);" +
                "       print(res);" +
                "       return res;" +
                "   } catch (err) {" +
                "       print('exception: ' + err);" +
                "   }" +
                "}"
        );
        jse.eval("var readed = foo();");
        jse.eval("print('path: ' + path);");
        String readed = (String)jse.get("readed");
        assertEquals(readed, content);
    }

    @Test
    public void jsInContract() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.setOwnerKeys(TestKeys.publicKey(1), TestKeys.publicKey(2), TestKeys.publicKey(3));
        contract.setCreatorKeys(TestKeys.publicKey(4), TestKeys.publicKey(5).getLongAddress());
        System.out.println("testKey[10].getShortAddress: " + TestKeys.publicKey(10).getShortAddress().toString());
        System.out.println("testKey[11].getShortAddress: " + TestKeys.publicKey(11).getShortAddress().toString());
        contract.getStateData().set("some_value", HashId.createRandom().toBase64String());
        contract.getStateData().set("some_hash_id", HashId.createRandom());
        String js = "";
        js += "print('hello world');";
        js += "var currentContract = jsApi.getCurrentContract();";
        js += "print('currentContract.getId(): ' + currentContract.getId());";
        js += "print('currentContract.getRevision(): ' + currentContract.getRevision());";
        js += "print('currentContract.getCreatedAt(): ' + currentContract.getCreatedAt());";
        js += "print('currentContract.getOrigin(): ' + currentContract.getOrigin());";
        js += "print('currentContract.getParent(): ' + currentContract.getParent());";
        js += "print('currentContract.getStateDataField(some_value): ' + currentContract.getStateDataField('some_value'));";
        js += "print('currentContract.getStateDataField(some_hash_id): ' + currentContract.getStateDataField('some_hash_id'));";
        js += "print('currentContract.getDefinitionDataField(scripts): ' + currentContract.getDefinitionDataField('scripts'));";
        js += "print('currentContract.getIssuer(): ' + currentContract.getIssuer());";
        js += "print('currentContract.getOwner(): ' + currentContract.getOwner());";
        js += "print('currentContract.getCreator(): ' + currentContract.getCreator());";
        js += "print('call currentContract.setOwner()...');";
        js += "currentContract.setOwner(['ZastWpWNPMqvVJAMocsMUTJg45i8LoC5Msmr7Lt9EaJJRwV2xV', 'a1sxhjdtGhNeji8SWJNPkwV5m6dgWfrQBnhiAxbQwZT6Y5FsXD']);";
        js += "print('currentContract.getOwner(): ' + currentContract.getOwner());";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        contract.execJS(js.getBytes());
    }

    @Test
    public void jsInContract_execZeroParams() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('jsApiParams.length: ' + jsApiParams.length);";
        js += "result = jsApiParams.length;";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        assertEquals(0, contract.execJS(js.getBytes()));
    }

    @Test
    public void jsInContract_execParams() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('jsApiParams.length: ' + jsApiParams.length);";
        js += "result = [jsApiParams.length, jsApiParams[0], jsApiParams[1]];";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror) contract.execJS(js.getBytes(), "prm1", "prm2");
        assertEquals(2, res.get("0"));
        assertEquals("prm1", res.get("1"));
        assertEquals("prm2", res.get("2"));
    }

    @Test
    public void extractContractShouldBeRestricted() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js1 = "";
        js1 += "var c = jsApi.getCurrentContract();";
        js1 += "var rc = c.extractContract(new Object());";
        js1 += "print('extractContract: ' + rc);";
        String js2 = "";
        js2 += "var c = jsApi.getCurrentContract();";
        js2 += "var rc = c.extractContract(null);";
        js2 += "print('extractContract: ' + rc);";
        contract.getState().setJS(js1.getBytes(), "client script 1.js", new JSApiScriptParameters());
        contract.getState().setJS(js2.getBytes(), "client script 2.js", new JSApiScriptParameters());
        contract.seal();
        contract = Contract.fromPackedTransaction(contract.getPackedTransaction());
        try {
            contract.execJS(js1.getBytes());
            assert false;
        } catch (ClassCastException e) {
            System.out.println(e);
            assert true;
        }
        try {
            contract.execJS(js2.getBytes());
            assert false;
        } catch (ClassCastException e) {
            System.out.println(e);
            assert true;
        }
    }

    @Test
    public void twoJsInContract() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js1d = "var result = 'return_from_script_1d';";
        String js2d = "var result = 'return_from_script_2d';";
        String js1s = "var result = 'return_from_script_1s';";
        String js2s = "var result = 'return_from_script_2s';";
        contract.getDefinition().setJS(js1d.getBytes(), "js1d.js", new JSApiScriptParameters());
        contract.getDefinition().setJS(js2d.getBytes(), "js2d.js", new JSApiScriptParameters());
        contract.getState().setJS(js1s.getBytes(), "js1s.js", new JSApiScriptParameters());
        contract.getState().setJS(js2s.getBytes(), "js2s.js", new JSApiScriptParameters());
        contract.seal();
        assertEquals("return_from_script_1d", contract.execJS(js1d.getBytes()));
        assertEquals("return_from_script_2d", contract.execJS(js2d.getBytes()));
        assertEquals("return_from_script_1s", contract.execJS(js1s.getBytes()));
        assertEquals("return_from_script_2s", contract.execJS(js2s.getBytes()));
        try {
            contract.execJS("print('another script');".getBytes());
            assert false;
        } catch (IllegalArgumentException e) {
            System.out.println(e);
            assert true;
        }
    }

    @Test
    public void fileName2fileKey() throws Exception {
        String in = "some long.file name with.extention";
        String expectedOut = "some_long_file_name_with_extention";
        assertEquals(expectedOut, JSApiHelpers.fileName2fileKey(in));
    }

    @Test
    public void rawJavaScript() throws Exception {
        String fileName = "somescript.js";
        String scriptDump = "cHJpbnQoJ2hlbGxvIHdvcmxkJyk7DQp2YXIgY3VycmVudENvbnRyYWN0ID0ganNBcGkuZ2V0Q3VycmVudENvbnRyYWN0KCk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldElkKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0SWQoKSk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldFJldmlzaW9uKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0UmV2aXNpb24oKSk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldENyZWF0ZWRBdCgpOiAnICsgY3VycmVudENvbnRyYWN0LmdldENyZWF0ZWRBdCgpKTsNCnByaW50KCdjdXJyZW50Q29udHJhY3QuZ2V0T3JpZ2luKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0T3JpZ2luKCkpOw0KcHJpbnQoJ2N1cnJlbnRDb250cmFjdC5nZXRQYXJlbnQoKTogJyArIGN1cnJlbnRDb250cmFjdC5nZXRQYXJlbnQoKSk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldFN0YXRlRGF0YUZpZWxkKHNvbWVfdmFsdWUpOiAnICsgY3VycmVudENvbnRyYWN0LmdldFN0YXRlRGF0YUZpZWxkKCdzb21lX3ZhbHVlJykpOw0KcHJpbnQoJ2N1cnJlbnRDb250cmFjdC5nZXRTdGF0ZURhdGFGaWVsZChzb21lX2hhc2hfaWQpOiAnICsgY3VycmVudENvbnRyYWN0LmdldFN0YXRlRGF0YUZpZWxkKCdzb21lX2hhc2hfaWQnKSk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldERlZmluaXRpb25EYXRhRmllbGQoc2NyaXB0cyk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0RGVmaW5pdGlvbkRhdGFGaWVsZCgnc2NyaXB0cycpKTsNCnByaW50KCdjdXJyZW50Q29udHJhY3QuZ2V0SXNzdWVyKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0SXNzdWVyKCkpOw0KcHJpbnQoJ2N1cnJlbnRDb250cmFjdC5nZXRPd25lcigpOiAnICsgY3VycmVudENvbnRyYWN0LmdldE93bmVyKCkpOw0KcHJpbnQoJ2N1cnJlbnRDb250cmFjdC5nZXRDcmVhdG9yKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0Q3JlYXRvcigpKTsNCnByaW50KCdjYWxsIGN1cnJlbnRDb250cmFjdC5zZXRPd25lcigpLi4uJyk7DQpjdXJyZW50Q29udHJhY3Quc2V0T3duZXIoWydaYXN0V3BXTlBNcXZWSkFNb2NzTVVUSmc0NWk4TG9DNU1zbXI3THQ5RWFKSlJ3VjJ4VicsICdhMXN4aGpkdEdoTmVqaThTV0pOUGt3VjVtNmRnV2ZyUUJuaGlBeGJRd1pUNlk1RnNYRCddKTsNCnByaW50KCdjdXJyZW50Q29udHJhY3QuZ2V0T3duZXIoKTogJyArIGN1cnJlbnRDb250cmFjdC5nZXRPd25lcigpKTsNCnJlc3VsdCA9IGpzQXBpUGFyYW1zWzBdICsganNBcGlQYXJhbXNbMV07DQo=";
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().set("some_value", HashId.createRandom().toBase64String());
        contract.getStateData().set("some_hash_id", HashId.createRandom());
        contract.getDefinition().setJS(Base64.decodeLines(scriptDump), fileName, new JSApiScriptParameters());
        contract.seal();
        String res = (String)contract.execJS(Base64.decodeLines(scriptDump), "3", "6");
        System.out.println("res: " + res);
        assertEquals("36", res);
        String compression = contract.getDefinition().getData().getOrThrow("scripts", JSApiHelpers.fileName2fileKey(fileName), "compression");
        System.out.println("compression: " + compression);
        assertEquals(JSApiCompressionEnum.RAW, JSApiCompressionEnum.valueOf(compression));
    }

    @Test
    public void compressedJavaScript() throws Exception {
        String fileName = "somescript.zip";
        String scriptDump = "UEsDBBQAAgAIAEGVA02XbF8YbAEAAPoEAAANAAAAc29tZXNjcmlwdC5qc62UXU+DMBSG7038D9yVRUOckTk1XkzmzMiY+xJ0y7JU6KATKLYF9vNlyj6cUtR42/O+z3vSntOI4pDLwEO+T6SUUN8BlavDgwRSyY4pRSHXSMgptLl0LS1YI8KKi7j2uSSvLNEHac+1UrcduXIpAelIKiiK7QOUYIZJKIBsJWKURhHkyGlwAWtHI4bdU+xiUVdrgRjTg6sTAWYtEGOGPOu6CTlsYeQ7MiMBmiXQj1ExeM8Cth7whzAPMm+GnV/G5a6ywCaa4xDz7Il3Um2KI86KA78zgdxVFthmLEZUNLe5oGRG0lBIyes/mFpCy2aW7IGg73/Rsk2koijvm16omIAxZNyKrG7PeE1MvWEQmxkPI909U3G9QzTVYAE97/CLW6jrg9Q8XZrgWAKwypbewuF3XhctcH1o6d3eS2qqQc1xrTnt34Qebiyf++l4VHtSW+yxCab/dokUsdjffFXZ5sCATU6mmW/3oDrNpG9QSwECFAAUAAIACABBlQNNl2xfGGwBAAD6BAAADQAAAAAAAAAAACAAAAAAAAAAc29tZXNjcmlwdC5qc1BLBQYAAAAAAQABADsAAACXAQAAAAA=";
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().set("some_value", HashId.createRandom().toBase64String());
        contract.getStateData().set("some_hash_id", HashId.createRandom());
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.isCompressed = true;
        contract.getDefinition().setJS(Base64.decodeLines(scriptDump), fileName, scriptParameters);
        contract.seal();
        String res = (String)contract.execJS(Base64.decodeLines(scriptDump), "3", "6");
        System.out.println("res: " + res);
        assertEquals("36", res);
        String compression = contract.getDefinition().getData().getOrThrow("scripts", JSApiHelpers.fileName2fileKey(fileName), "compression");
        System.out.println("compression: " + compression);
        assertEquals(JSApiCompressionEnum.ZIP, JSApiCompressionEnum.valueOf(compression));
    }

    @Test
    public void jsApiTimeLimit() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "function hardWork(ms) {";
        js += "  var unixtime_ms = new Date().getTime();";
        js += "  while(new Date().getTime() < unixtime_ms + ms) {}";
        js += "}";
        js += "print('jsApiTimeLimit');";
        js += "print('start hardWork...');";
        js += "hardWork(10000);";
        js += "print('hardWork time is up');";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.timeLimitMillis= 2000;
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        try {
            contract.execJS(js.getBytes());
        } catch (InterruptedException e) {
            System.out.println("InterruptedException: " + e);
        }
    }

}
