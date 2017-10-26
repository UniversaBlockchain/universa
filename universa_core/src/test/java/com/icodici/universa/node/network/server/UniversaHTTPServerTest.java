/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/17/17.
 *
 */

package com.icodici.universa.node.network.server;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.network.BasicHTTPService;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node.network.UniversaHTTPClient;
import com.icodici.universa.node.network.microhttpd.MicroHTTPDService;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.StopWatch;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;


class TestHTTPServer extends UniversaHTTPServer {
    @Override
    protected void addDefaultEndpoints() {
        super.addDefaultEndpoints();
        addUploadEndpoint();
        addGetEndpoint();
    }

    public TestHTTPServer(BasicHTTPService httpService, PrivateKey privateKey, int port, int threadLimit) throws IOException {
        super(httpService, privateKey, port, threadLimit);
    }

    public TestHTTPServer(BasicHTTPService httpService, PrivateKey privateKey, int port) throws IOException {
        super(httpService, privateKey, port);
    }

    // require 2 params: contract (Binder) and id (String)
    private void addUploadEndpoint() {
        this.addEndpoint("/uploadContract", (request, response) -> {
            try {
                Object contractObj = request.get("contract");

                if (contractObj == null || !(contractObj instanceof Contract))
                    return;

                Contract contract = (Contract) contractObj;

                Object id = request.get("id");

                final String fileName = String.format("%s/id_%s.unc", storage, id);

                File contractFileName = new File(fileName);

                if (!contractFileName.exists()) contractFileName.createNewFile();

                try (FileOutputStream fileOutputStream = new FileOutputStream(fileName)) {
                    fileOutputStream.write(contract.seal());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // require 1 param: id (String)
    private void addGetEndpoint() {
        this.addEndpoint("/getContract", (request, response) -> {
            Object id = request.get("id");

            Contract contract = null;

            Path path = Paths.get(String.format("%s/id_%s.unc", storage, id));

            try {
                byte[] data = Files.readAllBytes(path);

                contract = new Contract(data);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Binder binder = BossBiMapper.serialize(contract);

            response.set("contract", binder);
        });
    }

    public UniversaHTTPServer setStorage(String storage) {
        this.storage = storage;
        return this;
    }

    public static final String DEFAULT_STORAGE_TEST_CONTRACTS = "./src/test_contracts";
    private String storage = DEFAULT_STORAGE_TEST_CONTRACTS;

}

public class UniversaHTTPServerTest {

    private static final int DEFAULT_PORT = 17174;
    private static final String ROOT_URL = "http://localhost:" + DEFAULT_PORT;
    private static final int DEFAULT_WORKER_THREADS = 4;

    private TestHTTPServer universaHTTPServer;

    public void setMicroHTTPDUp() throws Exception {
        setMicroHTTPDUpK(TestKeys.privateKey(0));
    }

    public void setMicroHTTPDUp(PrivateKey key) throws Exception {
        setMicroHTTPDUpK(key);
    }


    public void setMicroHTTPDUpK(PrivateKey key) throws Exception {
        universaHTTPServer = new TestHTTPServer(new MicroHTTPDService(), key,
                DEFAULT_PORT, DEFAULT_WORKER_THREADS);

        universaHTTPServer.setStorage("./src/test_contracts");

        universaHTTPServer.setRequestPreprocessor(((request) -> {
            Object requestData = request.get("requestData");

            if (!(requestData instanceof BasicHTTPService.FileUpload))
                return new Binder(Errors.FAILURE.name(),
                        new ErrorRecord(Errors.FAILURE, "", "requestData is wrong"));


            byte[] data = ((BasicHTTPService.FileUpload) request.get("requestData")).getBytes();

            return Boss.unpack(data);
        }));
    }


    @After
    public void tearDown() throws Exception {
        universaHTTPServer.shutdown();
    }

    @Test
    public void shouldDeliverContract() throws Exception {
        setMicroHTTPDUp();
        universaHTTPServer.start();

        Contract contractToSend = Contract.fromYamlFile("./src/test_contracts/id_1.yml");

        Binder binder = BossBiMapper.serialize(contractToSend);

        UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);
        PublicKey nodeKey = TestKeys.publicKey(0);
        UniversaHTTPClient.Answer a = client.request("uploadContract",
                "contract", binder,
                "id", "3",
                "client_key", nodeKey.pack());

        assertEquals(a.code, 200);
    }

    @Test
    public void shouldGetContract() throws Exception {
        setMicroHTTPDUp();
        universaHTTPServer.start();


        UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);

        PublicKey nodeKey = TestKeys.publicKey(0);

        UniversaHTTPClient.Answer a = client.request("getContract", "id", "1",
                "client_key", nodeKey.pack());

        assertEquals(a.code, 200);

        Object contract = a.data.get("contract");
        assertNotNull(contract);
        assertTrue(contract instanceof Contract);
    }

    @Test
    public void shouldUploadAndGetContractThen() throws Exception {
        setMicroHTTPDUp();
        universaHTTPServer.start();


        Contract contractToSend = Contract.fromYamlFile("./src/test_contracts/id_1.yml");

        Binder binder = BossBiMapper.serialize(contractToSend);

        UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);

        PublicKey nodeKey = TestKeys.publicKey(0);

        UniversaHTTPClient.Answer upload = client.request("uploadContract", "contract", binder, "id", "2",
                "client_key", nodeKey.pack());

        assertEquals(upload.code, 200);

        UniversaHTTPClient.Answer get = client.request("getContract", "id", "2",
                "client_key", nodeKey.pack());

        assertEquals(get.code, 200);
        Object contract = get.data.get("contract");
        assertNotNull(contract);
        assertTrue(contract instanceof Contract);
    }

    @Test
    public void shouldRunServerWithTestEndpoint() throws Exception {
        setMicroHTTPDUp();
        universaHTTPServer.start();

        universaHTTPServer.addEndpoint("/test", (request, response) -> {
            try {
                Object obj = request.get("hello");

                assertNotNull(obj);
                assertTrue(obj instanceof String);
                assertEquals("world", obj);

                response.put("ping", obj);
            } catch (Exception e) {
                fail("No exception expected." + e.getMessage());
            }
        });

        UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);

        PublicKey nodeKey = TestKeys.publicKey(0);
        UniversaHTTPClient.Answer a = client.request("test", "hello", "world",
                "client_key", nodeKey.pack());

        assertEquals(a.code, 200);
        assertEquals("world", a.data.getStringOrThrow("ping"));
    }

    @Test
    public void shouldRunServerWithSeveralEndpoints() throws Exception {
        setMicroHTTPDUp();
        universaHTTPServer.start();

        universaHTTPServer
                .addEndpoint("/getNumber", (request, response) -> {
                    response.put("number", 100500);
                })
                .addEndpoint("/getProjectName", (request, response) -> {
                    response.put("name", "Universa");
                })
                .addEndpoint("/putSiteName", (request, response) -> {
                    Object name = request.get("name");
                    assertEquals("Universa.io", name);
                });


        UniversaHTTPClient client = new UniversaHTTPClient("node1", ROOT_URL);

        PublicKey nodeKey = TestKeys.publicKey(0);

        UniversaHTTPClient.Answer a = client.request("getNumber",
                "client_key", nodeKey.pack());

        assertEquals(a.code, 200);
        assertEquals(100500, a.data.getIntOrThrow("number"));

        a = client.request("getProjectName",
                "client_key", nodeKey.pack());

        assertEquals(a.code, 200);
        assertEquals("Universa", a.data.getStringOrThrow("name"));

        a = client.request("putSiteName", "name", "Universa.io",
                "client_key", nodeKey.pack());

        assertEquals(a.code, 200);
    }

    UniversaHTTPClient client;

    @Test
    public void handshake() throws Exception {
        setMicroHTTPDUp();
        StopWatch.measure(true, () -> {
            universaHTTPServer.start();
            client = new UniversaHTTPClient("testnode1", ROOT_URL);
            PublicKey nodeKey = TestKeys.publicKey(0);
            PrivateKey clientKey = TestKeys.privateKey(1);
            client.start(clientKey, nodeKey);
        });
        StopWatch.measure(true, () -> {
            assertTrue(client.ping());
            assertEquals("welcome to the Universa", client.command("hello").getStringOrThrow("message"));
        });
    }


    @Test
    public void handshakeWithChangingKeys() throws Exception {
        setMicroHTTPDUp();
        universaHTTPServer.start();

        UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);
        PublicKey nodeKey = TestKeys.publicKey(0);
        PrivateKey clientKey = TestKeys.privateKey(1);
        client.start(clientKey, nodeKey);
        assertTrue(client.ping());

        universaHTTPServer.changeKeyFor(clientKey.getPublicKey());
        assertTrue(client.ping());

        try {
            client.command("test_error");
            fail("expected exception wasn't thrown");
        } catch (UniversaHTTPClient.CommandFailedException e) {
            assertEquals(Errors.COMMAND_FAILED, e.getError().getError());
            assertEquals("test_error", e.getError().getObjectName());
            assertEquals("sample error", e.getError().getMessage());
        }
    }

    @Test
    public void handshakeWithoutKeysShouldFail() throws Exception {
        setMicroHTTPDUp(null);
        universaHTTPServer.start();

        UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);
        PublicKey nodeKey = TestKeys.publicKey(0);
        PrivateKey clientKey = TestKeys.privateKey(1);
        try {
            client.start(clientKey, nodeKey);
            fail("expected exception wasn't thrown");
        } catch (UniversaHTTPClient.EndpointException e) {
            assertTrue(e.getErrors().size() == 1);
            assertEquals(Errors.BAD_VALUE, e.getErrors().get(0).getError());
            assertEquals("wrong or tampered data block:null", e.getErrors().get(0).getMessage());
        }
    }

}