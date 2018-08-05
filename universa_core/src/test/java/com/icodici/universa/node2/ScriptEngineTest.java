package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.jsapi.JSApiCompressionEnum;
import com.icodici.universa.contract.jsapi.JSApiHelpers;
import com.icodici.universa.contract.jsapi.JSApiScriptParameters;
import com.icodici.universa.contract.jsapi.permissions.JSApiChangeNumberPermission;
import com.icodici.universa.contract.jsapi.permissions.JSApiPermission;
import com.icodici.universa.contract.jsapi.permissions.JSApiSplitJoinPermission;
import com.icodici.universa.contract.permissions.*;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.network.TestKeys;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import org.junit.Test;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

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
        scriptParameters.timeLimitMillis= 500;
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        try {
            contract.execJS(js.getBytes());
            assert false;
        } catch (InterruptedException e) {
            System.out.println("InterruptedException: " + e);
            assert true;
        }
    }

    @Test
    public void testSimpleRole() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getShortAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getShortAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSimpleRole');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"', '"+k1.toString()+"', '"+k2.toString()+"');";
        js += "print('simpleRole: ' + simpleRole.getAllAddresses());";
        js += "result = simpleRole.getAllAddresses();";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        List<String> res = (List<String>)contract.execJS(js.getBytes());
        assertTrue(res.contains(k0.toString()));
        assertTrue(res.contains(k1.toString()));
        assertTrue(res.contains(k2.toString()));
        assertFalse(res.contains(k3.toString()));
    }

    @Test
    public void testSimpleRoleCheck() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getShortAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getShortAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSimpleRoleCheck');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"', '"+k1.toString()+"', '"+k2.toString()+"');";
        js += "print('simpleRole: ' + simpleRole.getAllAddresses());";
        js += "var check0 = simpleRole.isAllowedForAddresses('"+k0.toString()+"', '"+k1.toString()+"');";
        js += "var check1 = simpleRole.isAllowedForAddresses('"+k0.toString()+"', '"+k1.toString()+"', '"+k2.toString()+"');";
        js += "print('check0: ' + check0);";
        js += "print('check1: ' + check1);";
        js += "result = [check0, check1];";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(js.getBytes());
        assertFalse((boolean)res.get("0"));
        assertTrue((boolean)res.get("1"));
    }

    @Test
    public void testListRole() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getShortAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getShortAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testListRole');";
        js += "var simpleRole0 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var simpleRole1 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k1.toString()+"');";
        js += "var simpleRole2 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k2.toString()+"');";
        js += "print('simpleRole0: ' + simpleRole0.getAllAddresses());";
        js += "print('simpleRole1: ' + simpleRole1.getAllAddresses());";
        js += "print('simpleRole2: ' + simpleRole2.getAllAddresses());";
        js += "var listRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole0, simpleRole1, simpleRole2);";
        js += "print('listRole: ' + listRole.getAllAddresses());";
        js += "result = listRole.getAllAddresses();";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        List<String> res = (List<String>)contract.execJS(js.getBytes());
        assertTrue(res.contains(k0.toString()));
        assertTrue(res.contains(k1.toString()));
        assertTrue(res.contains(k2.toString()));
        assertFalse(res.contains(k3.toString()));
    }

    @Test
    public void testListRoleCheckAll() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getShortAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getShortAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testListRoleCheckAll');";
        js += "var simpleRole0 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var simpleRole1 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k1.toString()+"');";
        js += "var simpleRole2 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k2.toString()+"');";
        js += "var simpleRole3 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k3.toString()+"');";
        js += "print('simpleRole0: ' + simpleRole0.getAllAddresses());";
        js += "print('simpleRole1: ' + simpleRole1.getAllAddresses());";
        js += "print('simpleRole2: ' + simpleRole2.getAllAddresses());";
        js += "print('simpleRole3: ' + simpleRole3.getAllAddresses());";
        js += "var listSubRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole2, simpleRole3);";
        js += "var listRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole0, simpleRole1, listSubRole);";
        js += "print('listRole: ' + listRole.getAllAddresses());";
        js += "var check0 = listRole.isAllowedForAddresses('"+k0.toString()+"', '"+k1.toString()+"', '"+k2.toString()+"');";
        js += "var check1 = listRole.isAllowedForAddresses('"+k0.toString()+"', '"+k1.toString()+"', '"+k2.toString()+"', '"+k3.toString()+"');";
        js += "print('check0: ' + check0);";
        js += "print('check1: ' + check1);";
        js += "result = [check0, check1];";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(js.getBytes());
        assertFalse((boolean)res.get("0"));
        assertTrue((boolean)res.get("1"));
    }

    @Test
    public void testListRoleCheckAny() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getShortAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getShortAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testListRoleCheckAny');";
        js += "var simpleRole0 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var simpleRole1 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k1.toString()+"');";
        js += "var simpleRole2 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k2.toString()+"');";
        js += "var simpleRole3 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k3.toString()+"');";
        js += "print('simpleRole0: ' + simpleRole0.getAllAddresses());";
        js += "print('simpleRole1: ' + simpleRole1.getAllAddresses());";
        js += "print('simpleRole2: ' + simpleRole2.getAllAddresses());";
        js += "print('simpleRole3: ' + simpleRole3.getAllAddresses());";
        js += "var listSubRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole2, simpleRole3);";
        js += "var listRole = jsApi.getRoleBuilder().createListRole('listRole', 'any', simpleRole0, simpleRole1, listSubRole);";
        js += "print('listRole: ' + listRole.getAllAddresses());";
        js += "var check0 = listRole.isAllowedForAddresses('"+k0.toString()+"');";
        js += "var check1 = listRole.isAllowedForAddresses('"+k1.toString()+"');";
        js += "var check2 = listRole.isAllowedForAddresses('"+k2.toString()+"');";
        js += "var check3 = listRole.isAllowedForAddresses('"+k3.toString()+"');";
        js += "var check4 = listRole.isAllowedForAddresses('"+k3.toString()+"', '"+k2.toString()+"');";
        js += "print('check0: ' + check0);";
        js += "print('check1: ' + check1);";
        js += "print('check2: ' + check2);";
        js += "print('check3: ' + check3);";
        js += "print('check4: ' + check4);";
        js += "result = [check0, check1, check2, check3, check4];";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(js.getBytes());
        assertTrue((boolean)res.get("0"));
        assertTrue((boolean)res.get("1"));
        assertFalse((boolean)res.get("2"));
        assertFalse((boolean)res.get("3"));
        assertTrue((boolean)res.get("4"));
    }

    @Test
    public void testListRoleCheckQuorum() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getShortAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getShortAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testListRoleCheckQuorum');";
        js += "var simpleRole0 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var simpleRole1 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k1.toString()+"');";
        js += "var simpleRole2 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k2.toString()+"');";
        js += "var simpleRole3 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k3.toString()+"');";
        js += "print('simpleRole0: ' + simpleRole0.getAllAddresses());";
        js += "print('simpleRole1: ' + simpleRole1.getAllAddresses());";
        js += "print('simpleRole2: ' + simpleRole2.getAllAddresses());";
        js += "print('simpleRole3: ' + simpleRole3.getAllAddresses());";
        js += "var listSubRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole2, simpleRole3);";
        js += "var listRole = jsApi.getRoleBuilder().createListRole('listRole', 'any', simpleRole0, simpleRole1, listSubRole);";
        js += "listRole.setQuorum(2);";
        js += "print('listRole: ' + listRole.getAllAddresses());";
        js += "var check0 = listRole.isAllowedForAddresses('"+k0.toString()+"');";
        js += "var check1 = listRole.isAllowedForAddresses('"+k1.toString()+"');";
        js += "var check2 = listRole.isAllowedForAddresses('"+k2.toString()+"');";
        js += "var check3 = listRole.isAllowedForAddresses('"+k3.toString()+"');";
        js += "var check4 = listRole.isAllowedForAddresses('"+k3.toString()+"', '"+k2.toString()+"');";
        js += "var check5 = listRole.isAllowedForAddresses('"+k0.toString()+"', '"+k1.toString()+"');";
        js += "var check6 = listRole.isAllowedForAddresses('"+k0.toString()+"', '"+k2.toString()+"', '"+k3.toString()+"');";
        js += "var check7 = listRole.isAllowedForAddresses('"+k1.toString()+"', '"+k2.toString()+"', '"+k3.toString()+"');";
        js += "var check8 = listRole.isAllowedForAddresses('"+k1.toString()+"', '"+k2.toString()+"');";
        js += "print('check0: ' + check0);";
        js += "print('check1: ' + check1);";
        js += "print('check2: ' + check2);";
        js += "print('check3: ' + check3);";
        js += "print('check4: ' + check4);";
        js += "print('check5: ' + check5);";
        js += "print('check6: ' + check6);";
        js += "print('check7: ' + check7);";
        js += "print('check8: ' + check8);";
        js += "result = [check0, check1, check2, check3, check4, check5, check6, check7, check8];";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(js.getBytes());
        assertFalse((boolean)res.get("0"));
        assertFalse((boolean)res.get("1"));
        assertFalse((boolean)res.get("2"));
        assertFalse((boolean)res.get("3"));
        assertFalse((boolean)res.get("4"));
        assertTrue((boolean)res.get("5"));
        assertTrue((boolean)res.get("6"));
        assertTrue((boolean)res.get("7"));
        assertFalse((boolean)res.get("8"));
    }

    @Test
    public void testRoleLink() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getShortAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getShortAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testRoleLink');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var simpleRole1 = jsApi.getRoleBuilder().createSimpleRole('k1', '"+k1.toString()+"');";
        js += "var simpleRole2 = jsApi.getRoleBuilder().createSimpleRole('k2', '"+k2.toString()+"');";
        js += "var listRole = jsApi.getRoleBuilder().createListRole('issuer', 'all', simpleRole1, simpleRole2);";
        js += "var roleLink0 = jsApi.getRoleBuilder().createRoleLink('link0', 'owner');";
        js += "var roleLink1 = jsApi.getRoleBuilder().createRoleLink('link1', 'link0');";
        js += "var roleLink2 = jsApi.getRoleBuilder().createRoleLink('link2', 'issuer');";
        js += "var roleLink3 = jsApi.getRoleBuilder().createRoleLink('link3', 'link2');";
        js += "jsApi.getCurrentContract().registerRole(simpleRole);";
        js += "jsApi.getCurrentContract().registerRole(listRole);";
        js += "jsApi.getCurrentContract().registerRole(roleLink0);";
        js += "jsApi.getCurrentContract().registerRole(roleLink1);";
        js += "jsApi.getCurrentContract().registerRole(roleLink2);";
        js += "jsApi.getCurrentContract().registerRole(roleLink3);";
        js += "print('simpleRole: ' + simpleRole.getAllAddresses());";
        js += "print('listRole: ' + listRole.getAllAddresses());";
        js += "print('roleLink0: ' + roleLink0.getAllAddresses());";
        js += "print('roleLink1: ' + roleLink1.getAllAddresses());";
        js += "print('roleLink2: ' + roleLink2.getAllAddresses());";
        js += "print('roleLink3: ' + roleLink3.getAllAddresses());";
        js += "var check0 = roleLink0.isAllowedForAddresses('"+k0.toString()+"');";
        js += "var check1 = roleLink1.isAllowedForAddresses('"+k1.toString()+"');";
        js += "var check2 = roleLink2.isAllowedForAddresses('"+k2.toString()+"');";
        js += "var check3 = roleLink2.isAllowedForAddresses('"+k1.toString()+"', '"+k2.toString()+"');";
        js += "print('check0: ' + check0);";
        js += "print('check1: ' + check1);";
        js += "print('check2: ' + check2);";
        js += "print('check3: ' + check3);";
        js += "result = [check0, check1, check2, check3];";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(js.getBytes());
        assertTrue((boolean)res.get("0"));
        assertFalse((boolean)res.get("1"));
        assertFalse((boolean)res.get("2"));
        assertTrue((boolean)res.get("3"));
    }

    @Test
    public void testSplitJoinPermission() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSplitJoinPermission');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var splitJoinPermission = jsApi.getPermissionBuilder().createSplitJoinPermission(simpleRole, " +
                "{field_name: 'testval', min_value: 33, min_unit: 1e-7, join_match_fields: ['state.origin']}" +
                ");";
        js += "print('simpleRole: ' + simpleRole.getAllAddresses());";
        js += "result = splitJoinPermission;";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        JSApiSplitJoinPermission res = (JSApiSplitJoinPermission)contract.execJS(js.getBytes());
        SplitJoinPermission splitJoinPermission = (SplitJoinPermission)res.extractPermission(new JSApiAccessor());
        SplitJoinPermission sample = new SplitJoinPermission(new SimpleRole("test"), Binder.of(
                "field_name", "testval", "min_value", 33, "min_unit", 1e-7));

        Field field = SplitJoinPermission.class.getDeclaredField("fieldName");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(splitJoinPermission));

        field = SplitJoinPermission.class.getDeclaredField("minValue");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(splitJoinPermission));

        field = SplitJoinPermission.class.getDeclaredField("minUnit");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(splitJoinPermission));

        field = SplitJoinPermission.class.getDeclaredField("mergeFields");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(splitJoinPermission));
    }

    @Test
    public void testChangeNumberPermission() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testChangeNumberPermission');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var changeNumberPermission = jsApi.getPermissionBuilder().createChangeNumberPermission(simpleRole, " +
                "{field_name: 'testval', min_value: 44, max_value: 55, min_step: 1, max_step: 2}" +
                ");";
        js += "print('simpleRole: ' + simpleRole.getAllAddresses());";
        js += "result = changeNumberPermission;";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        JSApiChangeNumberPermission res = (JSApiChangeNumberPermission)contract.execJS(js.getBytes());
        ChangeNumberPermission changeNumberPermission = (ChangeNumberPermission)res.extractPermission(new JSApiAccessor());
        ChangeNumberPermission sample = new ChangeNumberPermission(new SimpleRole("test"), Binder.of(
                "field_name", "testval", "min_value", 44, "max_value", 55, "min_step", 1, "max_step", 2));

        Field field = ChangeNumberPermission.class.getDeclaredField("fieldName");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(changeNumberPermission));

        field = ChangeNumberPermission.class.getDeclaredField("minValue");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(changeNumberPermission));

        field = ChangeNumberPermission.class.getDeclaredField("maxValue");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(changeNumberPermission));

        field = ChangeNumberPermission.class.getDeclaredField("minStep");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(changeNumberPermission));

        field = ChangeNumberPermission.class.getDeclaredField("maxStep");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(changeNumberPermission));
    }

    @Test
    public void testChangeOwnerPermission() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testChangeOwnerPermission');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var changeOwnerPermission = jsApi.getPermissionBuilder().createChangeOwnerPermission(simpleRole);";
        js += "print('simpleRole: ' + simpleRole.getAllAddresses());";
        js += "result = changeOwnerPermission;";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        JSApiPermission res = (JSApiPermission) contract.execJS(js.getBytes());
        ChangeOwnerPermission changeOwnerPermission = (ChangeOwnerPermission)res.extractPermission(new JSApiAccessor());
        ChangeOwnerPermission sample = new ChangeOwnerPermission(new SimpleRole("test"));

        Field field = Permission.class.getDeclaredField("name");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(changeOwnerPermission));
    }

    @Test
    public void testModifyDataPermission() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testModifyDataPermission');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var modifyDataPermission = jsApi.getPermissionBuilder().createModifyDataPermission(simpleRole, " +
                "{some_field: [1, 2, 3]});";
        js += "print('simpleRole: ' + simpleRole.getAllAddresses());";
        js += "result = modifyDataPermission;";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        JSApiPermission res = (JSApiPermission) contract.execJS(js.getBytes());
        ModifyDataPermission changeOwnerPermission = (ModifyDataPermission)res.extractPermission(new JSApiAccessor());
        ModifyDataPermission sample = new ModifyDataPermission(new SimpleRole("test"), Binder.of("fields", Binder.of("some_field", Arrays.asList(1, 2, 3))));

        Field field = Permission.class.getDeclaredField("name");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(changeOwnerPermission));

        field = ModifyDataPermission.class.getDeclaredField("fields");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(changeOwnerPermission));
    }

    @Test
    public void testRevokePermission() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testRevokePermission');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var revokePermission = jsApi.getPermissionBuilder().createRevokePermission(simpleRole);";
        js += "print('simpleRole: ' + simpleRole.getAllAddresses());";
        js += "result = revokePermission;";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        JSApiPermission res = (JSApiPermission) contract.execJS(js.getBytes());
        RevokePermission revokePermission = (RevokePermission)res.extractPermission(new JSApiAccessor());
        RevokePermission sample = new RevokePermission(new SimpleRole("test"));

        Field field = Permission.class.getDeclaredField("name");
        field.setAccessible(true);
        assertEquals(field.get(sample), field.get(revokePermission));
    }

}
