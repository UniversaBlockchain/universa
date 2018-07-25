package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.network.TestKeys;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
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
        js += "print('currentContract.getDefinitionDataField(script): ' + currentContract.getDefinitionDataField('script'));";
        js += "print('currentContract.getIssuer(): ' + currentContract.getIssuer());";
        js += "print('currentContract.getOwner(): ' + currentContract.getOwner());";
        js += "print('currentContract.getCreator(): ' + currentContract.getCreator());";
        js += "print('call currentContract.setOwner()...');";
        js += "currentContract.setOwner(['ZastWpWNPMqvVJAMocsMUTJg45i8LoC5Msmr7Lt9EaJJRwV2xV', 'a1sxhjdtGhNeji8SWJNPkwV5m6dgWfrQBnhiAxbQwZT6Y5FsXD']);";
        js += "print('currentContract.getOwner(): ' + currentContract.getOwner());";
        contract.setJS(js);
        contract.seal();
        contract.execJS();
    }

    @Test
    public void jsInContract_execZeroParams() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('jsApiParams.length: ' + jsApiParams.length);";
        js += "result = jsApiParams.length;";
        contract.setJS(js);
        contract.seal();
        assertEquals(0, contract.execJS());
    }

    @Test
    public void jsInContract_execParams() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('jsApiParams.length: ' + jsApiParams.length);";
        js += "result = [jsApiParams.length, jsApiParams[0], jsApiParams[1]];";
        contract.setJS(js);
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror) contract.execJS("prm1", "prm2");
        assertEquals(2, res.get("0"));
        assertEquals("prm1", res.get("1"));
        assertEquals("prm2", res.get("2"));
    }

}
