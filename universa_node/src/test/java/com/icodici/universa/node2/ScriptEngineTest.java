package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.InnerContractsService;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.jsapi.*;
import com.icodici.universa.contract.jsapi.permissions.JSApiChangeNumberPermission;
import com.icodici.universa.contract.jsapi.permissions.JSApiPermission;
import com.icodici.universa.contract.jsapi.permissions.JSApiSplitJoinPermission;
import com.icodici.universa.contract.jsapi.roles.JSApiRole;
import com.icodici.universa.contract.jsapi.storage.JSApiStorage;
import com.icodici.universa.contract.permissions.*;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.contract.services.NSmartContract;
import com.icodici.universa.contract.services.SlotContract;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.TestKeys;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

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

    Main createMain(String name, String postfix, boolean nolog) throws InterruptedException {
        String path = new File("src/test_node_config_v2" + postfix + "/" + name).getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, nolog ? "--nolog" : ""};

        List<Main> mm = new ArrayList<>();

        Thread thread = new Thread(() -> {
            try {
                Main m = new Main(args);
                try {
                    m.config.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
                } catch (KeyAddress.IllegalAddressException e) {
                    e.printStackTrace();
                }

                try {
                    //m.config.getKeysWhiteList().add(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")));
                    m.config.getAddressesWhiteList().add(new KeyAddress(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")), 0, true));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //m.config.getKeysWhiteList().add(m.config.getUIssuerKey());
                m.waitReady();
                mm.add(m);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.setName("Node Server: " + name);
        thread.start();

        while (mm.size() == 0) {
            Thread.sleep(100);
        }
        return mm.get(0);
    }

    private TestSpace prepareTestSpace(PrivateKey key) throws Exception {
        TestSpace testSpace = new TestSpace();
        testSpace.nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            testSpace.nodes.add(createMain("node" + (i + 1), "", false));
        testSpace.node = testSpace.nodes.get(0);
        assertEquals("http://localhost:8080", testSpace.node.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", testSpace.node.myInfo.publicUrlString());
        testSpace.myKey = key;
        testSpace.client = new Client(testSpace.myKey, testSpace.node.myInfo, null);

        testSpace.clients = new ArrayList();
        for (int i = 0; i < 4; i++)
            testSpace.clients.add(new Client(testSpace.myKey, testSpace.nodes.get(i).myInfo, null));

        for (Main m : testSpace.nodes) {
            while (m.node.isSanitating())
                Thread.sleep(100);
        }

        return testSpace;
    }

    private class TestSpace {
        public List<Main> nodes = null;
        public Main node = null;
        PrivateKey myKey = null;
        Client client = null;
        Object uContractLock = new Object();
        Contract uContract = null;
        public ArrayList<Client> clients;
    }

    private Config configForProvider = new Config();

    private NSmartContract.NodeInfoProvider nodeInfoProvider = new NodeConfigProvider(configForProvider);

    @Before
    public void beforeScriptEngineTest() throws Exception {
        // add U issuer test key
        configForProvider.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
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
        js += "result = simpleRole;";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        JSApiRole res = (JSApiRole)contract.execJS(js.getBytes());
        assertTrue(res.isAllowedForKeys(TestKeys.publicKey(0), TestKeys.publicKey(1), TestKeys.publicKey(2)));
        assertFalse(res.isAllowedForKeys(TestKeys.publicKey(0)));
        assertFalse(res.isAllowedForKeys(TestKeys.publicKey(1)));
        assertFalse(res.isAllowedForKeys(TestKeys.publicKey(2)));
        assertFalse(res.isAllowedForKeys(TestKeys.publicKey(3)));
    }

    @Test
    public void testSimpleRoleCheck() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getShortAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getShortAddress();
        String p0 = TestKeys.publicKey(0).packToBase64String();
        String p1 = TestKeys.publicKey(1).packToBase64String();
        String p2 = TestKeys.publicKey(2).packToBase64String();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSimpleRoleCheck');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"', '"+k1.toString()+"', '"+k2.toString()+"');";
        js += "var check0 = simpleRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p0+"'), jsApi.base64toPublicKey('"+p1+"'));";
        js += "var check1 = simpleRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p0+"'), jsApi.base64toPublicKey('"+p1+"'), jsApi.base64toPublicKey('"+p2+"'));";
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
        js += "var listRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole0, simpleRole1, simpleRole2);";
        js += "result = listRole;";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        contract.getDefinition().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        JSApiRole res = (JSApiRole)contract.execJS(js.getBytes());
        assertTrue(res.isAllowedForKeys(TestKeys.publicKey(0), TestKeys.publicKey(1), TestKeys.publicKey(2)));
        assertFalse(res.isAllowedForKeys(TestKeys.publicKey(0)));
        assertFalse(res.isAllowedForKeys(TestKeys.publicKey(1)));
        assertFalse(res.isAllowedForKeys(TestKeys.publicKey(2)));
        assertFalse(res.isAllowedForKeys(TestKeys.publicKey(3)));
    }

    @Test
    public void testListRoleCheckAll() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getLongAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getShortAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getLongAddress();
        String p0 = TestKeys.publicKey(0).packToBase64String();
        String p1 = TestKeys.publicKey(1).packToBase64String();
        String p2 = TestKeys.publicKey(2).packToBase64String();
        String p3 = TestKeys.publicKey(3).packToBase64String();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testListRoleCheckAll');";
        js += "var simpleRole0 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var simpleRole1 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k1.toString()+"');";
        js += "var simpleRole2 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k2.toString()+"');";
        js += "var simpleRole3 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k3.toString()+"');";
        js += "var listSubRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole2, simpleRole3);";
        js += "var listRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole0, simpleRole1, listSubRole);";
        js += "var check0 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p0+"'), jsApi.base64toPublicKey('"+p1+"'), jsApi.base64toPublicKey('"+p2+"'));";
        js += "var check1 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p0+"'), jsApi.base64toPublicKey('"+p1+"'), jsApi.base64toPublicKey('"+p2+"'), jsApi.base64toPublicKey('"+p3+"'));";
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
        KeyAddress k0 = TestKeys.publicKey(0).getLongAddress();
        KeyAddress k1 = TestKeys.publicKey(1).getShortAddress();
        KeyAddress k2 = TestKeys.publicKey(2).getLongAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getShortAddress();
        String p0 = TestKeys.publicKey(0).packToBase64String();
        String p1 = TestKeys.publicKey(1).packToBase64String();
        String p2 = TestKeys.publicKey(2).packToBase64String();
        String p3 = TestKeys.publicKey(3).packToBase64String();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testListRoleCheckAny');";
        js += "var simpleRole0 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var simpleRole1 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k1.toString()+"');";
        js += "var simpleRole2 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k2.toString()+"');";
        js += "var simpleRole3 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k3.toString()+"');";
        js += "var listSubRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole2, simpleRole3);";
        js += "var listRole = jsApi.getRoleBuilder().createListRole('listRole', 'any', simpleRole0, simpleRole1, listSubRole);";
        js += "var check0 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p0+"'));";
        js += "var check1 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p1+"'));";
        js += "var check2 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p2+"'));";
        js += "var check3 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p3+"'));";
        js += "var check4 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p3+"'), jsApi.base64toPublicKey('"+p2+"'));";
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
        KeyAddress k2 = TestKeys.publicKey(2).getLongAddress();
        KeyAddress k3 = TestKeys.publicKey(3).getLongAddress();
        String p0 = TestKeys.publicKey(0).packToBase64String();
        String p1 = TestKeys.publicKey(1).packToBase64String();
        String p2 = TestKeys.publicKey(2).packToBase64String();
        String p3 = TestKeys.publicKey(3).packToBase64String();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testListRoleCheckQuorum');";
        js += "var simpleRole0 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var simpleRole1 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k1.toString()+"');";
        js += "var simpleRole2 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k2.toString()+"');";
        js += "var simpleRole3 = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k3.toString()+"');";
        js += "var listSubRole = jsApi.getRoleBuilder().createListRole('listRole', 'all', simpleRole2, simpleRole3);";
        js += "var listRole = jsApi.getRoleBuilder().createListRole('listRole', 'any', simpleRole0, simpleRole1, listSubRole);";
        js += "listRole.setQuorum(2);";
        js += "var check0 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p0+"'));";
        js += "var check1 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p1+"'));";
        js += "var check2 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p2+"'));";
        js += "var check3 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p3+"'));";
        js += "var check4 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p3+"'), jsApi.base64toPublicKey('"+p2+"'));";
        js += "var check5 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p0+"'), jsApi.base64toPublicKey('"+p1+"'));";
        js += "var check6 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p0+"'), jsApi.base64toPublicKey('"+p2+"'), jsApi.base64toPublicKey('"+p3+"'));";
        js += "var check7 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p1+"'), jsApi.base64toPublicKey('"+p2+"'), jsApi.base64toPublicKey('"+p3+"'));";
        js += "var check8 = listRole.isAllowedForKeys(jsApi.base64toPublicKey('"+p1+"'), jsApi.base64toPublicKey('"+p2+"'));";
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
        String p0 = TestKeys.publicKey(0).packToBase64String();
        String p1 = TestKeys.publicKey(1).packToBase64String();
        String p2 = TestKeys.publicKey(2).packToBase64String();
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
        js += "var check0 = roleLink0.isAllowedForKeys(jsApi.base64toPublicKey('"+p0+"'));";
        js += "var check1 = roleLink1.isAllowedForKeys(jsApi.base64toPublicKey('"+p1+"'));";
        js += "var check2 = roleLink2.isAllowedForKeys(jsApi.base64toPublicKey('"+p2+"'));";
        js += "var check3 = roleLink2.isAllowedForKeys(jsApi.base64toPublicKey('"+p1+"'), jsApi.base64toPublicKey('"+p2+"'));";
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
        String p0 = TestKeys.publicKey(0).packToBase64String();
        String p1 = TestKeys.publicKey(1).packToBase64String();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSplitJoinPermission');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var splitJoinPermission = jsApi.getPermissionBuilder().createSplitJoinPermission(simpleRole, " +
                "{field_name: 'testval', min_value: 33, min_unit: 1e-7, join_match_fields: ['state.origin']}" +
                ");";
        js += "jsApi.getCurrentContract().addPermission(splitJoinPermission);";
        js += "var isPermitted0 = jsApi.getCurrentContract().isPermitted('split_join', jsApi.base64toPublicKey('"+p0+"'));";
        js += "var isPermitted1 = jsApi.getCurrentContract().isPermitted('split_join', jsApi.base64toPublicKey('"+p1+"'));";
        js += "print('isPermitted0: ' + isPermitted0);";
        js += "print('isPermitted1: ' + isPermitted1);";
        js += "result = [splitJoinPermission, isPermitted0, isPermitted1];";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(js.getBytes());
        SplitJoinPermission splitJoinPermission = (SplitJoinPermission)((JSApiSplitJoinPermission)res.get("0")).extractPermission(new JSApiAccessor());
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

        assertTrue((boolean)res.get("1"));
        assertFalse((boolean)res.get("2"));
    }

    @Test
    public void testChangeNumberPermission() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        String p0 = TestKeys.publicKey(0).packToBase64String();
        String p1 = TestKeys.publicKey(1).packToBase64String();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testChangeNumberPermission');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var changeNumberPermission = jsApi.getPermissionBuilder().createChangeNumberPermission(simpleRole, " +
                "{field_name: 'testval', min_value: 44, max_value: 55, min_step: 1, max_step: 2}" +
                ");";
        js += "jsApi.getCurrentContract().addPermission(changeNumberPermission);";
        js += "var isPermitted0 = jsApi.getCurrentContract().isPermitted('decrement_permission', jsApi.base64toPublicKey('"+p0+"'));";
        js += "var isPermitted1 = jsApi.getCurrentContract().isPermitted('decrement_permission', jsApi.base64toPublicKey('"+p1+"'));";
        js += "print('isPermitted0: ' + isPermitted0);";
        js += "print('isPermitted1: ' + isPermitted1);";
        js += "result = [changeNumberPermission, isPermitted0, isPermitted1];";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(js.getBytes());
        ChangeNumberPermission changeNumberPermission = (ChangeNumberPermission)((JSApiChangeNumberPermission)res.get("0")).extractPermission(new JSApiAccessor());
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

        assertTrue((boolean)res.get("1"));
        assertFalse((boolean)res.get("2"));
    }

    @Test
    public void testChangeOwnerPermission() throws Exception {
        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testChangeOwnerPermission');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var changeOwnerPermission = jsApi.getPermissionBuilder().createChangeOwnerPermission(simpleRole);";
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

    @Test
    public void testTransactionalAccess() throws Exception {
        String t1value = "t1value";
        String t2value = "t2value";
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getTransactionalData().set("t1", t1value);
        String js = "";
        js += "print('testTransactionalAccess');";
        js += "var t1 = jsApi.getCurrentContract().getTransactionalDataField('t1');";
        js += "print('t1: ' + t1);";
        js += "jsApi.getCurrentContract().setTransactionalDataField('t2', '"+t2value+"');";
        js += "result = t1;";
        contract.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        String res = (String)contract.execJS(js.getBytes());
        assertEquals(t1value, res);
        assertEquals(t2value, contract.getTransactionalData().getStringOrThrow("t2"));
        System.out.println("t2: " + contract.getTransactionalData().getStringOrThrow("t2"));
    }

    private List<String> prepareSharedFoldersForTest(String f1content, String f2content, String f3content) throws Exception {
        String tmpdir = System.getProperty("java.io.tmpdir");
        String strPath1 = tmpdir + "/" + "sharedTest1";
        String strPath2 = tmpdir + "/" + "sharedTest2";
        File strPath1File = new File(strPath1);
        File strPath2File = new File(strPath2);
        strPath1File.mkdirs();
        strPath2File.mkdirs();
        File f1 = new File(strPath1 + "/f1.txt");
        File f2 = new File(strPath2 + "/folder/f2.txt");
        File f3 = new File(tmpdir + "/f3.txt");
        f1.delete();
        f2.delete();
        f3.delete();
        f1.getParentFile().mkdirs();
        f2.getParentFile().mkdirs();
        f3.getParentFile().mkdirs();
        f1.createNewFile();
        f2.createNewFile();
        f3.createNewFile();
        Files.write(f1.toPath(), f1content.getBytes());
        Files.write(f2.toPath(), f2content.getBytes());
        Files.write(f3.toPath(), f3content.getBytes());
        List<String> res = new ArrayList<>();
        res.add(strPath1);
        res.add(strPath2);
        return res;
    }

    @Test
    public void testSharedFolders_read() throws Exception {
        String f1content = "f1 content";
        String f2content = "f2 content";
        String f3content = "f3 content";
        List<String> sharedFolders = prepareSharedFoldersForTest(f1content, f2content, f3content);
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSharedFolders_read');";
        js += "function bin2string(array){var result = '';for(var i = 0; i < array.length; ++i){result+=(String.fromCharCode(array[i]));}return result;}";
        js += "var file1Content = jsApi.getSharedFolders().readAllBytes('f1.txt');";
        js += "var file2Content = jsApi.getSharedFolders().readAllBytes('folder/f2.txt');";
        js += "print('file1Content: ' + bin2string(file1Content));";
        js += "print('file2Content: ' + bin2string(file2Content));";
        js += "result = [bin2string(file1Content), bin2string(file2Content)];";
        JSApiExecOptions execOptions = new JSApiExecOptions();
        execOptions.sharedFolders.addAll(sharedFolders);
        contract.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(execOptions, js.getBytes());
        assertEquals(f1content, res.get("0"));
        assertEquals(f2content, res.get("1"));
    }

    @Test
    public void testSharedFolders_restrictedPath() throws Exception {
        String f1content = "f1 content";
        String f2content = "f2 content";
        String f3content = "f3 content";
        List<String> sharedFolders = prepareSharedFoldersForTest(f1content, f2content, f3content);
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSharedFolders_restrictedPath');";
        js += "function bin2string(array){var result = '';for(var i = 0; i < array.length; ++i){result+=(String.fromCharCode(array[i]));}return result;}";
        js += "try {";
        js += "  var file3Content = jsApi.getSharedFolders().readAllBytes('../f3.txt');";
        js += "  print('file3Content: ' + bin2string(file3Content));";
        js += "} catch (err) {";
        js += "  result = err;";
        js += "}";
        JSApiExecOptions execOptions = new JSApiExecOptions();
        execOptions.sharedFolders.addAll(sharedFolders);
        contract.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        IOException res = (IOException) contract.execJS(execOptions, js.getBytes());
        System.out.println("IOException from js: " + res);
        assertTrue(res.toString().contains("file '../f3.txt' not found in shared folders"));
    }

    @Test
    public void testSharedFolders_write() throws Exception {
        String f1content = "f1 content";
        String f2content = "f2 content";
        String f3content = "f3 content";
        String f4content = "f4 content";
        List<String> sharedFolders = prepareSharedFoldersForTest(f1content, f2content, f3content);
        Paths.get(sharedFolders.get(0) + "/folder2/f4.txt").toFile().delete();
        Paths.get(sharedFolders.get(0) + "/folder2/f4.txt").toFile().getParentFile().delete();
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSharedFolders_write');";
        js += "var file4Content = '"+f4content+"';";
        js += "jsApi.getSharedFolders().writeNewFile('folder2/f4.txt', jsApi.string2bin(file4Content));";
        JSApiExecOptions execOptions = new JSApiExecOptions();
        execOptions.sharedFolders.addAll(sharedFolders);
        contract.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        contract.execJS(execOptions, js.getBytes());
        String f4readed = new String(Files.readAllBytes(Paths.get(sharedFolders.get(0) + "/folder2/f4.txt")));
        System.out.println("f4: " + f4readed);
        assertEquals(f4content, f4readed);
    }

    @Test
    public void testSharedFolders_rewrite() throws Exception {
        String f1content = "f1 content";
        String f1contentUpdated = "f1 content updated";
        String f2content = "f2 content";
        String f2contentUpdated = "f2 content updated";
        String f3content = "f3 content";
        List<String> sharedFolders = prepareSharedFoldersForTest(f1content, f2content, f3content);
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSharedFolders_rewrite');";
        js += "jsApi.getSharedFolders().rewriteExistingFile('f1.txt', jsApi.string2bin('"+f1contentUpdated+"'));";
        js += "jsApi.getSharedFolders().rewriteExistingFile('folder/f2.txt', jsApi.string2bin('"+f2contentUpdated+"'));";
        JSApiExecOptions execOptions = new JSApiExecOptions();
        execOptions.sharedFolders.addAll(sharedFolders);
        contract.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        contract.execJS(execOptions, js.getBytes());
        String f1readed = new String(Files.readAllBytes(Paths.get(sharedFolders.get(0) + "/f1.txt")));
        String f2readed = new String(Files.readAllBytes(Paths.get(sharedFolders.get(1) + "/folder/f2.txt")));
        assertNotEquals(f1content, f1readed);
        assertNotEquals(f2content, f2readed);
        assertEquals(f1contentUpdated, f1readed);
        assertEquals(f2contentUpdated, f2readed);
    }

    @Test
    public void testSharedStorage() throws Exception {
        String sharedStoragePath = JSApiStorage.getSharedStoragePath();
        String testFileName = "./someFolder/file1.txt";
        Paths.get(sharedStoragePath + testFileName).toFile().delete();
        String testString1 = "testString1_" + HashId.createRandom().toBase64String();
        String testString2 = "testString2_" + HashId.createRandom().toBase64String();
        System.out.println("testString1: " + testString1);
        System.out.println("testString2: " + testString2);
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testSharedStorage');";
        js += "function bin2string(array){var result = '';for(var i = 0; i < array.length; ++i){result+=(String.fromCharCode(array[i]));}return result;}";
        js += "var sharedStorage = jsApi.getSharedStorage();";
        js += "sharedStorage.writeNewFile('"+testFileName+"', jsApi.string2bin('"+testString1+"'));";
        js += "var file1readed = bin2string(sharedStorage.readAllBytes('"+testFileName+"'));";
        js += "sharedStorage.rewriteExistingFile('"+testFileName+"', jsApi.string2bin('"+testString2+"'));";
        js += "var file2readed = bin2string(sharedStorage.readAllBytes('"+testFileName+"'));";
        js += "var result = [file1readed, file2readed]";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_STORAGE, true);
        contract.getState().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(new JSApiExecOptions(), js.getBytes());
        assertEquals(testString1, res.get("0"));
        assertEquals(testString2, res.get("1"));
    }

    @Test
    public void testOriginStorage() throws Exception {
        String originStoragePath = JSApiStorage.getOriginStoragePath();
        String testFileName = "./someFolder/file1.txt";
        String testString1 = "testString1_" + HashId.createRandom().toBase64String();
        String testString2 = "testString2_" + HashId.createRandom().toBase64String();
        System.out.println("testString1: " + testString1);
        System.out.println("testString2: " + testString2);
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testOriginStorage');";
        js += "function bin2string(array){var result = '';for(var i = 0; i < array.length; ++i){result+=(String.fromCharCode(array[i]));}return result;}";
        js += "var originStorage = jsApi.getOriginStorage();";
        js += "originStorage.writeNewFile('"+testFileName+"', jsApi.string2bin('"+testString1+"'));";
        js += "var file1readed = bin2string(originStorage.readAllBytes('"+testFileName+"'));";
        js += "originStorage.rewriteExistingFile('"+testFileName+"', jsApi.string2bin('"+testString2+"'));";
        js += "var file2readed = bin2string(originStorage.readAllBytes('"+testFileName+"'));";
        js += "var result = [file1readed, file2readed]";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_ORIGIN_STORAGE, true);
        contract.getState().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        Paths.get(originStoragePath + JSApiHelpers.hashId2hex(contract.getOrigin()) + "/" + testFileName).toFile().delete();
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(new JSApiExecOptions(), js.getBytes());
        assertEquals(testString1, res.get("0"));
        assertEquals(testString2, res.get("1"));
        Contract contract2 = contract.createRevision();
        String js2 = "";
        js2 += "print('testOriginStorage_2');";
        js2 += "function bin2string(array){var result = '';for(var i = 0; i < array.length; ++i){result+=(String.fromCharCode(array[i]));}return result;}";
        js2 += "var originStorage = jsApi.getOriginStorage();";
        js2 += "var file2readed = bin2string(originStorage.readAllBytes('"+testFileName+"'));";
        js2 += "originStorage.rewriteExistingFile('"+testFileName+"', jsApi.string2bin('"+testString1+"'));";
        js2 += "var file1readed = bin2string(originStorage.readAllBytes('"+testFileName+"'));";
        js2 += "var result = [file1readed, file2readed]";
        contract2.getState().setJS(js2.getBytes(), "client script.js", scriptParameters);
        contract2.addSignerKey(TestKeys.privateKey(0));
        contract2.seal();
        ScriptObjectMirror res2 = (ScriptObjectMirror)contract2.execJS(new JSApiExecOptions(), js2.getBytes());
        assertEquals(testString1, res2.get("0"));
        assertEquals(testString2, res2.get("1"));
    }

    @Test
    public void testRevisionStorage() throws Exception {
        String revisionStoragePath = JSApiStorage.getRevisionStoragePath();
        String testFileName = "./someFolder/file1.txt";
        String testString1a = "testString1a_" + HashId.createRandom().toBase64String();
        String testString1b = "testString1b_" + HashId.createRandom().toBase64String();
        String testString2a = "testString2a_" + HashId.createRandom().toBase64String();
        String testString2b = "testString2b_" + HashId.createRandom().toBase64String();
        String testString3a = "testString3a_" + HashId.createRandom().toBase64String();
        String testString3b = "testString3b_" + HashId.createRandom().toBase64String();
        System.out.println("testString1a: " + testString1a);
        System.out.println("testString1b: " + testString1b);
        System.out.println("testString2a: " + testString2a);
        System.out.println("testString2b: " + testString2b);
        System.out.println("testString3a: " + testString3a);
        System.out.println("testString3b: " + testString3b);
        Contract contract1 = new Contract(TestKeys.privateKey(0));
        String js1 = "";
        js1 += "print('testRevisionStorage');";
        js1 += "function bin2string(array){var result = '';for(var i = 0; i < array.length; ++i){result+=(String.fromCharCode(array[i]));}return result;}";
        js1 += "var revisionStorage = jsApi.getRevisionStorage();";
        js1 += "revisionStorage.writeNewFile('"+testFileName+"', jsApi.string2bin('"+testString1a+"'));";
        js1 += "var file1Areaded = bin2string(revisionStorage.readAllBytes('"+testFileName+"'));";
        js1 += "revisionStorage.rewriteExistingFile('"+testFileName+"', jsApi.string2bin('"+testString1b+"'));";
        js1 += "var file1Breaded = bin2string(revisionStorage.readAllBytes('"+testFileName+"'));";
        js1 += "var fileParentReaded = null;";
        js1 += "try {";
        js1 += "  fileParentReaded = revisionStorage.readAllBytesFromParent('"+testFileName+"');";
        js1 += "} catch (err) {";
        js1 += "  fileParentReaded = null;";
        js1 += "}";
        js1 += "var result = [file1Areaded, file1Breaded, fileParentReaded]";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_REVISION_STORAGE, true);
        contract1.getState().setJS(js1.getBytes(), "client script.js", scriptParameters);
        contract1.seal();
        System.out.println("contract1.getId: " + Bytes.toHex(contract1.getId().getDigest()).replaceAll(" ", ""));
        System.out.println("contract1.getParent: " + contract1.getParent());
        Paths.get(revisionStoragePath + JSApiHelpers.hashId2hex(contract1.getId()) + "/" + testFileName).toFile().delete();
        ScriptObjectMirror res = (ScriptObjectMirror)contract1.execJS(new JSApiExecOptions(), js1.getBytes());
        assertEquals(testString1a, res.get("0"));
        assertEquals(testString1b, res.get("1"));
        assertNull(res.get("2"));

        Contract contract2 = contract1.createRevision();
        String js2 = "";
        js2 += "print('testRevisionStorage_2');";
        js2 += "function bin2string(array){var result = '';for(var i = 0; i < array.length; ++i){result+=(String.fromCharCode(array[i]));}return result;}";
        js2 += "var revisionStorage = jsApi.getRevisionStorage();";
        js2 += "revisionStorage.writeNewFile('"+testFileName+"', jsApi.string2bin('"+testString2a+"'));";
        js2 += "var file2Areaded = bin2string(revisionStorage.readAllBytes('"+testFileName+"'));";
        js2 += "revisionStorage.rewriteExistingFile('"+testFileName+"', jsApi.string2bin('"+testString2b+"'));";
        js2 += "var file2Breaded = bin2string(revisionStorage.readAllBytes('"+testFileName+"'));";
        js2 += "var fileParentReaded = revisionStorage.readAllBytesFromParent('"+testFileName+"');";
        js2 += "fileParentReaded = bin2string(fileParentReaded);";
        js2 += "var result = [file2Areaded, file2Breaded, fileParentReaded]";
        contract2.getState().setJS(js2.getBytes(), "client script.js", scriptParameters);
        contract2.seal();
        System.out.println("contract2.getId: " + Bytes.toHex(contract2.getId().getDigest()).replaceAll(" ", ""));
        System.out.println("contract2.getParent: " + Bytes.toHex(contract2.getParent().getDigest()).replaceAll(" ", ""));
        Paths.get(revisionStoragePath + JSApiHelpers.hashId2hex(contract2.getId()) + "/" + testFileName).toFile().delete();
        ScriptObjectMirror res2 = (ScriptObjectMirror)contract2.execJS(new JSApiExecOptions(), js2.getBytes());
        assertEquals(testString2a, res2.get("0"));
        assertEquals(testString2b, res2.get("1"));
        assertEquals(testString1b, res2.get("2"));

        Contract contract3 = contract2.createRevision();
        String js3 = "";
        js3 += "print('testRevisionStorage_3');";
        js3 += "function bin2string(array){var result = '';for(var i = 0; i < array.length; ++i){result+=(String.fromCharCode(array[i]));}return result;}";
        js3 += "var revisionStorage = jsApi.getRevisionStorage();";
        js3 += "revisionStorage.writeNewFile('"+testFileName+"', jsApi.string2bin('"+testString3a+"'));";
        js3 += "var file3Areaded = bin2string(revisionStorage.readAllBytes('"+testFileName+"'));";
        js3 += "revisionStorage.rewriteExistingFile('"+testFileName+"', jsApi.string2bin('"+testString3b+"'));";
        js3 += "var file3Breaded = bin2string(revisionStorage.readAllBytes('"+testFileName+"'));";
        js3 += "var fileParentReaded = revisionStorage.readAllBytesFromParent('"+testFileName+"');";
        js3 += "fileParentReaded = bin2string(fileParentReaded);";
        js3 += "var result = [file3Areaded, file3Breaded, fileParentReaded]";
        contract3.getState().setJS(js3.getBytes(), "client script.js", scriptParameters);
        contract3.seal();
        System.out.println("contract3.getId: " + Bytes.toHex(contract3.getId().getDigest()).replaceAll(" ", ""));
        System.out.println("contract3.getParent: " + Bytes.toHex(contract3.getParent().getDigest()).replaceAll(" ", ""));
        Paths.get(revisionStoragePath + JSApiHelpers.hashId2hex(contract3.getId()) + "/" + testFileName).toFile().delete();
        ScriptObjectMirror res3 = (ScriptObjectMirror)contract3.execJS(new JSApiExecOptions(), js3.getBytes());
        assertEquals(testString3a, res3.get("0"));
        assertEquals(testString3b, res3.get("1"));
        assertEquals(testString2b, res3.get("2"));
    }

    @Test
    public void scriptPermissionsToBinder() throws Exception {
        JSApiScriptParameters params = new JSApiScriptParameters();
        params.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_ORIGIN_STORAGE, true);
        params.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_REVISION_STORAGE, true);
        params.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_STORAGE, true);
        params.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_FOLDERS, true);
        params = JSApiScriptParameters.fromBinder(params.toBinder());
        assertTrue(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_ORIGIN_STORAGE));
        assertTrue(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_REVISION_STORAGE));
        assertTrue(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_STORAGE));
        assertTrue(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_FOLDERS));

        params.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_ORIGIN_STORAGE, false);
        params.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_REVISION_STORAGE, false);
        params.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_STORAGE, false);
        params.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_FOLDERS, false);
        params = JSApiScriptParameters.fromBinder(params.toBinder());
        assertFalse(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_ORIGIN_STORAGE));
        assertFalse(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_REVISION_STORAGE));
        assertFalse(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_STORAGE));
        assertFalse(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_FOLDERS));
    }

    @Test
    public void scriptPermissionsDefaultStates() throws Exception {
        JSApiScriptParameters params = new JSApiScriptParameters();
        params = JSApiScriptParameters.fromBinder(params.toBinder());
        assertFalse(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_ORIGIN_STORAGE));
        assertFalse(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_REVISION_STORAGE));
        assertFalse(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_STORAGE));
        assertTrue(params.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_FOLDERS));
    }

    @Test
    public void testUrlParser() throws Exception {
        JSApiUrlParser urlParser = new JSApiUrlParser();

        urlParser.addUrlMask("universa.com");
        urlParser.addUrlMask("t2.universa.com:80");
        urlParser.addUrlMask("t3.universa.com:3333");
        urlParser.addUrlMask("utoken.io");
        urlParser.addUrlMask("test1.utoken.io:80");
        urlParser.addUrlMask("test2.utoken.io:443");
        urlParser.addUrlMask("test3.utoken.io:8080");
        urlParser.addUrlMask("test4.utoken.io:*");
        urlParser.addUrlMask("*.utoken.io");
        urlParser.addUrlMask("*.utoken.io:4444");

        urlParser.addIpMask("192.168.33.44");
        urlParser.addIpMask("192.168.44.*:8080");

        assertTrue(urlParser.isUrlAllowed("https://www.utoken.io/imgres?imgurl=http%3A%2F%2Fkaifolog.ru%2Fuploads%2Fposts%2F2014-02%2F1392187237_005.jpg&imgrefurl=http%3A%2F%2Fkaifolog.ru%2Fpozitiv%2F5234-koteyki-55-foto.html&docid=5_IgRUU_v1M82M&tbnid=fN4J5V9ZY-tIiM%3A&vet=10ahUKEwjYn63jx43dAhVkkosKHW4TAcsQMwiOASgDMAM..i&w=640&h=640&bih=978&biw=1920&q=%D0%BA%D0%BE%D1%82%D0%B5%D0%B9%D0%BA%D0%B8&ved=0ahUKEwjYn63jx43dAhVkkosKHW4TAcsQMwiOASgDMAM&iact=mrc&uact=8"));
        assertTrue(urlParser.isUrlAllowed("utoken.io."));
        assertTrue(urlParser.isUrlAllowed("http://utoken.io"));
        assertTrue(urlParser.isUrlAllowed("https://utoken.io"));
        assertFalse(urlParser.isUrlAllowed("utoken.io.:8080"));
        assertFalse(urlParser.isUrlAllowed("test2.utoken.io.:8080"));
        assertTrue(urlParser.isUrlAllowed("test3.utoken.io.:8080"));
        assertTrue(urlParser.isUrlAllowed("test4.utoken.io.:8080"));
        assertTrue(urlParser.isUrlAllowed("utoken.io.:80"));
        assertTrue(urlParser.isUrlAllowed("utoken.io.:443"));
        assertTrue(urlParser.isUrlAllowed("test55.utoken.io"));
        assertFalse(urlParser.isUrlAllowed("test55.utoken.io:3333"));
        assertTrue(urlParser.isUrlAllowed("http://test55.utoken.io"));
        assertTrue(urlParser.isUrlAllowed("p1.test55.utoken.io"));
        assertFalse(urlParser.isUrlAllowed("p1.test55.utoken.io:3333"));
        assertTrue(urlParser.isUrlAllowed("p1.test55.utoken.io:4444"));
        assertTrue(urlParser.isUrlAllowed("universa.com"));
        assertFalse(urlParser.isUrlAllowed("t1.universa.com"));
        assertFalse(urlParser.isUrlAllowed("t2.universa.com"));
        assertTrue(urlParser.isUrlAllowed("http://t2.universa.com"));
        assertFalse(urlParser.isUrlAllowed("http://t3.universa.com"));
        assertFalse(urlParser.isUrlAllowed("https://t3.universa.com"));
        assertTrue(urlParser.isUrlAllowed("http://t3.universa.com:3333"));
        assertTrue(urlParser.isUrlAllowed("https://t3.universa.com:3333"));

        assertTrue(urlParser.isUrlAllowed("192.168.33.44"));
        assertFalse(urlParser.isUrlAllowed("192.168.33.45"));
        assertFalse(urlParser.isUrlAllowed("192.168.32.44"));
        assertFalse(urlParser.isUrlAllowed("192.168.32.44:3333"));
        assertTrue(urlParser.isUrlAllowed("http://192.168.33.44"));
        assertFalse(urlParser.isUrlAllowed("192.168.44.55"));
        assertTrue(urlParser.isUrlAllowed("192.168.44.55:8080"));
    }

    @Test
    public void testHttpClient() throws Exception {
        JSApiScriptParameters jsApiScriptParameters = new JSApiScriptParameters();
        jsApiScriptParameters.domainMasks.add("httpbin.org");
        JSApiHttpClient client = new JSApiHttpClient(jsApiScriptParameters);
        List res = client.sendGetRequest("https://httpbin.org/get?param=333", "json");
        System.out.println("resp code: " + res.get(0));
        System.out.println("resp body: " + res.get(1));
        assertEquals(200, res.get(0));
        assertEquals("333", ((Map)((Map)res.get(1)).get("args")).get("param"));
        res = client.sendPostRequest("http://httpbin.org/post", "json", Binder.of("postparam", 44), "form");
        System.out.println("resp code: " + res.get(0));
        System.out.println("resp body: " + res.get(1));
        assertEquals(200, res.get(0));
        assertEquals("44", ((Map)((Map)res.get(1)).get("form")).get("postparam"));
        res = client.sendPostRequest("http://httpbin.org/post", "json", Binder.of("jsonparam", 55), "json");
        System.out.println("resp code: " + res.get(0));
        System.out.println("resp body: " + res.get(1));
        assertEquals(200, res.get(0));
        assertEquals(55l, ((Map)((Map)res.get(1)).get("json")).get("jsonparam"));
    }

    @Test
    public void testHttpClientFromJS() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testHttpClientFromJS');";
        js += "var httpClient = jsApi.getHttpClient();";
        js += "var res0 = httpClient.sendGetRequest('https://httpbin.org/get?param=333', 'json');";
        js += "var res1 = httpClient.sendPostRequest('http://httpbin.org/post', 'json', {postparam:44}, 'form');";
        js += "var res2 = httpClient.sendPostRequest('http://httpbin.org/post', 'json', {jsonparam:55}, 'json');";
        js += "var result = [res0, res1, res2];";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.domainMasks.add("httpbin.org");
        scriptParameters.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_HTTP_CLIENT, true);
        contract.getState().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        contract = Contract.fromPackedTransaction(contract.getPackedTransaction());
        ScriptObjectMirror res = (ScriptObjectMirror)contract.execJS(new JSApiExecOptions(), js.getBytes());
        List res0 = (List)res.get("0");
        assertEquals(200, res0.get(0));
        assertEquals("333", ((Map)((Map)res0.get(1)).get("args")).get("param"));
        List res1 = (List)res.get("1");
        assertEquals(200, res1.get(0));
        assertEquals("44", ((Map)((Map)res1.get(1)).get("form")).get("postparam"));
        List res2 = (List)res.get("2");
        assertEquals(200, res2.get(0));
        assertEquals(55l, ((Map)((Map)res2.get(1)).get("json")).get("jsonparam"));
    }

    @Ignore
    @Test
    public void testHttpClientMultipart() throws Exception {
        String tmpdir = System.getProperty("java.io.tmpdir");
        String strPath1 = tmpdir + "/" + "testHttpClientMultipart";
        File strPath1File = new File(strPath1);
        strPath1File.mkdirs();
        File f1 = new File(strPath1 + "/file1");
        File f2 = new File(strPath1 + "/file2");
        f1.delete();
        f2.delete();
        f1.getParentFile().mkdirs();
        f2.getParentFile().mkdirs();
        f1.createNewFile();
        f2.createNewFile();
        Files.write(f1.toPath(), Do.randomBytes(32));
        Files.write(f2.toPath(), Do.randomBytes(1024));

        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('testHttpClientFromJS');";
        js += "var httpClient = jsApi.getHttpClient();";
        js += "var file1Content = jsApi.getSharedFolders().readAllBytes('file1');";
        js += "var file2Content = jsApi.getSharedFolders().readAllBytes('file2');";
        js += "var res = httpClient.sendPostRequestMultipart('http://192.168.1.131/upload', 'text', {imageName1:55, imageName2:66}, {image1:file1Content, image2:file2Content});";
        js += "var result = res;";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.ipMasks.add("192.168.1.*");
        scriptParameters.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_HTTP_CLIENT, true);
        contract.getState().setJS(js.getBytes(), "client script.js", scriptParameters);
        contract.seal();
        contract = Contract.fromPackedTransaction(contract.getPackedTransaction());
        JSApiExecOptions execOptions = new JSApiExecOptions();
        execOptions.sharedFolders.add(strPath1);
        Object res = contract.execJS(execOptions, js.getBytes());
        System.out.println(res);
    }

    @Test
    public void references() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "print('references');";
        js += "var ref = jsApi.getReferenceBuilder().createReference('EXISTING_STATE');";
        js += "ref.setConditions({'all_of':['ref.issuer=="+TestKeys.publicKey(1).getShortAddress().toString()+"']});";
        js += "jsApi.getCurrentContract().addReference(ref);";
        contract.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        contract = Contract.fromPackedTransaction(contract.getPackedTransaction());

        Contract batchContract = new Contract(TestKeys.privateKey(3));
        batchContract.addNewItems(contract);
        batchContract.seal();
        assertTrue(batchContract.check());
        contract.execJS(new JSApiExecOptions(), js.getBytes());
        contract.seal();
        batchContract.seal();
        assertFalse(batchContract.check());
    }

    @Test
    // simple http server creation
    public void httpServerTest1() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "var jsApiEvents = new Object();";
        js += "jsApiEvents.httpHandler_index = function(request, response){" +
                "  response.setResponseCode(201);" +
                "  response.setBodyAsPlainText('answer plain text');" +
                "};";
        js += "jsApiEvents.httpHandler_about = function(request, response){" +
                "  print('httpHandler_about params: ' + request.getParams());" +
                "  response.setBodyAsJson({status: 'ok', answer: 'some string value', jsApiParams: jsApiParams});" +
                "};";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.timeLimitMillis = 3000;
        contract.getState().setJS(js.getBytes(), "client script.js", scriptParameters, true);
        contract.seal();
        contract = Contract.fromPackedTransaction(contract.getPackedTransaction());

        JSApiHttpServerRoutes routes = new JSApiHttpServerRoutes();
        routes.setPortToListen(8880);
        String[] jsParams = new String[]{"prm1", "prm2"};
        routes.addNewRoute("/contract1", "httpHandler_index", contract, "client script.js", jsParams);
        routes.addNewRoute("/contract1/about", "httpHandler_about", contract, "client script.js", jsParams);
        JSApiHttpServer httpServer = new JSApiHttpServer(routes, new JSApiExecOptions(), hashId -> {
//            System.out.println("is approved hashId="+hashId+"? returns true");
            return true;
        }, (slotId, originId) -> null);

        // here can be any http client. JSApiHttpClient used just for easiness
        scriptParameters = new JSApiScriptParameters();
        scriptParameters.domainMasks.add("localhost:*");
        JSApiHttpClient httpClient = new JSApiHttpClient(scriptParameters);
        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        params.put("param2", 42);
        List httpRes = httpClient.sendPostRequest("http://localhost:8880/contract1", JSApiHttpClient.RESPTYPE_TEXT, params, "form");
        System.out.println("httpRes: " + httpRes);
        assertEquals(201, httpRes.get(0));
        assertEquals("answer plain text", httpRes.get(1));
        httpRes = httpClient.sendPostRequest("http://localhost:8880/contract1/about", JSApiHttpClient.RESPTYPE_TEXT, params, "form");
        System.out.println("httpRes: " + httpRes);
        assertEquals(200, httpRes.get(0));
        assertEquals(true, ((String)httpRes.get(1)).indexOf("\"answer\":\"some string value\"") != -1);
        assertEquals(true, ((String)httpRes.get(1)).indexOf("\"status\":\"ok\"") != -1);
        assertEquals(true, ((String)httpRes.get(1)).indexOf("\"jsApiParams\":[\"prm1\",\"prm2\"]") != -1);
        httpRes = httpClient.sendGetRequest("http://localhost:8880/contract1/about", JSApiHttpClient.RESPTYPE_JSON);
        System.out.println("httpRes: " + httpRes);
        assertEquals(200, httpRes.get(0));
        assertEquals("some string value", ((HashMap)httpRes.get(1)).get("answer"));
        assertEquals("ok", ((HashMap)httpRes.get(1)).get("status"));

        httpServer.stop();
    }

    @Test
    // checks that application/x-www-form-urlencoded request correctly parsed at server
    public void httpServerTest2() throws Exception {
        Contract contractServer = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "var jsApiEvents = new Object();";
        js += "jsApiEvents.httpHandler_test = function(request, response){" +
                "  response.setBodyAsJson({req_params: request.getParams()});" +
                "};";
        contractServer.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters(), true);
        contractServer.seal();
        contractServer = Contract.fromPackedTransaction(contractServer.getPackedTransaction());
        JSApiHttpServerRoutes routes = new JSApiHttpServerRoutes();
        routes.setPortToListen(8880);
        routes.addNewRoute("/test", "httpHandler_test", contractServer, "client script.js", null);
        JSApiHttpServer httpServer = new JSApiHttpServer(routes, new JSApiExecOptions(), hashId -> true, (slotId, originId) -> null);

        // here can be any http client. JSApiHttpClient used just for easiness
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.domainMasks.add("localhost:*");
        JSApiHttpClient httpClient = new JSApiHttpClient(scriptParameters);

        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        params.put("param2", 42l);
        List httpRes = httpClient.sendPostRequest("http://localhost:8880/test", JSApiHttpClient.RESPTYPE_JSON, params, "form");
        System.out.println("httpRes: " + httpRes);
        assertEquals("value1", ((HashMap)((HashMap)httpRes.get(1)).get("req_params")).get("param1"));
        assertEquals("42", ((HashMap)((HashMap)httpRes.get(1)).get("req_params")).get("param2"));

        httpServer.stop();
    }

    @Test
    // checks that application/json request correctly parsed at server
    public void httpServerTest3() throws Exception {
        Contract contractServer = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "var jsApiEvents = new Object();";
        js += "jsApiEvents.httpHandler_test = function(request, response){" +
                "  response.setBodyAsJson({req_params: request.getParams()});" +
                "};";
        contractServer.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters(), true);
        contractServer.seal();
        contractServer = Contract.fromPackedTransaction(contractServer.getPackedTransaction());
        JSApiHttpServerRoutes routes = new JSApiHttpServerRoutes();
        routes.setPortToListen(8880);
        routes.addNewRoute("/test", "httpHandler_test", contractServer, "client script.js", null);
        JSApiHttpServer httpServer = new JSApiHttpServer(routes, new JSApiExecOptions(), hashId -> true, (slotId, originId) -> null);

        // here can be any http client. JSApiHttpClient used just for easiness
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.domainMasks.add("localhost:*");
        JSApiHttpClient httpClient = new JSApiHttpClient(scriptParameters);

        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        params.put("param2", 42l);
        List httpRes = httpClient.sendPostRequest("http://localhost:8880/test", JSApiHttpClient.RESPTYPE_JSON, params, "json");
        System.out.println("httpRes: " + httpRes);
        assertEquals("value1", ((HashMap)((HashMap)httpRes.get(1)).get("req_params")).get("param1"));
        assertEquals(42l, ((HashMap)((HashMap)httpRes.get(1)).get("req_params")).get("param2"));

        httpServer.stop();
    }

    @Test
    // checks that multipart/form-data request correctly parsed at server
    public void httpServerTest4() throws Exception {
        Contract contractServer = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "var jsApiEvents = new Object();";
        js += "jsApiEvents.httpHandler_test = function(request, response){" +
                "  print('[js] req.params: '+request.getParams());" +
                "  response.setBodyAsJson({" +
                "    filename: jsApi.bin2base64(request.getParams().get('filename'))," +
                "    param1: request.getParams().get('param1')," +
                "    param2: parseInt(request.getParams().get('param2'))" +
                "  });" +
                "};";
        contractServer.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters(), true);
        contractServer.seal();
        contractServer = Contract.fromPackedTransaction(contractServer.getPackedTransaction());
        JSApiHttpServerRoutes routes = new JSApiHttpServerRoutes();
        routes.setPortToListen(8880);
        routes.addNewRoute("/test", "httpHandler_test", contractServer, "client script.js", null);
        JSApiHttpServer httpServer = new JSApiHttpServer(routes, new JSApiExecOptions(), hashId -> true, (slotId, originId) -> null);

        // here can be any http client. JSApiHttpClient used just for easiness
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.domainMasks.add("localhost:*");
        JSApiHttpClient httpClient = new JSApiHttpClient(scriptParameters);

        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        params.put("param2", 42l);
        Map<String, byte[]> files = new HashMap<>();
        byte[] fileData = Do.randomBytes(3200);
        files.put("filename", fileData);
        List httpRes = httpClient.sendPostRequestMultipart("http://localhost:8880/test", JSApiHttpClient.RESPTYPE_JSON, params, files);
        System.out.println("httpRes: " + httpRes);
        assertEquals(Base64.encodeString(fileData), ((HashMap)httpRes.get(1)).get("filename"));
        assertEquals("value1", ((HashMap)httpRes.get(1)).get("param1"));
        assertEquals(42l, ((HashMap)httpRes.get(1)).get("param2"));

        httpServer.stop();
    }

    @Test
    // server routes-config from json file, several server-contracts,
    // make requests from js-http-client to js-http-server, 'main' js event
    public void httpServerTest5() throws Exception {
        String tmpdir = System.getProperty("java.io.tmpdir");
        String strPathRoutes = tmpdir + "/" + "routes.json";
        Files.deleteIfExists(Paths.get(strPathRoutes));
        new File(strPathRoutes).createNewFile();
        String strPathContract1 = tmpdir + "/" + "contract1.tp";
        Files.deleteIfExists(Paths.get(strPathContract1));
        new File(strPathContract1).createNewFile();
        String strPathContract2 = tmpdir + "/" + "contract2.tp";
        Files.deleteIfExists(Paths.get(strPathContract2));
        new File(strPathContract2).createNewFile();

        Contract contract1 = new Contract(TestKeys.privateKey(0));
        String js1 = "";
        js1 += "var jsApiEvents = new Object();";
        js1 += "jsApiEvents.httpHandler_endpoint1 = function(request, response){" +
                "  response.setBodyAsPlainText('endpoint1');" +
                "};";
        String js2 = "";
        js2 += "var jsApiEvents = new Object();";
        js2 += "jsApiEvents.httpHandler_endpoint2 = function(request, response){" +
                "  response.setBodyAsPlainText('endpoint2');" +
                "};";
        JSApiScriptParameters scriptParameters1 = new JSApiScriptParameters();
        scriptParameters1.timeLimitMillis = 3000;
        contract1.getState().setJS(js1.getBytes(), "script1.js", scriptParameters1, true);
        contract1.getState().setJS(js2.getBytes(), "script2.js", scriptParameters1, true);
        contract1.seal();
        contract1 = Contract.fromPackedTransaction(contract1.getPackedTransaction());

        Contract contract2 = new Contract(TestKeys.privateKey(0));
        String js3 = "";
        js3 += "var jsApiEvents = new Object();";
        js3 += "jsApiEvents.httpHandler_endpoint3 = function(request, response){" +
                "  response.setBodyAsPlainText(jsApiParams[0]+jsApiParams[1]+jsApiParams[2]);" +
                "};";
        JSApiScriptParameters scriptParameters2 = new JSApiScriptParameters();
        scriptParameters2.timeLimitMillis = 3000;
        contract2.getState().setJS(js3.getBytes(), "script3.js", scriptParameters2, true);
        contract2.seal();
        contract2 = Contract.fromPackedTransaction(contract2.getPackedTransaction());

        String routesJsonString =
                "{\n" +
                "  \"listenPort\": \"8880\",\n" +
                "  \"routes\": [\n" +
                "    {\"endpoint\": \"/endpoint1\", \"handlerName\": \"httpHandler_endpoint1\", \"contractPath\": \""+strPathContract1+"\", \"scriptName\": \"script1.js\"},\n" +
                "    {\"endpoint\": \"/endpoint2\", \"handlerName\": \"httpHandler_endpoint2\", \"contractPath\": \""+strPathContract1+"\", \"scriptName\": \"script2.js\"},\n" +
                "    {\"endpoint\": \"/endpoint3\", \"handlerName\": \"httpHandler_endpoint3\", \"contractPath\": \""+strPathContract2+"\", \"scriptName\": \"script3.js\", \"jsApiParams\": [\"param1\", \"param2\", \"param3\"]}\n" +
                "  ]\n" +
                "}\n";

        Files.write(Paths.get(strPathContract1), contract1.getPackedTransaction(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        Files.write(Paths.get(strPathContract2), contract2.getPackedTransaction(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        JSApiHttpServerRoutes routes = new JSApiHttpServerRoutes(routesJsonString.getBytes(), (slotId, originId) -> null);
        JSApiHttpServer httpServer = new JSApiHttpServer(routes, new JSApiExecOptions(), hashId -> true, (slotId, originId) -> null);


        Contract httpClientContract = new Contract(TestKeys.privateKey(1));
        String js = "";
        js += "print('httpServerTest5');";
        js += "var jsApiEvents = new Object();";
        js += "jsApiEvents.main = function() {";
        js += "  var httpClient = jsApi.getHttpClient();";
        js += "  var res0 = httpClient.sendGetRequest('http://localhost:8880/endpoint1', 'text');";
        js += "  var res1 = httpClient.sendGetRequest('http://localhost:8880/endpoint2', 'text');";
        js += "  var res2 = httpClient.sendGetRequest('http://localhost:8880/endpoint3', 'text');";
        js += "  return [res0, res1, res2];";
        js += "}";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.domainMasks.add("localhost:*");
        scriptParameters.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_HTTP_CLIENT, true);
        httpClientContract.getState().setJS(js.getBytes(), "client script.js", scriptParameters, true);
        httpClientContract.seal();
        httpClientContract = Contract.fromPackedTransaction(httpClientContract.getPackedTransaction());
        ScriptObjectMirror res = (ScriptObjectMirror)httpClientContract.execJSByName("client script.js");
        System.out.println("res0: " + res.get("0"));
        System.out.println("res1: " + res.get("1"));
        System.out.println("res2: " + res.get("2"));
        assertEquals("endpoint1", ((List)res.get("0")).get(1));
        assertEquals("endpoint2", ((List)res.get("1")).get(1));
        assertEquals("param1param2param3", ((List)res.get("2")).get(1));

        httpServer.stop();
    }

    @Test
    public void jsFileContentInContract() throws Exception {
        String fileName = "somescript.js";
        String scriptDump = "cHJpbnQoJ2hlbGxvIHdvcmxkJyk7DQp2YXIgY3VycmVudENvbnRyYWN0ID0ganNBcGkuZ2V0Q3VycmVudENvbnRyYWN0KCk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldElkKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0SWQoKSk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldFJldmlzaW9uKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0UmV2aXNpb24oKSk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldENyZWF0ZWRBdCgpOiAnICsgY3VycmVudENvbnRyYWN0LmdldENyZWF0ZWRBdCgpKTsNCnByaW50KCdjdXJyZW50Q29udHJhY3QuZ2V0T3JpZ2luKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0T3JpZ2luKCkpOw0KcHJpbnQoJ2N1cnJlbnRDb250cmFjdC5nZXRQYXJlbnQoKTogJyArIGN1cnJlbnRDb250cmFjdC5nZXRQYXJlbnQoKSk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldFN0YXRlRGF0YUZpZWxkKHNvbWVfdmFsdWUpOiAnICsgY3VycmVudENvbnRyYWN0LmdldFN0YXRlRGF0YUZpZWxkKCdzb21lX3ZhbHVlJykpOw0KcHJpbnQoJ2N1cnJlbnRDb250cmFjdC5nZXRTdGF0ZURhdGFGaWVsZChzb21lX2hhc2hfaWQpOiAnICsgY3VycmVudENvbnRyYWN0LmdldFN0YXRlRGF0YUZpZWxkKCdzb21lX2hhc2hfaWQnKSk7DQpwcmludCgnY3VycmVudENvbnRyYWN0LmdldERlZmluaXRpb25EYXRhRmllbGQoc2NyaXB0cyk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0RGVmaW5pdGlvbkRhdGFGaWVsZCgnc2NyaXB0cycpKTsNCnByaW50KCdjdXJyZW50Q29udHJhY3QuZ2V0SXNzdWVyKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0SXNzdWVyKCkpOw0KcHJpbnQoJ2N1cnJlbnRDb250cmFjdC5nZXRPd25lcigpOiAnICsgY3VycmVudENvbnRyYWN0LmdldE93bmVyKCkpOw0KcHJpbnQoJ2N1cnJlbnRDb250cmFjdC5nZXRDcmVhdG9yKCk6ICcgKyBjdXJyZW50Q29udHJhY3QuZ2V0Q3JlYXRvcigpKTsNCnByaW50KCdjYWxsIGN1cnJlbnRDb250cmFjdC5zZXRPd25lcigpLi4uJyk7DQpjdXJyZW50Q29udHJhY3Quc2V0T3duZXIoWydaYXN0V3BXTlBNcXZWSkFNb2NzTVVUSmc0NWk4TG9DNU1zbXI3THQ5RWFKSlJ3VjJ4VicsICdhMXN4aGpkdEdoTmVqaThTV0pOUGt3VjVtNmRnV2ZyUUJuaGlBeGJRd1pUNlk1RnNYRCddKTsNCnByaW50KCdjdXJyZW50Q29udHJhY3QuZ2V0T3duZXIoKTogJyArIGN1cnJlbnRDb250cmFjdC5nZXRPd25lcigpKTsNCnJlc3VsdCA9IGpzQXBpUGFyYW1zWzBdICsganNBcGlQYXJhbXNbMV07DQo=";
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().set("some_value", HashId.createRandom().toBase64String());
        contract.getStateData().set("some_hash_id", HashId.createRandom());
        contract.getDefinition().setJS(Base64.decodeLines(scriptDump), fileName, new JSApiScriptParameters(), true);
        contract.seal();
        HashId scriptHash = HashId.of(Base64.decodeLines(scriptDump));
        String res = (String)contract.execJSByScriptHash(scriptHash, "3", "6");
        System.out.println("res: " + res);
        assertEquals("36", res);
        String compression = contract.getDefinition().getData().getOrThrow("scripts", JSApiHelpers.fileName2fileKey(fileName), "compression");
        System.out.println("compression: " + compression);
        assertEquals(JSApiCompressionEnum.RAW, JSApiCompressionEnum.valueOf(compression));
    }

    @Test
    public void jsFileContentInContract_byFilename() throws Exception {
        String fileName = "somescript.zip";
        String scriptDump = "UEsDBBQAAgAIAEGVA02XbF8YbAEAAPoEAAANAAAAc29tZXNjcmlwdC5qc62UXU+DMBSG7038D9yVRUOckTk1XkzmzMiY+xJ0y7JU6KATKLYF9vNlyj6cUtR42/O+z3vSntOI4pDLwEO+T6SUUN8BlavDgwRSyY4pRSHXSMgptLl0LS1YI8KKi7j2uSSvLNEHac+1UrcduXIpAelIKiiK7QOUYIZJKIBsJWKURhHkyGlwAWtHI4bdU+xiUVdrgRjTg6sTAWYtEGOGPOu6CTlsYeQ7MiMBmiXQj1ExeM8Cth7whzAPMm+GnV/G5a6ywCaa4xDz7Il3Um2KI86KA78zgdxVFthmLEZUNLe5oGRG0lBIyes/mFpCy2aW7IGg73/Rsk2koijvm16omIAxZNyKrG7PeE1MvWEQmxkPI909U3G9QzTVYAE97/CLW6jrg9Q8XZrgWAKwypbewuF3XhctcH1o6d3eS2qqQc1xrTnt34Qebiyf++l4VHtSW+yxCab/dokUsdjffFXZ5sCATU6mmW/3oDrNpG9QSwECFAAUAAIACABBlQNNl2xfGGwBAAD6BAAADQAAAAAAAAAAACAAAAAAAAAAc29tZXNjcmlwdC5qc1BLBQYAAAAAAQABADsAAACXAQAAAAA=";
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().set("some_value", HashId.createRandom().toBase64String());
        contract.getStateData().set("some_hash_id", HashId.createRandom());
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.isCompressed = true;
        contract.getDefinition().setJS(Base64.decodeLines(scriptDump), fileName, scriptParameters, true);
        contract.seal();
        String res = (String)contract.execJSByName("somescript.zip", "3", "6");
        System.out.println("res: " + res);
        assertEquals("36", res);
        String compression = contract.getDefinition().getData().getOrThrow("scripts", JSApiHelpers.fileName2fileKey(fileName), "compression");
        System.out.println("compression: " + compression);
        assertEquals(JSApiCompressionEnum.ZIP, JSApiCompressionEnum.valueOf(compression));
    }

    @Test
    public void jsDemo1() throws Exception {
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Contract contract = new Contract(TestKeys.privateKey(1));
        ModifyDataPermission perm = new ModifyDataPermission(contract.getOwner(), new Binder());
        perm.addField("test_value", Arrays.asList("0", "1"));
        contract.addPermission(perm);
        contract.getStateData().set("test_value", "0");
        String js = "";
        js += "print('demo1');";
        js += "print('  create new revision...');";
        js += "rev = jsApi.getCurrentContract().createRevision();";
        js += "print('  new revision: ' + rev.getRevision());";
        js += "var oldValue = rev.getStateDataField('test_value');";
        js += "var newValue = oldValue=='0' ? '1' : '0';";
        js += "print('  change test_value: ' + oldValue + ' -> ' + newValue);";
        js += "rev.setStateDataField('test_value', newValue);";
        js += "result = rev";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        assertTrue(contract.check());

        ItemResult ir = testSpace.client.register(contract.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir.state);

        for (int i = 0; i < 10; ++i) {
            contract = ((JSApiContract) contract.execJS(js.getBytes())).extractContract(new JSApiAccessor());
            contract.addSignerKey(TestKeys.privateKey(1));
            contract.seal();
            assertEquals(i%2==0 ? "1" : "0", contract.getStateData().getStringOrThrow("test_value"));
            ir = testSpace.client.register(contract.getPackedTransaction(), 5000);
            assertEquals(ItemState.APPROVED, ir.state);
        }

        testSpace.nodes.forEach(m -> m.shutdown());
    }

    @Test
    public void jsDemo2() throws Exception {
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Contract contract = new Contract(TestKeys.privateKey(1));
        Binder permParams = new Binder();
        permParams.set("min_value", 1);
        permParams.set("min_step", 1);
        permParams.set("max_step", 1);
        permParams.set("field_name", "test_value");
        ChangeNumberPermission perm = new ChangeNumberPermission(contract.getOwner(), permParams);
        contract.addPermission(perm);
        contract.getStateData().set("test_value", 11);
        String js = "";
        js += "print('demo2');";
        js += "print('  create new revision...');";
        js += "rev = jsApi.getCurrentContract().createRevision();";
        js += "print('  new revision: ' + rev.getRevision());";
        js += "var oldValue = parseInt(rev.getStateDataField('test_value'));";
        js += "var newValue = (oldValue + 1) >> 0;"; // '>> 0' converts js-number to int
        js += "print('  change test_value: ' + oldValue + ' -> ' + newValue);";
        js += "rev.setStateDataField('test_value', newValue);";
        js += "result = rev";
        contract.getDefinition().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.seal();
        assertTrue(contract.check());

        ItemResult ir = testSpace.client.register(contract.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir.state);

        for (int i = 0; i < 10; ++i) {
            contract = ((JSApiContract) contract.execJS(js.getBytes())).extractContract(new JSApiAccessor());
            contract.addSignerKey(TestKeys.privateKey(1));
            contract.seal();
            assertEquals(i+12, contract.getStateData().getIntOrThrow("test_value"));
            ir = testSpace.client.register(contract.getPackedTransaction(), 5000);
            assertEquals(ItemState.APPROVED, ir.state);
        }

        testSpace.nodes.forEach(m -> m.shutdown());
    }

    @Test
    public void jsAddPermission() throws Exception {
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));

        KeyAddress k0 = TestKeys.publicKey(0).getShortAddress();
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().set("testval", 3);
        String js = "";
        js += "print('addPermission');";
        js += "var simpleRole = jsApi.getRoleBuilder().createSimpleRole('owner', '"+k0.toString()+"');";
        js += "var changeNumberPermission = jsApi.getPermissionBuilder().createChangeNumberPermission(simpleRole, " +
                "{field_name: 'testval', min_value: 3, max_value: 80, min_step: 1, max_step: 3}" +
                ");";
        js += "jsApi.getCurrentContract().addPermission(changeNumberPermission);";
        contract.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters());
        contract.execJS(js.getBytes());
        contract.seal();

        ItemResult ir = testSpace.client.register(contract.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir.state);

        Contract newRev = contract.createRevision();
        newRev.addSignerKey(TestKeys.privateKey(0));
        newRev.getStateData().set("testval", 5);
        newRev.seal();

        ir = testSpace.client.register(newRev.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir.state);

        testSpace.nodes.forEach(m -> m.shutdown());
    }

    @Test
    public void latestHttpContractFromSlot1() throws Exception {
        System.out.println("============= start nodes...");
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));
        System.out.println("============= start nodes done\n\n\n");

        Contract contractServer = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "var jsApiEvents = new Object();";
        js += "jsApiEvents.httpHandler_getVersion = function(request, response){" +
                "  response.setBodyAsJson({" +
                "    version: 1" +
                "  });" +
                "};";
        contractServer.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters(), true);
        RoleLink issuerLink = new RoleLink("issuer_link", "issuer");
        contractServer.registerRole(issuerLink);
        Permission perm = new ModifyDataPermission(issuerLink, Binder.of("fields", Binder.of("scripts", null)));
        contractServer.addPermission(perm);
        contractServer.seal();
        contractServer = Contract.fromPackedTransaction(contractServer.getPackedTransaction());

        ItemResult itemResult = testSpace.client.register(contractServer.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // put contractServer rev1 into slot1
        SlotContract slotContract = ContractsService.createSlotContract(new HashSet<>(Arrays.asList(TestKeys.privateKey(0))), new HashSet<>(Arrays.asList(TestKeys.publicKey(0))), nodeInfoProvider);
        slotContract.setNodeInfoProvider(nodeInfoProvider);
        slotContract.putTrackingContract(contractServer);
        Contract stepaU = InnerContractsService.createFreshU(100000000, new HashSet<>(Arrays.asList(TestKeys.publicKey(0))));
        itemResult = testSpace.client.register(stepaU.getPackedTransaction(), 5000);
        System.out.println("stepaU : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
        Parcel parcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), stepaU, 1, 100, new HashSet<>(Arrays.asList(TestKeys.privateKey(0))), false);
        testSpace.client.registerParcelWithState(parcel.pack(), 5000);
        itemResult = testSpace.client.getState(slotContract.getId());
        System.out.println("slot : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // start http server
        JSApiHttpServerRoutes routes = new JSApiHttpServerRoutes();
        routes.setPortToListen(8880);
        routes.addNewRoute("/contract1/getVersion", "httpHandler_getVersion", contractServer, "client script.js", null, slotContract.getId());
        JSApiHttpServer httpServer = new JSApiHttpServer(routes, new JSApiExecOptions(), hashId -> {
            try {
                return testSpace.client.getState(hashId).state == ItemState.APPROVED;
            } catch (ClientError e) {
                e.printStackTrace();
                return false;
            }
        },
        (slotId, originId) -> {
            try {
                Binder slotInfo = testSpace.client.querySlotInfo(slotId);
                return testSpace.client.queryContract(slotId, originId, null);
            } catch (ClientError e) {
                e.printStackTrace();
                return null;
            }
        });

        // here can be any http client. JSApiHttpClient used just for easiness
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.domainMasks.add("localhost:*");
        JSApiHttpClient httpClient = new JSApiHttpClient(scriptParameters);

        // http access to contractServer rev1
        List httpRes = httpClient.sendGetRequest("http://localhost:8880/contract1/getVersion", JSApiHttpClient.RESPTYPE_JSON);
        System.out.println("httpRes: " + httpRes);
        assertEquals(1l, ((HashMap)httpRes.get(1)).get("version"));

        // create and register contractServer rev2
        Contract contractServer2 = contractServer.createRevision();
        contractServer2.addSignerKey(TestKeys.privateKey(0));
        String js2 = "";
        js2 += "var jsApiEvents = new Object();";
        js2 += "jsApiEvents.httpHandler_getVersion = function(request, response){" +
                "  response.setBodyAsJson({" +
                "    version: 2" +
                "  });" +
                "};";
        contractServer2.getState().setJS(js2.getBytes(), "client script.js", new JSApiScriptParameters(), true);
        contractServer2.seal();
        contractServer2 = Contract.fromPackedTransaction(contractServer2.getPackedTransaction());
        ItemResult itemResult2 = testSpace.client.register(contractServer2.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult2.state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(contractServer.getId()).state);

        // force update server contracts from slot1
        httpServer.checkAllContracts();

        // http access to contractServer rev2
        httpRes = httpClient.sendGetRequest("http://localhost:8880/contract1/getVersion", JSApiHttpClient.RESPTYPE_JSON);
        System.out.println("httpRes: " + httpRes);
        assertEquals(2l, ((HashMap)httpRes.get(1)).get("version"));

        System.out.println("\n\n\n============= shutdown...");
        testSpace.nodes.forEach(m -> m.shutdown());

        httpServer.stop();
    }

    @Test
    public void initHttpRoutesFromSlot1() throws Exception {
        System.out.println("============= start nodes...");
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));
        System.out.println("============= start nodes done\n\n\n");

        Contract contractServer = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "var jsApiEvents = new Object();";
        js += "jsApiEvents.httpHandler_getVersion = function(request, response){" +
                "  response.setBodyAsJson({" +
                "    version: 1" +
                "  });" +
                "};";
        contractServer.getState().setJS(js.getBytes(), "client script.js", new JSApiScriptParameters(), true);
        RoleLink issuerLink = new RoleLink("issuer_link", "issuer");
        contractServer.registerRole(issuerLink);
        Permission perm = new ModifyDataPermission(issuerLink, Binder.of("fields", Binder.of("scripts", null)));
        contractServer.addPermission(perm);
        contractServer.seal();
        contractServer = Contract.fromPackedTransaction(contractServer.getPackedTransaction());

        ItemResult itemResult = testSpace.client.register(contractServer.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // put contractServer rev1 into slot1
        SlotContract slotContract = ContractsService.createSlotContract(new HashSet<>(Arrays.asList(TestKeys.privateKey(0))), new HashSet<>(Arrays.asList(TestKeys.publicKey(0))), nodeInfoProvider);
        slotContract.setNodeInfoProvider(nodeInfoProvider);
        slotContract.putTrackingContract(contractServer);
        Contract stepaU = InnerContractsService.createFreshU(100000000, new HashSet<>(Arrays.asList(TestKeys.publicKey(0))));
        itemResult = testSpace.client.register(stepaU.getPackedTransaction(), 5000);
        System.out.println("stepaU : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
        Parcel parcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), stepaU, 1, 100, new HashSet<>(Arrays.asList(TestKeys.privateKey(0))), false);
        testSpace.client.registerParcelWithState(parcel.pack(), 5000);
        itemResult = testSpace.client.getState(slotContract.getId());
        System.out.println("slot : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        JSApiHttpServer.ISlot1Requestor slot1Requestor = (slotId, originId) -> {
            try {
                Binder slotInfo = testSpace.client.querySlotInfo(slotId);
                return testSpace.client.queryContract(slotId, originId, null);
            } catch (ClientError e) {
                e.printStackTrace();
                return null;
            }
        };

        // start http server
        String routesJsonString =
                "{\n" +
                        "  \"listenPort\": \"8880\",\n" +
                        "  \"routes\": [\n" +
                        "    {\"endpoint\": \"/contract1/getVersion\", \"handlerName\": \"httpHandler_getVersion\", \"scriptName\": \"client script.js\", \"slotId\":\""+slotContract.getId().toBase64String()+"\", \"originId\":\""+contractServer.getOrigin().toBase64String()+"\"}\n" +
                        "  ]\n" +
                        "}\n";
        String tmpdir = System.getProperty("java.io.tmpdir");
        String strPathRoutes = tmpdir + "/" + "routes.json";
        Files.deleteIfExists(Paths.get(strPathRoutes));
        new File(strPathRoutes).createNewFile();
        Files.write(Paths.get(strPathRoutes), routesJsonString.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        JSApiHttpServerRoutes routes = new JSApiHttpServerRoutes(strPathRoutes, slot1Requestor);
        JSApiHttpServer httpServer = new JSApiHttpServer(routes, new JSApiExecOptions(), hashId -> {
                    try {
                        return testSpace.client.getState(hashId).state == ItemState.APPROVED;
                    } catch (ClientError e) {
                        e.printStackTrace();
                        return false;
                    }
                },
                slot1Requestor
        );

        // here can be any http client. JSApiHttpClient used just for easiness
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.domainMasks.add("localhost:*");
        JSApiHttpClient httpClient = new JSApiHttpClient(scriptParameters);

        // http access to contractServer rev1
        List httpRes = httpClient.sendGetRequest("http://localhost:8880/contract1/getVersion", JSApiHttpClient.RESPTYPE_JSON);
        System.out.println("httpRes: " + httpRes);
        assertEquals(1l, ((HashMap)httpRes.get(1)).get("version"));

        // create and register contractServer rev2
        Contract contractServer2 = contractServer.createRevision();
        contractServer2.addSignerKey(TestKeys.privateKey(0));
        String js2 = "";
        js2 += "var jsApiEvents = new Object();";
        js2 += "jsApiEvents.httpHandler_getVersion = function(request, response){" +
                "  response.setBodyAsJson({" +
                "    version: 2" +
                "  });" +
                "};";
        contractServer2.getState().setJS(js2.getBytes(), "client script.js", new JSApiScriptParameters(), true);
        contractServer2.seal();
        contractServer2 = Contract.fromPackedTransaction(contractServer2.getPackedTransaction());
        ItemResult itemResult2 = testSpace.client.register(contractServer2.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult2.state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(contractServer.getId()).state);

        // force update server contracts from slot1
        httpServer.checkAllContracts();

        // http access to contractServer rev2
        httpRes = httpClient.sendGetRequest("http://localhost:8880/contract1/getVersion", JSApiHttpClient.RESPTYPE_JSON);
        System.out.println("httpRes: " + httpRes);
        assertEquals(2l, ((HashMap)httpRes.get(1)).get("version"));

        System.out.println("\n\n\n============= shutdown...");
        testSpace.nodes.forEach(m -> m.shutdown());

        httpServer.stop();
    }

    @Ignore
    @Test
    public void testSpaceForUniclient() throws Exception {
        System.out.println("============= start nodes...");
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));
        System.out.println("============= start nodes done\n\n\n");

        String tmpdir = System.getProperty("java.io.tmpdir");
        String strPathRoutes = tmpdir + "/" + "routes.json";
        Files.deleteIfExists(Paths.get(strPathRoutes));
        new File(strPathRoutes).createNewFile();
        String strPathContract1 = tmpdir + "/" + "contract1.tp";
        Files.deleteIfExists(Paths.get(strPathContract1));
        new File(strPathContract1).createNewFile();
        String strPathContract2 = tmpdir + "/" + "contract2.tp";
        Files.deleteIfExists(Paths.get(strPathContract2));
        new File(strPathContract2).createNewFile();

        Contract contract1 = new Contract(TestKeys.privateKey(0));
        String js1 = "";
        js1 += "var jsApiEvents = new Object();";
        js1 += "jsApiEvents.httpHandler_endpoint1 = function(request, response){" +
                "  response.setBodyAsPlainText('endpoint1');" +
                "};";
        String js2 = "";
        js2 += "var jsApiEvents = new Object();";
        js2 += "jsApiEvents.httpHandler_endpoint2 = function(request, response){" +
                "  response.setBodyAsPlainText('endpoint2');" +
                "};";
        JSApiScriptParameters scriptParameters1 = new JSApiScriptParameters();
        scriptParameters1.timeLimitMillis = 3000;
        contract1.getState().setJS(js1.getBytes(), "script1.js", scriptParameters1, true);
        contract1.getState().setJS(js2.getBytes(), "script2.js", scriptParameters1, true);
        contract1.seal();
        contract1 = Contract.fromPackedTransaction(contract1.getPackedTransaction());

        Contract contract2 = new Contract(TestKeys.privateKey(0));
        String js3 = "";
        js3 += "var jsApiEvents = new Object();";
        js3 += "jsApiEvents.httpHandler_endpoint3 = function(request, response){" +
                "  response.setBodyAsPlainText(jsApiParams[0]+jsApiParams[1]+jsApiParams[2]);" +
                "};";
        JSApiScriptParameters scriptParameters2 = new JSApiScriptParameters();
        scriptParameters2.timeLimitMillis = 3000;
        contract2.getState().setJS(js3.getBytes(), "script3.js", scriptParameters2, true);
        contract2.seal();
        contract2 = Contract.fromPackedTransaction(contract2.getPackedTransaction());

        String routesJsonString =
                "{\n" +
                        "  \"listenPort\": \"8880\",\n" +
                        "  \"routes\": [\n" +
                        "    {\"endpoint\": \"/endpoint1\", \"handlerName\": \"httpHandler_endpoint1\", \"contractPath\": \""+strPathContract1+"\", \"scriptName\": \"script1.js\"},\n" +
                        "    {\"endpoint\": \"/endpoint2\", \"handlerName\": \"httpHandler_endpoint2\", \"contractPath\": \""+strPathContract1+"\", \"scriptName\": \"script2.js\"},\n" +
                        "    {\"endpoint\": \"/endpoint3\", \"handlerName\": \"httpHandler_endpoint3\", \"contractPath\": \""+strPathContract2+"\", \"scriptName\": \"script3.js\", \"jsApiParams\": [\"param1\", \"param2\", \"param3\"]}\n" +
                        "  ]\n" +
                        "}\n";

        Files.write(Paths.get(strPathRoutes), routesJsonString.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        Files.write(Paths.get(strPathContract1), contract1.getPackedTransaction(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        Files.write(Paths.get(strPathContract2), contract2.getPackedTransaction(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        testSpace.client.register(contract1.getPackedTransaction(), 5000);
        testSpace.client.register(contract2.getPackedTransaction(), 5000);

        System.out.println("sleep...");
        Thread.sleep(1000000000);

        System.out.println("\n\n\n============= shutdown...");
        testSpace.nodes.forEach(m -> m.shutdown());
    }

    @Test
    public void jsInStateAndDefinition() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.setOwnerKeys(TestKeys.publicKey(1), TestKeys.publicKey(2), TestKeys.publicKey(3));
        contract.setCreatorKeys(TestKeys.publicKey(4), TestKeys.publicKey(5).getLongAddress());
        String jsDefinitionA = "print('hello world from definition A'); result = 'dA';";
        String jsDefinitionB = "print('hello world from definition B'); result = 'dB';";
        String jsStateA = "print('hello world from state A'); result = 'sA';";
        String jsStateB = "print('hello world from state B'); result = 'sB';";
        contract.getDefinition().setJS(jsDefinitionA.getBytes(), "script1.js", new JSApiScriptParameters(), true);
        contract.getDefinition().setJS(jsDefinitionB.getBytes(), "script2.js", new JSApiScriptParameters(), true);
        contract.getState().setJS(jsStateA.getBytes(), "script3.js", new JSApiScriptParameters(), true);
        contract.getState().setJS(jsStateB.getBytes(), "script4.js", new JSApiScriptParameters(), true);
        contract.seal();
        String res1 = (String)contract.execJSByName("script1.js");
        String res2 = (String)contract.execJSByName("script2.js");
        String res3 = (String)contract.execJSByName("script3.js");
        String res4 = (String)contract.execJSByName("script4.js");
        assertEquals("dA", res1);
        assertEquals("dB", res2);
        assertEquals("sA", res3);
        assertEquals("sB", res4);
    }

    @Test
    public void loadHttpContractFromUrl() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        String js = "";
        js += "var jsApiEvents = new Object();";
        js += "jsApiEvents.httpHandler_index = function(request, response){" +
                "  response.setBodyAsPlainText('httpHandler_index_answer');" +
                "};";
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.timeLimitMillis = 3000;
        contract.getState().setJS(js.getBytes(), "script.js", scriptParameters, true);
        contract.seal();
        contract = Contract.fromPackedTransaction(contract.getPackedTransaction());
        byte[] bin = contract.getPackedTransaction();

        Contract someFileHost = new Contract(TestKeys.privateKey(1));
        String jsHost = "";
        jsHost += "var jsApiEvents = new Object();";
        jsHost += "jsApiEvents.httpHandler_getContract = function(request, response){" +
                  "  response.setBodyAsFileBinary(jsApi.base64toBin('"+Base64.encodeString(bin)+"'));" +
                  "};";
        JSApiScriptParameters scriptParametersHost = new JSApiScriptParameters();
        scriptParametersHost.timeLimitMillis = 3000;
        someFileHost.getState().setJS(jsHost.getBytes(), "fileHost.js", scriptParametersHost, true);
        someFileHost.seal();
        someFileHost = Contract.fromPackedTransaction(someFileHost.getPackedTransaction());

        String tmpdir = System.getProperty("java.io.tmpdir");
        String strPathContractHost = tmpdir + "/" + "contractHost.tp";
        Files.deleteIfExists(Paths.get(strPathContractHost));
        new File(strPathContractHost).createNewFile();
        Files.write(Paths.get(strPathContractHost), someFileHost.getPackedTransaction(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        String routesJsonStringHost =
                "{\n" +
                "  \"listenPort\": \"8882\",\n" +
                "  \"routes\": [\n" +
                "    {\"endpoint\": \"/fileHosting/getContract\", \"handlerName\": \"httpHandler_getContract\", \"contractPath\": \""+strPathContractHost+"\", \"scriptName\": \"fileHost.js\"}\n" +
                "  ]\n" +
                "}\n";
        JSApiHttpServerRoutes routesHost = new JSApiHttpServerRoutes(routesJsonStringHost.getBytes(), (slotId, originId) -> null);
        JSApiHttpServer httpServerFileHost = new JSApiHttpServer(routesHost, new JSApiExecOptions(), hashId -> true, (slotId, originId) -> null);

        String routesJsonString =
                "{\n" +
                        "  \"listenPort\": \"8880\",\n" +
                        "  \"routes\": [\n" +
                        "    {\"endpoint\": \"/contract1\", \"handlerName\": \"httpHandler_index\", \"contractPath\": \"http://localhost:8882/fileHosting/getContract\", \"scriptName\": \"script.js\"}\n" +
                        "  ]\n" +
                        "}\n";
        JSApiHttpServerRoutes routes = new JSApiHttpServerRoutes(routesJsonString.getBytes(), (slotId, originId) -> null);
        JSApiHttpServer httpServer = new JSApiHttpServer(routes, new JSApiExecOptions(), hashId -> true, (slotId, originId) -> null);

        // here can be any http client. JSApiHttpClient used just for easiness
        JSApiScriptParameters scriptParametersClient = new JSApiScriptParameters();
        scriptParametersClient.domainMasks.add("localhost:*");
        JSApiHttpClient httpClient = new JSApiHttpClient(scriptParametersClient);
        List httpRes = httpClient.sendGetRequest("http://localhost:8880/contract1", JSApiHttpClient.RESPTYPE_TEXT);
        System.out.println("httpRes: " + httpRes);
        assertEquals("httpHandler_index_answer", httpRes.get(1));

        httpServerFileHost.stop();
        httpServer.stop();
    }

}
