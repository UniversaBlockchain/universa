/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.network.microhttpd;

// todo: rewrite without using of obsolete and removed clases

// Note: the actual funcinality of this test is covered in BasicHttpServerTest, so it could be
// very well discarded.

//
//@Ignore("is covered by other tests. slow. call it explicitly of need")
//public class MicroHTTPDServiceTest {
//    private static final int DEFAULT_PORT = 17174;
//    private static final String ROOT_URL = "http://localhost:" + DEFAULT_PORT;
//    private static final int DEFAULT_WORKER_THREADS = 4;
//
//    private MicroHTTPDService service;
//
//    @Before
//    public void setUp() throws Exception {
//        service = new MicroHTTPDService();
//        service.start(DEFAULT_PORT, DEFAULT_WORKER_THREADS);
//    }
//
//    @After
//    public void tearDown() throws Exception {
//        service.close();
//        service = null;
//    }
//
//    /**
//     * Test that the {@link MicroHTTPDService} basically works;
//     * multiple {@link com.icodici.universa.node.network.BasicHTTPService::on} handlers are routed properly;
//     * and if a missing route is called, error is generated.
//     */
//    @Test
//    public void basicTest() throws IOException {
//        service.on("/ping1", (request, response) -> {
//            try {
//                final MicroHTTPDService.MicroHTTPDServiceResponse microResponse = (MicroHTTPDService.MicroHTTPDServiceResponse) response;
//                @Nullable String error = microResponse.getError();
//                assertNull(error);
//                final Binder result = Binder.fromKeysValues("ping", "pong1");
//                response.setBody(Boss.pack(result));
//            } catch (AssertionError e) {
//                e.printStackTrace();
//            }
//        });
//        service.on("/ping2", (request, response) -> {
//            try {
//                final Binder result = Binder.fromKeysValues("ping", "pong2");
//                response.setBody(Boss.pack(result));
//                Binder headers = response.getHeaders();
//                headers.put("X-Foo-Bar", "Bar Foo X");
//            } catch (AssertionError e) {
//                e.printStackTrace();
//            }
//        });
//        service.on("", (request, response) -> {
//            // Default handler
//            try {
//                final MicroHTTPDService.MicroHTTPDServiceResponse microResponse = (MicroHTTPDService.MicroHTTPDServiceResponse) response;
//                @Nullable String error = microResponse.getError();
//                assertNull(error);
//
//                final Binder result = Binder.fromKeysValues("errors", Arrays.asList("Unsupported URL: " + request.getPath()));
//                response.setBody(Boss.pack(result));
//            } catch (AssertionError e) {
//                e.printStackTrace();
//            }
//        });
//        service.on("asdf", (request, response) -> {
//            // Default handler
//            try {
//                final MicroHTTPDService.MicroHTTPDServiceResponse microResponse = (MicroHTTPDService.MicroHTTPDServiceResponse) response;
//                @Nullable String error = microResponse.getError();
//                assertNull(error);
//
//                final Binder result = Binder.fromKeysValues("errors", Arrays.asList("Unsupported URL: " + request.getPath()));
//                response.setBody(Boss.pack(result));
//            } catch (AssertionError e) {
//                e.printStackTrace();
//            }
//        });
//
//        {
//            UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);
//            UniversaHTTPClient.Answer a = client.request("ping1", "hello", "world");
//
//            assertEquals(a.code, 200);
//            assertEquals("pong1", a.data.getStringOrThrow("ping"));
//        }
//        {
//            UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);
//            UniversaHTTPClient.Answer a = client.request("ping2", "hello", "world");
//
//            assertEquals(a.code, 200);
//            assertEquals("pong2", a.data.getStringOrThrow("ping"));
//        }
//        {
//            UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);
//            UniversaHTTPClient.Answer a = client.request("ping", "hello", "world");
//
//            assertEquals(a.code, 200);
//            assertTrue(a.data.getListOrThrow("errors").contains("Unsupported URL: /ping"));
//        }
//    }
//
//    @Test
//    public void test404() throws IOException {
//        final ArrayList<String> requestLog = new ArrayList<>();
//        final ArrayList<Integer> responseLog = new ArrayList<>();
//
//        final BasicHTTPService.Handler
//                handler1 = (request, response) -> responseLog.add(1),
//                handlerE = (request, response) -> responseLog.add(0);
//
//        final Consumer<String> connect = path -> {
//            URLConnection connection = null;
//            int responseCode = 0;
//            try {
//                connection = new URL(ROOT_URL + path).openConnection();
//
//                final HttpURLConnection httpConnection = (HttpURLConnection) connection;
//                responseCode = httpConnection.getResponseCode();
//                httpConnection.getInputStream();
//                requestLog.add("Connect to " + path);
//            } catch (IOException e) {
//                requestLog.add(String.format("Connect to %s failed with %s", path, responseCode));
//            }
//        };
//
//        // Call an url when no handlers are added
//        {
//            requestLog.clear();
//            responseLog.clear();
//
//            connect.accept("/ping1");
//
//            assertArrayEquals(
//                    new String[]{"Connect to /ping1 failed with 404"},
//                    requestLog.toArray());
//            assertArrayEquals(
//                    new Integer[]{},
//                    responseLog.toArray());
//        }
//
//        // Then, call an url when some handler is added, but no 404 handler yet
//        {
//            requestLog.clear();
//            responseLog.clear();
//
//            assertNull(service.on("/ping1", handler1));
//
//            connect.accept("/ping1");
//            connect.accept("/ping2");
//
//            assertArrayEquals(
//                    new String[]{"Connect to /ping1", "Connect to /ping2 failed with 404"},
//                    requestLog.toArray());
//            assertArrayEquals(
//                    new Integer[]{1},
//                    responseLog.toArray());
//        }
//
//        // Then, call an url when both some handler is added and a specific 404 handler
//        {
//            requestLog.clear();
//            responseLog.clear();
//
//            assertNull(service.onNotFound(handlerE));
//
//            connect.accept("/ping1");
//            connect.accept("/ping2");
//
//            assertArrayEquals(
//                    new String[]{"Connect to /ping1", "Connect to /ping2"},
//                    requestLog.toArray());
//            assertArrayEquals(
//                    new Integer[]{1, 0},
//                    responseLog.toArray());
//        }
//    }
//
//    @Test
//    public void testOverwritingOnHandlers() throws IOException {
//        final ArrayList<Integer> log = new ArrayList<>();
//
//        final BasicHTTPService.Handler
//                handler1 = (request, response) -> log.add(1),
//                handler2 = (request, response) -> log.add(2),
//                handler3 = (request, response) -> log.add(3),
//                handlerE = (request, response) -> log.add(0);
//        assertNull(service.on("/ping1", handler3));
//        assertEquals(handler3, service.on("/ping1", handler2));
//        assertEquals(handler2, service.on("/ping1", handler1));
//
//        assertNull(service.on("/ping2", handler2));
//        assertEquals(handler2, service.on("/ping2", handler2));
//        assertEquals(handler2, service.on("/ping2", handler2));
//
//        assertNull(service.on("/ping3", handler1));
//        assertEquals(handler1, service.on("/ping3", handler2));
//        assertEquals(handler2, service.on("/ping3", handler3));
//
//        assertNull(service.onNotFound(handler3));
//        assertEquals(handler3, service.onNotFound(handler2));
//        assertEquals(handler2, service.onNotFound(handler1));
//        assertEquals(handler1, service.onNotFound(handlerE));
//
//        // Now call them all.
//
//        final Consumer<String> connect = path -> {
//            URLConnection connection = null;
//            try {
//                connection = new URL(ROOT_URL + path).openConnection();
//
//                final HttpURLConnection httpConnection = (HttpURLConnection) connection;
//                httpConnection.getInputStream();
//            } catch (IOException e) {
//                System.out.printf("Error: %s\n", e);
//            }
//        };
//
//        connect.accept("/ping1");
//        connect.accept("/ping1");
//        connect.accept("/ping2");
//        connect.accept("/ping2");
//        connect.accept("/ping3");
//        connect.accept("/ping3");
//        connect.accept("/pingBad");
//        connect.accept("/pingBad");
//
//        assertArrayEquals(new Integer[]{1, 1, 2, 2, 3, 3, 0, 0}, log.toArray());
//    }
//
//    /**
//     * Test the {@link com.icodici.universa.node.network.BasicHTTPService.Request} interface.
//     */
//    @Test
//    public void testRequest() throws IOException {
//        // Regular parameters
//        service.on("/ping1", (request, response) -> {
//            try {
//                final MicroHTTPDService.MicroHTTPDServiceResponse microResponse = (MicroHTTPDService.MicroHTTPDServiceResponse) response;
//                @Nullable String error = microResponse.getError();
//
//                assertEquals("/ping1", request.getPath());
//                assertEquals("localhost", request.getDomain());
//                assertTrue(request.getHeaders().getString("content-type").startsWith("multipart/form-data"));
//                assertEquals("POST", request.getMethod());
//
//                final Binder params = request.getParams();
//
//                // Test FileUpload
//                final BasicHTTPService.FileUpload requestDataUpload = (BasicHTTPService.FileUpload) params.get("requestData");
//
//                assertEquals("requestData.boss", requestDataUpload.getFileName());
//                assertEquals(Binder.fromKeysValues("hello", "world"), Boss.unpack(requestDataUpload.getBytes()));
//
//                final Binder result = Binder.fromKeysValues("ping", "pong1");
//                response.setBody(Boss.pack(result));
//            } catch (AssertionError e) {
//                e.printStackTrace();
//            }
//        });
//
//        // Test good connection
//        {
//            UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);
//            UniversaHTTPClient.Answer a = client.request("ping1", "hello", "world");
//
//            assertEquals(a.code, 200);
//            assertEquals("pong1", a.data.getStringOrThrow("ping"));
//        }
//    }
//
//    /**
//     * Test basic params merging inside of {@link com.icodici.universa.node.network.BasicHTTPService.Request} interface.
//     */
//    @Test
//    public void testRequestWithParamsMergingBasic() throws IOException {
//        // Regular parameters
//        service.on("/ping1", (request, response) -> {
//            try {
//                final MicroHTTPDService.MicroHTTPDServiceResponse microResponse = (MicroHTTPDService.MicroHTTPDServiceResponse) response;
//                @Nullable String error = microResponse.getError();
//
//                assertNull(error);
//
//                assertEquals("/ping1", request.getPath());
//                assertEquals("localhost", request.getDomain());
//                assertTrue(request.getHeaders().getString("content-type").startsWith("multipart/form-data"));
//                assertEquals("POST", request.getMethod());
//
//                final Binder params = request.getParams();
//
//                assertEquals(
//                        new HashSet<>(Arrays.asList("url1", "url2")),
//                        new HashSet<String>((List) params.get("a")));
//
//                assertEquals(
//                        "url1",
//                        params.get("b"));
//
//                assertEquals(
//                        "cUrl1",
//                        params.get("cUrl"));
//
//                // Test FileUpload
//                final BasicHTTPService.FileUpload requestDataUpload = (BasicHTTPService.FileUpload) params.get("requestData");
//
//                assertEquals("requestData.boss", requestDataUpload.getFileName());
//                assertEquals(Binder.fromKeysValues("hello", "world"), Boss.unpack(requestDataUpload.getBytes()));
//
//                final Binder result = Binder.fromKeysValues("ping", "pong1");
//                response.setBody(Boss.pack(result));
//            } catch (AssertionError e) {
//                e.printStackTrace();
//            }
//        });
//        {
//            UniversaHTTPClient client = new UniversaHTTPClient("testnode1", ROOT_URL);
//            UniversaHTTPClient.Answer a = client.request("ping1?a=url1&a=url2&b=url1&cUrl=cUrl1", "hello", "world");
//
//            assertEquals(a.code, 200);
//            assertEquals("pong1", a.data.getStringOrThrow("ping"));
//        }
//    }
//
//    /**
//     * Test complex params merging inside of {@link com.icodici.universa.node.network.BasicHTTPService.Request} interface.
//     */
//    @Test
//    public void testRequestWithParamsMergingComplex() throws IOException {
//        service.on("/ping2", (request, response) -> {
//            try {
//                final Binder params = request.getParams();
//
//                // getParams() contains the parameters both from query arguments (url1, url2)
//                // and from form (1, 2)
//                assertEquals(
//                        new HashSet<>(Arrays.asList("url1", "url2", "1", "2")),
//                        new HashSet<String>((List) params.get("a")));
//                assertEquals(
//                        new HashSet<>(Arrays.asList("url1", "1")),
//                        new HashSet<String>((List) params.get("b")));
//                // Something only in query arguments
//                assertEquals(
//                        "cUrl1",
//                        params.get("cUrl"));
//                // Something only in form
//                assertEquals(
//                        "c1",
//                        params.get("c"));
//
//                // Test FileUpload-s
//                final BasicHTTPService.FileUpload
//                        requestDataUpload1 = (BasicHTTPService.FileUpload) params.get("requestData1"),
//                        requestDataUpload21 = (BasicHTTPService.FileUpload) params.get("requestData21"),
//                        requestDataUpload22 = (BasicHTTPService.FileUpload) params.get("requestData22");
//
//                assertEquals("rData1", requestDataUpload1.getFileName());
//                assertArrayEquals("Hello world 1".getBytes(), requestDataUpload1.getBytes());
//
//                assertEquals("rData2", requestDataUpload21.getFileName());
//                assertArrayEquals("Hello world 2a".getBytes(), requestDataUpload21.getBytes());
//
//                assertEquals("rData2", requestDataUpload22.getFileName());
//                assertArrayEquals("Hello world 2b".getBytes(), requestDataUpload22.getBytes());
//
//                final Binder result = Binder.fromKeysValues("ping", "pong1");
//                response.setBody(Boss.pack(result));
//            } catch (AssertionError e) {
//                e.printStackTrace();
//            }
//        });
//
//        {
//            final String path = "/ping2?a=url1&a=url2&b=url1&cUrl=cUrl1";
//
//            // For this test, we create a HTTP request fully manually.
//
//            final String charset = "UTF-8";
//            final String boundary = "==boundary==" + Ut.randomString(48);
//            final String CRLF = "\r\n"; // Line separator required by multipart/form-data.
//            final URLConnection connection = new URL(ROOT_URL + path).openConnection();
//            connection.setDoOutput(true);
//
//            connection.setConnectTimeout(2000);
//            connection.setReadTimeout(5000);
//            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
//
//            try (
//                    final OutputStream output = connection.getOutputStream();
//                    final PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
//            ) {
//                // Send normal param.
//
//                // Send binary file.
//                writer.append("--" + boundary).append(CRLF);
//                writer.append("Content-Disposition: form-data; name=\"requestData1\"; filename=\"rData1\"").append(CRLF);
//                writer.append("Content-Type: application/octet-stream").append(CRLF);
//                writer.append("Content-Transfer-Encoding: binary").append(CRLF);
//                writer.append(CRLF).flush();
//                output.write("Hello world 1".getBytes());
//                output.flush(); // Important before continuing with writer!
//                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
//
//                // Send another binary file
//                writer.append("--" + boundary).append(CRLF);
//                writer.append("Content-Disposition: form-data; name=\"requestData2\"; filename=\"rData2\"").append(CRLF);
//                writer.append("Content-Type: application/octet-stream").append(CRLF);
//                writer.append("Content-Transfer-Encoding: binary").append(CRLF);
//                writer.append(CRLF).flush();
//                output.write("Hello world 2a".getBytes());
//                output.flush(); // Important before continuing with writer!
//                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
//
//                // Send another binary file
//                writer.append("--" + boundary).append(CRLF);
//                writer.append("Content-Disposition: form-data; name=\"requestData2\"; filename=\"rData2\"").append(CRLF);
//                writer.append("Content-Type: application/octet-stream").append(CRLF);
//                writer.append("Content-Transfer-Encoding: binary").append(CRLF);
//                writer.append(CRLF).flush();
//                output.write("Hello world 2b".getBytes());
//                output.flush(); // Important before continuing with writer!
//                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
//
//                writer.append("--" + boundary).append(CRLF);
//                writer.append("Content-Disposition: form-data; name=\"a\"").append(CRLF);
//                writer.append(CRLF).flush();
//                writer.append("1");
//                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
//
//                writer.append("--" + boundary).append(CRLF);
//                writer.append("Content-Disposition: form-data; name=\"a\"").append(CRLF);
//                writer.append(CRLF).flush();
//                writer.append("2");
//                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
//
//                writer.append("--" + boundary).append(CRLF);
//                writer.append("Content-Disposition: form-data; name=\"b\"").append(CRLF);
//                writer.append(CRLF).flush();
//                writer.append("1");
//                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
//
//                writer.append("--" + boundary).append(CRLF);
//                writer.append("Content-Disposition: form-data; name=\"c\"").append(CRLF);
//                writer.append(CRLF).flush();
//                writer.append("c1");
//                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
//
//                // End of multipart/form-data.
//                writer.append("--" + boundary + "--").append(CRLF).flush();
//            }
//
//            final HttpURLConnection httpConnection = (HttpURLConnection) connection;
//            int responseCode = httpConnection.getResponseCode();
//            byte[] answer = Do.read(httpConnection.getInputStream());
//            assertEquals(200, responseCode);
//        }
//    }
//}
