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

import com.facebook.airlift.configuration.Config;
import com.facebook.airlift.configuration.ConfigDescription;
import com.facebook.airlift.configuration.ConfigSecuritySensitive;
import com.facebook.airlift.configuration.DefunctConfig;
import com.facebook.airlift.units.DataSize;
import com.facebook.airlift.units.Duration;
import com.facebook.airlift.units.MaxDataSize;
import com.facebook.airlift.units.MinDataSize;
import com.facebook.airlift.units.MinDuration;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.facebook.airlift.http.server.UriCompliance.DEFAULT;
import static com.facebook.airlift.units.DataSize.Unit.KILOBYTE;
import static com.facebook.airlift.units.DataSize.Unit.MEGABYTE;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@DefunctConfig({
        "jetty.http.enabled",
        "jetty.http.port",
        "jetty.https.enabled",
        "jetty.https.port",
        "jetty.https.keystore.path",
        "jetty.https.keystore.password",
        "jetty.log.path",
        "jetty.log.retain-days",
        "jetty.threads.min",
        "jetty.threads.max",
        "jetty.threads.max-idle-time-ms",
        "jetty.net.max-idle-time-ms",
        "jetty.auth.users-file",
        "http-server.https.keystore.password",
        "http-server.log.retention-time",
})
public class HttpServerConfig
{
    public enum AuthorizationPolicy
    {
        ALLOW, DENY, DEFAULT_ROLES
    }

    private boolean httpEnabled = true;
    private int httpPort = 8080;
    private int httpAcceptQueueSize = 8000;

    private boolean httpsEnabled;
    private boolean sniHostCheck = true;
    private int httpsPort = 8443;
    private String keystorePath;
    private String keystorePassword;
    private String keyManagerPassword;
    private String trustStorePath;
    private String trustStorePassword;
    private String secureRandomAlgorithm;
    private List<String> includedCipherSuites = ImmutableList.of();
    private UriCompliance uriCompliance = DEFAULT;
    private List<HttpComplianceViolation> httpComplianceViolations = ImmutableList.of();

    /**
     * This property is initialized with Jetty's default excluded ciphers list.
     *
     * @see org.eclipse.jetty.util.ssl.SslContextFactory#SslContextFactory(boolean, String)
     */
    private List<String> excludedCipherSuites = ImmutableList.of("^.*_(MD5|SHA|SHA1)$", "^TLS_RSA_.*$", "^SSL_.*$", "^.*_NULL_.*$", "^.*_anon_.*$");

    private Duration sslSessionTimeout = new Duration(4, HOURS);
    private int sslSessionCacheSize = 10_000;

    private String logPath = "var/log/http-request.log";
    private boolean logEnabled = true;
    private boolean requestStatsEnabled = true;
    private int logHistory = 15;
    private int logQueueSize = 10_000;
    private DataSize logMaxFileSize = new DataSize(100, MEGABYTE);
    private boolean logCompressionEnabled = true;

    private Integer httpAcceptorThreads;
    private Integer httpSelectorThreads;
    private Integer httpsAcceptorThreads;
    private Integer httpsSelectorThreads;

    private int minThreads = 2;
    private int maxThreads = 200;
    private Duration threadMaxIdleTime = new Duration(1, MINUTES);
    private Duration networkMaxIdleTime = new Duration(200, SECONDS);
    private DataSize maxRequestHeaderSize;
    private DataSize maxResponseHeaderSize;
    private int http2MaxConcurrentStreams = 16384;
    private DataSize http2InitialSessionReceiveWindowSize = new DataSize(16, MEGABYTE);
    private DataSize http2InitialStreamReceiveWindowSize = new DataSize(16, MEGABYTE);
    private DataSize http2InputBufferSize = new DataSize(8, KILOBYTE);
    private Duration http2StreamIdleTimeout = new Duration(15, SECONDS);

    private String userAuthFile;

    private boolean adminEnabled = true;
    private int adminPort;
    private int adminMinThreads = 2;
    private int adminMaxThreads = 200;
    private int timeoutThreads = 1;
    private int timeoutConcurrency = 1;

    private boolean showStackTrace = true;
    private boolean authorizationEnabled;
    private AuthorizationPolicy defaultAuthorizationPolicy = AuthorizationPolicy.ALLOW;
    private Set<String> defaultAllowedRoles = ImmutableSet.of();
    private boolean allowUnsecureRequestsInAuthorizer;

    public boolean isHttpEnabled()
    {
        return httpEnabled;
    }

    @Config("http-server.http.enabled")
    public HttpServerConfig setHttpEnabled(boolean httpEnabled)
    {
        this.httpEnabled = httpEnabled;
        return this;
    }

    public int getHttpPort()
    {
        return httpPort;
    }

    @Config("http-server.accept-queue-size")
    public HttpServerConfig setHttpAcceptQueueSize(int httpAcceptQueueSize)
    {
        this.httpAcceptQueueSize = httpAcceptQueueSize;
        return this;
    }

    public int getHttpAcceptQueueSize()
    {
        return httpAcceptQueueSize;
    }

    @Config("http-server.http.port")
    public HttpServerConfig setHttpPort(int httpPort)
    {
        this.httpPort = httpPort;
        return this;
    }

    public boolean isHttpsEnabled()
    {
        return httpsEnabled;
    }

    @Config("http-server.https.enabled")
    public HttpServerConfig setHttpsEnabled(boolean httpsEnabled)
    {
        this.httpsEnabled = httpsEnabled;
        return this;
    }

    @Config("http-server.https.sni-host-check")
    public HttpServerConfig setSniHostCheck(boolean sniHostCheck)
    {
        this.sniHostCheck = sniHostCheck;
        return this;
    }

    public boolean isSniHostCheck()
    {
        return sniHostCheck;
    }

    public int getHttpsPort()
    {
        return httpsPort;
    }

    @Config("http-server.https.port")
    public HttpServerConfig setHttpsPort(int httpsPort)
    {
        this.httpsPort = httpsPort;
        return this;
    }

    @MinDuration("1s")
    public Duration getSslSessionTimeout()
    {
        return sslSessionTimeout;
    }

    @Config("http-server.https.ssl-session-timeout")
    public HttpServerConfig setSslSessionTimeout(Duration sslSessionTimeout)
    {
        this.sslSessionTimeout = sslSessionTimeout;
        return this;
    }

    @Min(1)
    public int getSslSessionCacheSize()
    {
        return sslSessionCacheSize;
    }

    @Config("http-server.https.ssl-session-cache-size")
    public HttpServerConfig setSslSessionCacheSize(int sslSessionCacheSize)
    {
        this.sslSessionCacheSize = sslSessionCacheSize;
        return this;
    }

    public String getKeystorePath()
    {
        return keystorePath;
    }

    @Config("http-server.https.keystore.path")
    public HttpServerConfig setKeystorePath(String keystorePath)
    {
        this.keystorePath = keystorePath;
        return this;
    }

    public String getKeystorePassword()
    {
        return keystorePassword;
    }

    @Config("http-server.https.keystore.key")
    @ConfigSecuritySensitive
    public HttpServerConfig setKeystorePassword(String keystorePassword)
    {
        this.keystorePassword = keystorePassword;
        return this;
    }

    public String getKeyManagerPassword()
    {
        return keyManagerPassword;
    }

    @Config("http-server.https.keymanager.password")
    @ConfigSecuritySensitive
    public HttpServerConfig setKeyManagerPassword(String keyManagerPassword)
    {
        this.keyManagerPassword = keyManagerPassword;
        return this;
    }

    public String getTrustStorePath()
    {
        return trustStorePath;
    }

    @Config("http-server.https.truststore.path")
    public HttpServerConfig setTrustStorePath(String trustStorePath)
    {
        this.trustStorePath = trustStorePath;
        return this;
    }

    public String getTrustStorePassword()
    {
        return trustStorePassword;
    }

    @Config("http-server.https.truststore.key")
    @ConfigSecuritySensitive
    public HttpServerConfig setTrustStorePassword(String trustStorePassword)
    {
        this.trustStorePassword = trustStorePassword;
        return this;
    }

    public String getSecureRandomAlgorithm()
    {
        return secureRandomAlgorithm;
    }

    @Config("http-server.https.secure-random-algorithm")
    public HttpServerConfig setSecureRandomAlgorithm(String secureRandomAlgorithm)
    {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
        return this;
    }

    public List<String> getHttpsIncludedCipherSuites()
    {
        return includedCipherSuites;
    }

    @Config("http-server.https.included-cipher")
    public HttpServerConfig setHttpsIncludedCipherSuites(String includedCipherSuites)
    {
        this.includedCipherSuites = Splitter
                .on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(requireNonNull(includedCipherSuites, "includedCipherSuites is null"));
        return this;
    }

    public List<String> getHttpsExcludedCipherSuites()
    {
        return excludedCipherSuites;
    }

    @Config("http-server.https.excluded-cipher")
    @ConfigDescription("Setting this config property overwrites Jetty's default excluded cipher suites")
    public HttpServerConfig setHttpsExcludedCipherSuites(String excludedCipherSuites)
    {
        this.excludedCipherSuites = Splitter
                .on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(requireNonNull(excludedCipherSuites, "excludedCipherSuites is null"));
        return this;
    }

    public String getLogPath()
    {
        return logPath;
    }

    @Config("http-server.log.path")
    public HttpServerConfig setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    public boolean isLogEnabled()
    {
        return logEnabled;
    }

    @Config("http-server.log.enabled")
    public HttpServerConfig setLogEnabled(boolean logEnabled)
    {
        this.logEnabled = logEnabled;
        return this;
    }

    public boolean isRequestStatsEnabled()
    {
        return requestStatsEnabled;
    }

    @Config("http-server.request-stats.enabled")
    public HttpServerConfig setRequestStatsEnabled(boolean requestStatsEnabled)
    {
        this.requestStatsEnabled = requestStatsEnabled;
        return this;
    }

    public DataSize getLogMaxFileSize()
    {
        return logMaxFileSize;
    }

    @Config("http-server.log.max-size")
    public HttpServerConfig setLogMaxFileSize(DataSize logMaxFileSize)
    {
        this.logMaxFileSize = logMaxFileSize;
        return this;
    }

    @Min(1)
    public Integer getHttpAcceptorThreads()
    {
        return httpAcceptorThreads;
    }

    @Config("http-server.http.acceptor-threads")
    public HttpServerConfig setHttpAcceptorThreads(Integer httpAcceptorThreads)
    {
        this.httpAcceptorThreads = httpAcceptorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpSelectorThreads()
    {
        return httpSelectorThreads;
    }

    @Config("http-server.http.selector-threads")
    public HttpServerConfig setHttpSelectorThreads(Integer httpSelectorThreads)
    {
        this.httpSelectorThreads = httpSelectorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpsAcceptorThreads()
    {
        return httpsAcceptorThreads;
    }

    @Config("http-server.https.acceptor-threads")
    public HttpServerConfig setHttpsAcceptorThreads(Integer httpsAcceptorThreads)
    {
        this.httpsAcceptorThreads = httpsAcceptorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpsSelectorThreads()
    {
        return httpsSelectorThreads;
    }

    @Config("http-server.https.selector-threads")
    public HttpServerConfig setHttpsSelectorThreads(Integer httpsSelectorThreads)
    {
        this.httpsSelectorThreads = httpsSelectorThreads;
        return this;
    }

    public int getMaxThreads()
    {
        return maxThreads;
    }

    @Config("http-server.threads.max")
    public HttpServerConfig setMaxThreads(int maxThreads)
    {
        this.maxThreads = maxThreads;
        return this;
    }

    public int getMinThreads()
    {
        return minThreads;
    }

    @Config("http-server.threads.min")
    public HttpServerConfig setMinThreads(int minThreads)
    {
        this.minThreads = minThreads;
        return this;
    }

    public Duration getThreadMaxIdleTime()
    {
        return threadMaxIdleTime;
    }

    @Config("http-server.threads.max-idle-time")
    public HttpServerConfig setThreadMaxIdleTime(Duration threadMaxIdleTime)
    {
        this.threadMaxIdleTime = threadMaxIdleTime;
        return this;
    }

    @Min(1)
    public int getTimeoutThreads()
    {
        return timeoutThreads;
    }

    @Config("http-server.timeout-threads")
    public HttpServerConfig setTimeoutThreads(int timeoutThreads)
    {
        this.timeoutThreads = timeoutThreads;
        return this;
    }

    @Min(1)
    public int getTimeoutConcurrency()
    {
        return timeoutConcurrency;
    }

    @Config("http-server.timeout-concurrency")
    public HttpServerConfig setTimeoutConcurrency(int timeoutConcurrency)
    {
        this.timeoutConcurrency = timeoutConcurrency;
        return this;
    }

    public int getLogHistory()
    {
        return logHistory;
    }

    @Config("http-server.log.max-history")
    public HttpServerConfig setLogHistory(int logHistory)
    {
        this.logHistory = logHistory;
        return this;
    }

    @Min(1)
    public int getLogQueueSize()
    {
        return logQueueSize;
    }

    @Config("http-server.log.queue-size")
    public HttpServerConfig setLogQueueSize(int logQueueSize)
    {
        this.logQueueSize = logQueueSize;
        return this;
    }

    public boolean isLogCompressionEnabled()
    {
        return logCompressionEnabled;
    }

    @Config("http-server.log.compression.enabled")
    public HttpServerConfig setLogCompressionEnabled(boolean logCompressionEnabled)
    {
        this.logCompressionEnabled = logCompressionEnabled;
        return this;
    }

    public String getUserAuthFile()
    {
        return userAuthFile;
    }

    @Config("http-server.auth.users-file")
    public HttpServerConfig setUserAuthFile(String userAuthFile)
    {
        this.userAuthFile = userAuthFile;
        return this;
    }

    public Duration getNetworkMaxIdleTime()
    {
        return networkMaxIdleTime;
    }

    @Config("http-server.net.max-idle-time")
    public HttpServerConfig setNetworkMaxIdleTime(Duration networkMaxIdleTime)
    {
        this.networkMaxIdleTime = networkMaxIdleTime;
        return this;
    }

    public boolean isAdminEnabled()
    {
        return adminEnabled;
    }

    @Config("http-server.admin.enabled")
    public HttpServerConfig setAdminEnabled(boolean adminEnabled)
    {
        this.adminEnabled = adminEnabled;
        return this;
    }

    public int getAdminPort()
    {
        return adminPort;
    }

    @Config("http-server.admin.port")
    public HttpServerConfig setAdminPort(int adminPort)
    {
        this.adminPort = adminPort;
        return this;
    }

    public int getAdminMinThreads()
    {
        return adminMinThreads;
    }

    @Config("http-server.admin.threads.min")
    public HttpServerConfig setAdminMinThreads(int adminMinThreads)
    {
        this.adminMinThreads = adminMinThreads;
        return this;
    }

    @Min(2)
    public int getAdminMaxThreads()
    {
        return adminMaxThreads;
    }

    @Config("http-server.admin.threads.max")
    public HttpServerConfig setAdminMaxThreads(int adminMaxThreads)
    {
        this.adminMaxThreads = adminMaxThreads;
        return this;
    }

    public DataSize getMaxRequestHeaderSize()
    {
        return maxRequestHeaderSize;
    }

    @Config("http-server.max-request-header-size")
    public HttpServerConfig setMaxRequestHeaderSize(DataSize maxRequestHeaderSize)
    {
        this.maxRequestHeaderSize = maxRequestHeaderSize;
        return this;
    }

    public DataSize getMaxResponseHeaderSize()
    {
        return maxResponseHeaderSize;
    }

    @Config("http-server.max-response-header-size")
    public HttpServerConfig setMaxResponseHeaderSize(DataSize maxResponseHeaderSize)
    {
        this.maxResponseHeaderSize = maxResponseHeaderSize;
        return this;
    }

    @Min(1)
    public int getHttp2MaxConcurrentStreams()
    {
        return http2MaxConcurrentStreams;
    }

    @Config("http-server.http2.max-concurrent-streams")
    @ConfigDescription("Maximum concurrent streams per connection for HTTP/2")
    public HttpServerConfig setHttp2MaxConcurrentStreams(int http2MaxConcurrentStreams)
    {
        this.http2MaxConcurrentStreams = http2MaxConcurrentStreams;
        return this;
    }

    public boolean isShowStackTrace()
    {
        return showStackTrace;
    }

    @Config("http-server.show-stack-trace")
    @ConfigDescription("Show the stack trace when generating an error response")
    public HttpServerConfig setShowStackTrace(boolean showStackTrace)
    {
        this.showStackTrace = showStackTrace;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("1GB")
    public DataSize getHttp2InitialSessionReceiveWindowSize()
    {
        return http2InitialSessionReceiveWindowSize;
    }

    @Config("http-server.http2.session-receive-window-size")
    @ConfigDescription("Initial size of session's flow control receive window for HTTP/2")
    public HttpServerConfig setHttp2InitialSessionReceiveWindowSize(DataSize http2InitialSessionReceiveWindowSize)
    {
        this.http2InitialSessionReceiveWindowSize = http2InitialSessionReceiveWindowSize;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("1GB")
    public DataSize getHttp2InitialStreamReceiveWindowSize()
    {
        return http2InitialStreamReceiveWindowSize;
    }

    @Config("http-server.http2.stream-receive-window-size")
    @ConfigDescription("Initial size of stream's flow control receive window for HTTP/2")
    public HttpServerConfig setHttp2InitialStreamReceiveWindowSize(DataSize http2InitialStreamReceiveWindowSize)
    {
        this.http2InitialStreamReceiveWindowSize = http2InitialStreamReceiveWindowSize;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("32MB")
    public DataSize getHttp2InputBufferSize()
    {
        return http2InputBufferSize;
    }

    @Config("http-server.http2.input-buffer-size")
    @ConfigDescription("Size of the buffer used to read from the network for HTTP/2")
    public HttpServerConfig setHttp2InputBufferSize(DataSize http2InputBufferSize)
    {
        this.http2InputBufferSize = http2InputBufferSize;
        return this;
    }

    public Duration getHttp2StreamIdleTimeout()
    {
        return http2StreamIdleTimeout;
    }

    @Config("http-server.http2.stream-idle-timeout")
    public HttpServerConfig setHttp2StreamIdleTimeout(Duration http2StreamIdleTimeout)
    {
        this.http2StreamIdleTimeout = http2StreamIdleTimeout;
        return this;
    }

    public boolean isAuthorizationEnabled()
    {
        return authorizationEnabled;
    }

    @Config("http-server.authorization.enabled")
    public HttpServerConfig setAuthorizationEnabled(boolean authorizationEnabled)
    {
        this.authorizationEnabled = authorizationEnabled;
        return this;
    }

    @NotNull
    public AuthorizationPolicy getDefaultAuthorizationPolicy()
    {
        return defaultAuthorizationPolicy;
    }

    @Config("http-server.authorization.default-policy")
    @ConfigDescription("The default authorization policy applies to endpoints without allowed roles specified")
    public HttpServerConfig setDefaultAuthorizationPolicy(AuthorizationPolicy defaultAuthorizationPolicy)
    {
        this.defaultAuthorizationPolicy = requireNonNull(defaultAuthorizationPolicy, "defaultAuthorizationPolicy is null");
        return this;
    }

    public Set<String> getDefaultAllowedRoles()
    {
        return defaultAllowedRoles;
    }

    @Config("http-server.authorization.default-allowed-roles")
    @ConfigDescription("A comma-separated list of default roles allowed to access endpoints without explicitly specified roles")
    public HttpServerConfig setDefaultAllowedRoles(String defaultAllowedRoles)
    {
        this.defaultAllowedRoles = Splitter
                .on(",")
                .trimResults()
                .omitEmptyStrings()
                .splitToList(requireNonNull(defaultAllowedRoles, "defaultAllowedRoles is null"))
                .stream()
                .map(role -> role.toLowerCase(ENGLISH))
                .collect(toImmutableSet());
        return this;
    }

    public boolean isAllowUnsecureRequestsInAuthorizer()
    {
        return allowUnsecureRequestsInAuthorizer;
    }

    @Config("http-server.authorization.allow-unsecured-requests")
    @ConfigDescription("Skip authorization check for unsecured requests when this config is on")
    public HttpServerConfig setAllowUnsecureRequestsInAuthorizer(boolean allowUnsecureRequestsInAuthorizer)
    {
        this.allowUnsecureRequestsInAuthorizer = allowUnsecureRequestsInAuthorizer;
        return this;
    }

    @Config("http-server.uri-compliance.mode")
    @ConfigDescription("The URI compliance mode")
    public HttpServerConfig setUriComplianceMode(UriCompliance uriCompliance)
    {
        this.uriCompliance = uriCompliance;
        return this;
    }

    public UriCompliance getUriComplianceMode()
    {
        return uriCompliance;
    }

    @Config("http-server.http-compliance.violations")
    @ConfigDescription("The http compliance violations permitted")
    public HttpServerConfig setHttpComplianceViolations(String httpComplianceViolations)
    {
        this.httpComplianceViolations = Splitter
                .on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(requireNonNull(httpComplianceViolations, "httpComplianceViolations is null"))
                .stream()
                .map(violation -> {
                    try {
                        return HttpComplianceViolation.valueOf(violation);
                    }
                    catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid value for http compliance violation: " + httpComplianceViolations + ". Permitted values are " + Arrays.toString(HttpComplianceViolation.values()), e);
                    }
                })
                .collect(toImmutableList());
        return this;
    }

    public List<HttpComplianceViolation> getHttpComplianceViolations()
    {
        return httpComplianceViolations;
    }
}
