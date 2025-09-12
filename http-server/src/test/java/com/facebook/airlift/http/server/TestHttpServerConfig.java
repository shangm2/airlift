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

import com.facebook.airlift.configuration.testing.ConfigAssertions;
import com.facebook.airlift.units.DataSize;
import com.facebook.airlift.units.Duration;
import com.google.common.collect.ImmutableMap;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.facebook.airlift.units.DataSize.Unit.GIGABYTE;
import static com.facebook.airlift.units.DataSize.Unit.KILOBYTE;
import static com.facebook.airlift.units.DataSize.Unit.MEGABYTE;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestHttpServerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpServerConfig.class)
                .setHttpEnabled(true)
                .setHttpPort(8080)
                .setHttpAcceptQueueSize(8000)
                .setHttpsEnabled(false)
                .setHttpsPort(8443)
                .setSecureRandomAlgorithm(null)
                .setHttpsIncludedCipherSuites("")
                .setHttpsExcludedCipherSuites(String.join(",", getJettyDefaultExcludedCiphers()))
                .setSslSessionTimeout(new Duration(4, HOURS))
                .setSslSessionCacheSize(10_000)
                .setKeystorePath(null)
                .setKeystorePassword(null)
                .setKeyManagerPassword(null)
                .setTrustStorePath(null)
                .setTrustStorePassword(null)
                .setLogPath("var/log/http-request.log")
                .setLogEnabled(true)
                .setRequestStatsEnabled(true)
                .setLogMaxFileSize(new DataSize(100, MEGABYTE))
                .setLogHistory(15)
                .setLogQueueSize(10_000)
                .setLogCompressionEnabled(true)
                .setHttpAcceptorThreads(null)
                .setHttpSelectorThreads(null)
                .setHttpsAcceptorThreads(null)
                .setHttpsSelectorThreads(null)
                .setMinThreads(2)
                .setMaxThreads(200)
                .setTimeoutConcurrency(1)
                .setTimeoutThreads(1)
                .setThreadMaxIdleTime(new Duration(1, MINUTES))
                .setNetworkMaxIdleTime(new Duration(200, SECONDS))
                .setUserAuthFile(null)
                .setAdminEnabled(true)
                .setAdminPort(0)
                .setAdminMinThreads(2)
                .setAdminMaxThreads(200)
                .setMaxRequestHeaderSize(null)
                .setMaxResponseHeaderSize(null)
                .setHttp2MaxConcurrentStreams(16384)
                .setShowStackTrace(true)
                .setHttp2InitialSessionReceiveWindowSize(new DataSize(16, MEGABYTE))
                .setHttp2InputBufferSize(new DataSize(8, KILOBYTE))
                .setHttp2InitialStreamReceiveWindowSize(new DataSize(16, MEGABYTE))
                .setHttp2StreamIdleTimeout(new Duration(15, SECONDS))
                .setAuthorizationEnabled(false)
                .setDefaultAuthorizationPolicy(HttpServerConfig.AuthorizationPolicy.ALLOW)
                .setDefaultAllowedRoles("")
                .setAllowUnsecureRequestsInAuthorizer(false)
                .setSniHostCheck(true)
                .setUriComplianceMode(UriCompliance.DEFAULT)
                .setHttpComplianceViolations(""));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.enabled", "false")
                .put("http-server.http.port", "1")
                .put("http-server.accept-queue-size", "1024")
                .put("http-server.https.enabled", "true")
                .put("http-server.https.port", "2")
                .put("http-server.https.keystore.path", "/keystore")
                .put("http-server.https.keystore.key", "keystore password")
                .put("http-server.https.keymanager.password", "keymanager password")
                .put("http-server.https.truststore.path", "/truststore")
                .put("http-server.https.truststore.key", "truststore password")
                .put("http-server.https.secure-random-algorithm", "NativePRNG")
                .put("http-server.https.included-cipher", "TLS_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .put("http-server.https.excluded-cipher", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .put("http-server.https.ssl-session-timeout", "7h")
                .put("http-server.https.ssl-session-cache-size", "456")
                .put("http-server.log.path", "/log")
                .put("http-server.log.enabled", "false")
                .put("http-server.log.max-size", "1GB")
                .put("http-server.log.max-history", "1")
                .put("http-server.log.queue-size", "1")
                .put("http-server.log.compression.enabled", "false")
                .put("http-server.request-stats.enabled", "false")
                .put("http-server.http.acceptor-threads", "10")
                .put("http-server.http.selector-threads", "11")
                .put("http-server.https.acceptor-threads", "12")
                .put("http-server.https.selector-threads", "13")
                .put("http-server.threads.min", "100")
                .put("http-server.threads.max", "500")
                .put("http-server.timeout-concurrency", "5")
                .put("http-server.timeout-threads", "10")
                .put("http-server.threads.max-idle-time", "10m")
                .put("http-server.net.max-idle-time", "20m")
                .put("http-server.auth.users-file", "/auth")
                .put("http-server.admin.enabled", "false")
                .put("http-server.admin.port", "3")
                .put("http-server.admin.threads.min", "3")
                .put("http-server.admin.threads.max", "4")
                .put("http-server.max-request-header-size", "32kB")
                .put("http-server.max-response-header-size", "57kB")
                .put("http-server.http2.max-concurrent-streams", "1234")
                .put("http-server.show-stack-trace", "false")
                .put("http-server.http2.session-receive-window-size", "4MB")
                .put("http-server.http2.stream-receive-window-size", "4MB")
                .put("http-server.http2.input-buffer-size", "4MB")
                .put("http-server.http2.stream-idle-timeout", "23s")
                .put("http-server.authorization.enabled", "true")
                .put("http-server.authorization.default-policy", "DENY")
                .put("http-server.authorization.default-allowed-roles", "user, internal, admin")
                .put("http-server.authorization.allow-unsecured-requests", "true")
                .put("http-server.https.sni-host-check", "false")
                .put("http-server.uri-compliance.mode", "LEGACY")
                .put("http-server.http-compliance.violations", "UNSAFE_HOST_HEADER,MISMATCHED_AUTHORITY")
                .build();

        HttpServerConfig expected = new HttpServerConfig()
                .setHttpEnabled(false)
                .setHttpPort(1)
                .setHttpAcceptQueueSize(1024)
                .setHttpsEnabled(true)
                .setHttpsPort(2)
                .setHttpsIncludedCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .setHttpsExcludedCipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .setSslSessionTimeout(new Duration(7, HOURS))
                .setSslSessionCacheSize(456)
                .setKeystorePath("/keystore")
                .setKeystorePassword("keystore password")
                .setKeyManagerPassword("keymanager password")
                .setTrustStorePath("/truststore")
                .setTrustStorePassword("truststore password")
                .setSecureRandomAlgorithm("NativePRNG")
                .setLogPath("/log")
                .setLogEnabled(false)
                .setRequestStatsEnabled(false)
                .setLogMaxFileSize(new DataSize(1, GIGABYTE))
                .setLogHistory(1)
                .setLogQueueSize(1)
                .setLogCompressionEnabled(false)
                .setHttpAcceptorThreads(10)
                .setHttpSelectorThreads(11)
                .setHttpsAcceptorThreads(12)
                .setHttpsSelectorThreads(13)
                .setMinThreads(100)
                .setMaxThreads(500)
                .setTimeoutConcurrency(5)
                .setTimeoutThreads(10)
                .setThreadMaxIdleTime(new Duration(10, MINUTES))
                .setNetworkMaxIdleTime(new Duration(20, MINUTES))
                .setMaxRequestHeaderSize(new DataSize(32, KILOBYTE))
                .setMaxResponseHeaderSize(new DataSize(57, KILOBYTE))
                .setUserAuthFile("/auth")
                .setAdminEnabled(false)
                .setAdminPort(3)
                .setAdminMinThreads(3)
                .setAdminMaxThreads(4)
                .setHttp2MaxConcurrentStreams(1234)
                .setShowStackTrace(false)
                .setHttp2InitialSessionReceiveWindowSize(new DataSize(4, MEGABYTE))
                .setHttp2InitialStreamReceiveWindowSize(new DataSize(4, MEGABYTE))
                .setHttp2InputBufferSize(new DataSize(4, MEGABYTE))
                .setHttp2StreamIdleTimeout(new Duration(23, SECONDS))
                .setAuthorizationEnabled(true)
                .setDefaultAuthorizationPolicy(HttpServerConfig.AuthorizationPolicy.DENY)
                .setDefaultAllowedRoles("user, internal, admin")
                .setAllowUnsecureRequestsInAuthorizer(true)
                .setSniHostCheck(false)
                .setUriComplianceMode(UriCompliance.LEGACY)
                .setHttpComplianceViolations("UNSAFE_HOST_HEADER,MISMATCHED_AUTHORITY");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    private List<String> getJettyDefaultExcludedCiphers()
    {
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        return Arrays.asList(sslContextFactory.getExcludeCipherSuites());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Invalid value for http compliance violation: INVALID_VALUE. Permitted values are " +
                    "\\[CASE_SENSITIVE_FIELD_NAME, CASE_INSENSITIVE_METHOD, HTTP_0_9, MULTILINE_FIELD_VALUE, MULTIPLE_CONTENT_LENGTHS, TRANSFER_ENCODING_WITH_CONTENT_LENGTH, " +
                    "WHITESPACE_AFTER_FIELD_NAME, NO_COLON_AFTER_FIELD_NAME, DUPLICATE_HOST_HEADERS, UNSAFE_HOST_HEADER, MISMATCHED_AUTHORITY\\]")
    public void testInvalidHttpComplianceViolation()
    {
        new HttpServerConfig().setHttpComplianceViolations("INVALID_VALUE");
    }
}
