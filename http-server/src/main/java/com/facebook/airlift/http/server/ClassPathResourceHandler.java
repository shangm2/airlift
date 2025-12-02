/*
 * Copyright (C) 2012 Ness Computing, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.airlift.http.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpException;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jetty.http.HttpHeader.CONTENT_TYPE;
import static org.eclipse.jetty.server.Response.toRedirectURI;

/**
 * Serves files from a given folder on the classpath through jetty.
 * Intended to serve a couple of static files e.g. for javascript or HTML.
 */
// Forked from https://github.com/NessComputing/components-ness-httpserver/
public class ClassPathResourceHandler
        extends Handler.Wrapper
{
    private static final MimeTypes MIME_TYPES;
    private static final byte[] EMPTY_BYTE = new byte[0];

    static {
        MIME_TYPES = new MimeTypes();
    }

    private final String baseUri; // "" or "/foo"
    private final String classPathResourceBase;
    private final List<String> welcomeFiles;
    private final Map<String, String> extraHeaders;

    public ClassPathResourceHandler(String baseUri, String classPathResourceBase, String... welcomeFiles)
    {
        this(baseUri, classPathResourceBase, ImmutableList.copyOf(welcomeFiles), ImmutableMap.of());
    }

    public ClassPathResourceHandler(String baseUri, String classPathResourceBase, List<String> welcomeFiles, Map<String, String> extraHeaders)
    {
        requireNonNull(baseUri, "baseUri is null");
        requireNonNull(classPathResourceBase, "classPathResourceBase is null");
        requireNonNull(welcomeFiles, "welcomeFiles is null");
        checkArgument(baseUri.equals("/") || !baseUri.endsWith("/"), "baseUri should not end with a slash: %s", baseUri);

        baseUri = baseUri.startsWith("/") ? baseUri : '/' + baseUri;
        baseUri = baseUri.equals("/") ? "" : baseUri;
        this.baseUri = baseUri;

        this.classPathResourceBase = classPathResourceBase;

        ImmutableList.Builder<String> files = ImmutableList.builder();
        for (String welcomeFile : welcomeFiles) {
            if (!welcomeFile.startsWith("/")) {
                welcomeFile = "/" + welcomeFile;
            }
            files.add(welcomeFile);
        }
        this.welcomeFiles = files.build();
        this.extraHeaders = ImmutableMap.copyOf(extraHeaders);
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback)
    {
        String resourcePath = getResourcePath(request);
        if (resourcePath == null) {
            return false;
        }

        if (resourcePath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
            response.getHeaders().add(HttpHeaders.LOCATION, toRedirectURI(request, baseUri + "/"));
            callback.succeeded();
            return true;
        }

        URL resource = getResource(resourcePath);
        if (resource == null) {
            return false;
        }

        // When a request hits this handler, it will serve something. Either data or an error.

        String method = request.getMethod();
        boolean skipContent = false;
        if (!HttpMethod.GET.is(method)) {
            if (HttpMethod.HEAD.is(method)) {
                skipContent = true;
            }
            else {
                callback.failed(new HttpException.IllegalArgumentException(HttpServletResponse.SC_METHOD_NOT_ALLOWED));
                return true;
            }
        }

        InputStream resourceStream = null;
        try {
            resourceStream = resource.openStream();

            String contentType = MIME_TYPES.getMimeByExtension(resource.toString());
            response.getHeaders().add(CONTENT_TYPE, contentType);
            extraHeaders.forEach((name, value) -> response.getHeaders().add(name, value));
            if (skipContent) {
                callback.succeeded();
                return true;
            }

            try (OutputStream out = Response.asBufferedOutputStream(request, response)) {
                resourceStream.transferTo(out);
            }
            response.write(true, ByteBuffer.wrap(EMPTY_BYTE), callback);
        }
        catch (Exception e) {
            callback.failed(e);
        }
        finally {
            closeQuietly(resourceStream);
        }
        return true;
    }

    @Nullable
    private String getResourcePath(Request request)
    {
        String pathInfo = request.getHttpURI().getPath();

        // Only serve the content if the request matches the base path.
        if (pathInfo == null || !pathInfo.startsWith(baseUri)) {
            return null;
        }

        // chop off the base uri
        pathInfo = pathInfo.substring(baseUri.length());

        if (!pathInfo.startsWith("/") && !pathInfo.isEmpty()) {
            // basepath is /foo and request went to /foobar --> pathInfo starts with bar
            // basepath is /foo and request went to /foo --> pathInfo should be /index.html
            return null;
        }

        return pathInfo;
    }

    private URL getResource(String resourcePath)
    {
        checkArgument(resourcePath.startsWith("/"), "resourcePath does not start with a slash: %s", resourcePath);

        if (!"/".equals(resourcePath)) {
            return getClass().getClassLoader().getResource(classPathResourceBase + resourcePath);
        }

        // check welcome files
        for (String welcomeFile : welcomeFiles) {
            URL resource = getClass().getClassLoader().getResource(classPathResourceBase + welcomeFile);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    private static void closeQuietly(@Nullable InputStream in)
    {
        if (in != null) {
            try {
                in.close();
            }
            catch (IOException e) {
                // ignored
            }
        }
    }
}
