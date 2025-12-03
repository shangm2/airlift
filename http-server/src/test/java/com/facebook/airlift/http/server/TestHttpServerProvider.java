/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.airlift.http.server;

import com.facebook.airlift.event.client.NullEventClient;
import com.facebook.airlift.http.client.HttpClient;
import com.facebook.airlift.http.client.HttpClient.HttpResponseFuture;
import com.facebook.airlift.http.client.HttpClientConfig;
import com.facebook.airlift.http.client.Request;
import com.facebook.airlift.http.client.StatusResponseHandler.StatusResponse;
import com.facebook.airlift.http.client.StringResponseHandler.StringResponse;
import com.facebook.airlift.http.client.jetty.JettyHttpClient;
import com.facebook.airlift.log.Logging;
import com.facebook.airlift.node.NodeConfig;
import com.facebook.airlift.node.NodeInfo;
import com.facebook.airlift.tracetoken.TraceTokenManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.facebook.airlift.http.client.Request.Builder.prepareGet;
import static com.facebook.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.facebook.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static com.facebook.airlift.http.server.TestHttpServerInfo.closeChannels;
import static com.facebook.airlift.testing.Assertions.assertContains;
import static com.facebook.airlift.testing.Assertions.assertInstanceOf;
import static com.facebook.airlift.testing.Assertions.assertNotEquals;
import static com.google.common.io.Files.asCharSink;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.google.common.io.Resources.getResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(singleThreaded = true)
public class TestHttpServerProvider
{
    private HttpServer server;
    private File tempDir;
    private NodeInfo nodeInfo;
    private HttpServerConfig config;
    private HttpServerInfo httpServerInfo;

    @DataProvider
    public static Object[][] serverAdminMode()
    {
        return new Object[][] {{false}, {true}};
    }

    private static HttpServlet createCertTestServlet()
    {
        return new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response)
                    throws IOException
            {
                X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
                if ((certs == null) || (certs.length == 0)) {
                    throw new RuntimeException("No client certificate");
                }
                if (certs.length > 1) {
                    throw new RuntimeException("Received multiple client certificates");
                }
                X509Certificate cert = certs[0];
                response.getWriter().write(cert.getSubjectX500Principal().getName());
                response.setStatus(HttpServletResponse.SC_OK);
            }
        };
    }

    @BeforeSuite
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = createTempDirectory("test-http-server-provider").toFile().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
        config = new HttpServerConfig()
                .setHttpPort(0)
                .setHttpsPort(0)
                .setLogPath(tempDir.toPath().resolve("http-request.log").toAbsolutePath().toString());
        nodeInfo = new NodeInfo(new NodeConfig()
                .setEnvironment("test")
                .setNodeInternalAddress("localhost"));
        httpServerInfo = new HttpServerInfo(config, nodeInfo);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        closeChannels(httpServerInfo);
        try {
            if (server != null) {
                server.stop();
            }
        }
        finally {
            deleteRecursively(tempDir.toPath(), ALLOW_INSECURE);
        }
    }

    @Test
    public void testConnectorDefaults()
    {
        assertTrue(config.isHttpEnabled());
        assertNotNull(httpServerInfo.getHttpUri());
        assertNotNull(httpServerInfo.getHttpExternalUri());
        assertNotNull(httpServerInfo.getHttpChannel());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), httpServerInfo.getHttpExternalUri().getScheme());
        assertEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getHttpExternalUri().getPort());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), "http");

        assertFalse(config.isHttpsEnabled());
        assertNull(httpServerInfo.getHttpsUri());
        assertNull(httpServerInfo.getHttpsExternalUri());
        assertNull(httpServerInfo.getHttpsChannel());

        assertTrue(config.isAdminEnabled());
        assertNotNull(httpServerInfo.getAdminUri());
        assertNotNull(httpServerInfo.getAdminExternalUri());
        assertNotNull(httpServerInfo.getAdminChannel());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), httpServerInfo.getAdminExternalUri().getScheme());
        assertEquals(httpServerInfo.getAdminUri().getPort(), httpServerInfo.getAdminExternalUri().getPort());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), "http");

        assertNotEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getAdminUri().getPort());
    }

    @Test
    public void testHttpDisabled()
            throws Exception
    {
        closeChannels(httpServerInfo);

        config.setHttpEnabled(false);
        httpServerInfo = new HttpServerInfo(config, nodeInfo);

        assertNull(httpServerInfo.getHttpUri());
        assertNull(httpServerInfo.getHttpExternalUri());
        assertNull(httpServerInfo.getHttpChannel());

        assertNull(httpServerInfo.getHttpsUri());
        assertNull(httpServerInfo.getHttpsExternalUri());
        assertNull(httpServerInfo.getHttpsChannel());

        assertNotNull(httpServerInfo.getAdminUri());
        assertNotNull(httpServerInfo.getAdminExternalUri());
        assertNotNull(httpServerInfo.getAdminChannel());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), httpServerInfo.getAdminExternalUri().getScheme());
        assertEquals(httpServerInfo.getAdminUri().getPort(), httpServerInfo.getAdminExternalUri().getPort());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), "http");
    }

    @Test
    public void testAdminDisabled()
            throws Exception
    {
        closeChannels(httpServerInfo);

        config.setAdminEnabled(false);
        httpServerInfo = new HttpServerInfo(config, nodeInfo);

        assertNotNull(httpServerInfo.getHttpUri());
        assertNotNull(httpServerInfo.getHttpExternalUri());
        assertNotNull(httpServerInfo.getHttpChannel());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), httpServerInfo.getHttpExternalUri().getScheme());
        assertEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getHttpExternalUri().getPort());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), "http");

        assertNull(httpServerInfo.getHttpsUri());
        assertNull(httpServerInfo.getHttpsExternalUri());
        assertNull(httpServerInfo.getHttpsChannel());

        assertNull(httpServerInfo.getAdminUri());
        assertNull(httpServerInfo.getAdminExternalUri());
        assertNull(httpServerInfo.getAdminChannel());
    }

    @Test
    public void testHttpsEnabled()
            throws Exception
    {
        closeChannels(httpServerInfo);

        config.setHttpsEnabled(true);
        httpServerInfo = new HttpServerInfo(config, nodeInfo);

        assertNotNull(httpServerInfo.getHttpUri());
        assertNotNull(httpServerInfo.getHttpExternalUri());
        assertNotNull(httpServerInfo.getHttpChannel());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), httpServerInfo.getHttpExternalUri().getScheme());
        assertEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getHttpExternalUri().getPort());
        assertEquals(httpServerInfo.getHttpUri().getScheme(), "http");

        assertNotNull(httpServerInfo.getHttpsUri());
        assertNotNull(httpServerInfo.getHttpsExternalUri());
        assertNotNull(httpServerInfo.getHttpsChannel());
        assertEquals(httpServerInfo.getHttpsUri().getScheme(), httpServerInfo.getHttpsExternalUri().getScheme());
        assertEquals(httpServerInfo.getHttpsUri().getPort(), httpServerInfo.getHttpsExternalUri().getPort());
        assertEquals(httpServerInfo.getHttpsUri().getScheme(), "https");

        assertNotNull(httpServerInfo.getAdminUri());
        assertNotNull(httpServerInfo.getAdminExternalUri());
        assertNotNull(httpServerInfo.getAdminChannel());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), httpServerInfo.getAdminExternalUri().getScheme());
        assertEquals(httpServerInfo.getAdminUri().getPort(), httpServerInfo.getAdminExternalUri().getPort());
        assertEquals(httpServerInfo.getAdminUri().getScheme(), "https");

        assertNotEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getHttpsUri().getPort());
        assertNotEquals(httpServerInfo.getHttpUri().getPort(), httpServerInfo.getAdminUri().getPort());
    }

    @Test
    public void testHttp()
            throws Exception
    {
        createServer();
        server.start();

        try (JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig().setHttp2Enabled(false))) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getHeader("X-Protocol"), "HTTP/1.1");
        }

        try (JettyHttpClient httpClient = new JettyHttpClient(new HttpClientConfig().setHttp2Enabled(true))) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getHeader("X-Protocol"), "HTTP/2.0");
        }
    }

    @Test
    public void testHttps()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("test.keystore.with.two.passwords").getPath())
                .setKeystorePassword("airlift")
                .setKeyManagerPassword("airliftkey");

        createAndStartServer();

        HttpClientConfig http1ClientConfig = new HttpClientConfig()
                .setHttp2Enabled(false)
                .setTrustStorePath(getResource("test.truststore").getPath())
                .setTrustStorePassword("airlift");

        try (JettyHttpClient httpClient = new JettyHttpClient(http1ClientConfig)) {
            StatusResponse response = httpClient.execute(prepareGet().setUri(httpServerInfo.getHttpsUri()).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getHeader("X-Protocol"), "HTTP/1.1");
        }
    }

    @Test
    public void testFilter()
            throws Exception
    {
        createServer();
        server.start();

        try (JettyHttpClient client = new JettyHttpClient()) {
            StatusResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri().resolve("/filter")).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_PAYMENT_REQUIRED);
        }
    }

    @Test
    public void testAuth()
            throws Exception
    {
        File file = File.createTempFile("auth", ".properties", tempDir);
        asCharSink(file, UTF_8).write("user: password");

        config.setUserAuthFile(file.getAbsolutePath());

        createServer();
        server.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(
                    prepareGet()
                            .setUri(httpServerInfo.getHttpUri())
                            .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("user:password".getBytes()).trim())
                            .build(),
                    createStringResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getBody(), "user");
        }
    }

    @Test
    public void testClientCertificateJava()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setAdminEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("clientcert-java/server.keystore").getPath())
                .setKeystorePassword("airlift");

        createAndStartServer(createCertTestServlet());

        HttpClientConfig clientConfig = new HttpClientConfig()
                .setKeyStorePath(getResource("clientcert-java/client.keystore").getPath())
                .setKeyStorePassword("airlift")
                .setTrustStorePath(getResource("clientcert-java/client.truststore").getPath())
                .setTrustStorePassword("airlift");

        try (JettyHttpClient httpClient = new JettyHttpClient(clientConfig)) {
            StringResponse response = httpClient.execute(
                    prepareGet().setUri(httpServerInfo.getHttpsUri()).build(),
                    createStringResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getBody(), "CN=testing,OU=Client,O=Airlift,L=Palo Alto,ST=CA,C=US");
        }
    }

    @Test(dataProvider = "serverAdminMode")
    public void testKeystoreUpdate(boolean serverAdminMode)
            throws Exception
    {
        tempDir = createTempDirectory("test-keystore").toFile().getCanonicalFile();
        Path tempKeyStore = tempDir.toPath().resolve("server.keystore").toAbsolutePath();
        Path originalKeyStore = Path.of(getResource("clientcert-java/server.keystore").getPath());
        Path updatedKeyStore = Path.of(getResource("clientcert-java/updatedServer.keystore").getPath());

        // Start the server with the original keystore
        Files.copy(originalKeyStore, tempKeyStore, REPLACE_EXISTING);

        int keyStoreScanIntervalSeconds = 1;
        config.setHttpEnabled(false)
                .setAdminEnabled(serverAdminMode)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(tempKeyStore.toString())
                .setKeystorePassword("airlift")
                .setKeystoreScanIntervalSeconds(keyStoreScanIntervalSeconds);

        createAndStartServer();

        // Original certificate subject is returned
        assertEquals(getServerCertSubject(), "CN=localhost,OU=Server,O=Airlift,L=Palo Alto,ST=CA,C=US");

        // Replace the keystore with the replacement keystore
        Files.copy(updatedKeyStore, tempKeyStore, REPLACE_EXISTING);

        Thread.sleep(keyStoreScanIntervalSeconds * 1000L); // wait for the first scan to complete
        // Poll every 100ms up to 2 seconds for the updated keystore to be picked up
        for (int i = 0; i < 20; i++) {
            try {
                // Updated certificate subject is returned
                assertEquals(getServerCertSubject(), "CN=localhost,OU=Server,O=updated,L=Palo Alto,ST=CA,C=US");
                return;
            }
            catch (AssertionError e) {
                Thread.sleep(100);
            }
        }
        fail("Updated keystore was not picked up within expected time");
    }

    @Test
    public void testClientCertificatePem()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setAdminEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("clientcert-pem/server.pem").getPath())
                .setKeystorePassword("airlift")
                .setTrustStorePath(getResource("clientcert-pem/ca.crt").getPath());

        createAndStartServer(createCertTestServlet());

        HttpClientConfig clientConfig = new HttpClientConfig()
                .setKeyStorePath(getResource("clientcert-pem/client.pem").getPath())
                .setKeyStorePassword("airlift")
                .setTrustStorePath(getResource("clientcert-pem/ca.crt").getPath());

        assertClientCertificateRequest(clientConfig);
    }

    private void assertClientCertificateRequest(HttpClientConfig clientConfig)
    {
        try (JettyHttpClient httpClient = new JettyHttpClient(clientConfig)) {
            StringResponse response = httpClient.execute(
                    prepareGet().setUri(httpServerInfo.getHttpsUri()).build(),
                    createStringResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);
            assertEquals(response.getBody(), "CN=testing,OU=Client,O=Airlift,L=Palo Alto,ST=CA,C=US");
        }
    }

    @Test
    public void testShowStackTraceEnabled()
            throws Exception
    {
        createServer(new ErrorServlet());
        server.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStringResponseHandler());
            assertEquals(response.getStatusCode(), 500);
            assertContains(response.getBody(), "ErrorServlet.java");
        }
    }

    @Test
    public void testShowStackTraceDisabled()
            throws Exception
    {
        config.setShowStackTrace(false);
        createServer(new ErrorServlet());
        server.start();

        try (HttpClient client = new JettyHttpClient()) {
            StringResponse response = client.execute(prepareGet().setUri(httpServerInfo.getHttpUri()).build(), createStringResponseHandler());
            assertEquals(response.getStatusCode(), 500);
            assertTrue(!response.getBody().contains("ErrorServlet.java"));
        }
    }

    @Test(timeOut = 30000)
    public void testStop()
            throws Exception
    {
        DummyServlet servlet = new DummyServlet();
        createAndStartServer(servlet);

        try (HttpClient client = new JettyHttpClient()) {
            URI uri = URI.create(httpServerInfo.getHttpUri().toASCIIString() + "/?sleep=50000");
            Request request = prepareGet().setUri(uri).build();
            HttpResponseFuture<?> future = client.executeAsync(request, createStatusResponseHandler());

            // wait until the servlet starts processing the request
            servlet.getLatch().await(1, TimeUnit.SECONDS);

            // stop server while the request is still active
            server.stop();

            // wait until the server is stopped
            server.join();

            // request should fail rather than sleeping the full duration
            try {
                future.get(5, TimeUnit.SECONDS);
                fail("expected exception");
            }
            catch (ExecutionException e) {
                assertInstanceOf(e.getCause(), UncheckedIOException.class);
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Insufficient configured threads: .*")
    public void testInsufficientThreadsHttp()
            throws Exception
    {
        config.setMaxThreads(1);
        createAndStartServer();
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Insufficient configured threads: .*")
    public void testInsufficientThreadsHttps()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("test.keystore").getPath())
                .setKeystorePassword("airlift")
                .setMaxThreads(1);
        createAndStartServer();
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = ".+Cannot recover key.*")
    public void testInsufficientPasswordToAccessKeystore()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(getResource("test.keystore.with.two.passwords").getPath())
                .setKeystorePassword("airlift");
        createAndStartServer();
    }

    @Test
    public void testHttpsDaysUntilCertificateExpiration()
            throws Exception
    {
        config.setHttpEnabled(false)
                .setHttpsEnabled(true)
                .setHttpsPort(0)
                .setKeystorePath(Path.of(getResource("test.keystore").toURI()).toAbsolutePath().toString())
                .setKeystorePassword("airlift");
        createAndStartServer();
        Long daysUntilCertificateExpiration = server.getDaysUntilCertificateExpiration();
        assertNotNull(daysUntilCertificateExpiration);
        // todo this should be positive but the certificate is expired
        assertTrue(daysUntilCertificateExpiration < 0);
    }

    @Test
    public void testNoHttpsDaysUntilCertificateExpiration()
            throws Exception
    {
        config.setHttpEnabled(true)
                .setHttpsPort(0);
        createAndStartServer();
        assertNull(server.getDaysUntilCertificateExpiration());
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Insufficient configured threads: .*")
    public void testInsufficientThreadsAdmin()
            throws Exception
    {
        config.setAdminMaxThreads(1);
        createAndStartServer();
    }

    private void createAndStartServer()
            throws Exception
    {
        createAndStartServer(new DummyServlet());
    }

    private String getServerCertSubject()
            throws NoSuchAlgorithmException, KeyManagementException, IOException
    {
        SSLContext trustAllSSLContext = SSLContext.getInstance("TLS");
        trustAllSSLContext.init(null, new TrustManager[] {new X509TrustManager()
        {
            public void checkClientTrusted(X509Certificate[] certs, String authType)
            {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType)
            {
            }

            public X509Certificate[] getAcceptedIssuers()
            {
                return null;
            }
        }}, new SecureRandom());

        URI uri = httpServerInfo.getHttpsUri();
        HttpsURLConnection conn = (HttpsURLConnection) uri.toURL().openConnection();
        conn.setSSLSocketFactory(trustAllSSLContext.getSocketFactory());
        conn.connect();

        X509Certificate[] certs = (X509Certificate[]) conn.getServerCertificates();
        assertTrue(certs.length > 0, String.format("No certificates were obtained for URI: %s", uri));
        // Return the leaf certificate subject
        String subject = certs[0].getSubjectX500Principal().getName();
        conn.disconnect();

        return subject;
    }

    private void createAndStartServer(HttpServlet servlet)
            throws Exception
    {
        closeChannels(httpServerInfo);
        httpServerInfo = new HttpServerInfo(config, nodeInfo);
        createServer(servlet);
        server.start();
    }

    private void createServer()
    {
        createServer(new DummyServlet());
    }

    private void createServer(HttpServlet servlet)
    {
        HashLoginServiceProvider loginServiceProvider = new HashLoginServiceProvider(config);
        HttpServerProvider serverProvider = new HttpServerProvider(
                httpServerInfo,
                nodeInfo,
                config,
                servlet,
                ImmutableMap.of(),
                ImmutableSet.of(new DummyFilter()),
                ImmutableSet.of(),
                ImmutableSet.of(),
                new RequestStats(),
                new NullEventClient());
        serverProvider.setTheAdminServlet(new DummyServlet());
        serverProvider.setLoginService(loginServiceProvider.get());
        serverProvider.setTokenManager(new TraceTokenManager());
        server = serverProvider.get();
    }
}
