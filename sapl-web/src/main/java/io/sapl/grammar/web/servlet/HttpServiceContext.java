/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.web.servlet;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.xtext.web.server.IServiceContext;
import org.eclipse.xtext.web.server.ISession;
import org.eclipse.xtext.xbase.lib.Exceptions;

import com.google.common.io.CharStreams;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Provides the parameters and metadata of an {@link HttpServletRequest}.
 */
public class HttpServiceContext implements IServiceContext {
    private final HttpServletRequest request;

    private final Map<String, String> parameters = new HashMap<>();

    private HttpSessionWrapper sessionWrapper;

    /**
     * Creates the Context.
     * 
     * @param request a HttpServletRequest
     */
    public HttpServiceContext(HttpServletRequest request) {
        this.request = request;
        this.initializeParameters();
    }

    private String initializeParameters() {
        try {
            String[] contentType = null;
            if (request.getContentType() != null) {
                contentType = request.getContentType().split(";(\\s*)");
            }
            if (contentType != null && "application/x-www-form-urlencoded".equals(contentType[0])) {
                String charset;
                if (contentType.length >= 2 && contentType[1].startsWith("charset=")) {
                    charset = (contentType[1]).substring("charset=".length());
                } else {
                    charset = Charset.defaultCharset().toString();
                }
                String[] encodedParams = CharStreams.toString(request.getReader()).split("&");
                for (String param : encodedParams) {
                    int nameEnd = param.indexOf("=");
                    if (nameEnd > 0) {
                        String key   = param.substring(0, nameEnd);
                        String value = URLDecoder.decode(param.substring(nameEnd + 1), charset);
                        parameters.put(key, value);
                    }
                }
            }
            Enumeration<String> paramNames = request.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String name = paramNames.nextElement();
                parameters.put(name, request.getParameter(name));
            }
            if (!parameters.containsKey(IServiceContext.SERVICE_TYPE)) {
                String substring = null;
                if (request.getPathInfo() != null) {
                    substring = request.getPathInfo().substring(1);
                }
                return parameters.put(IServiceContext.SERVICE_TYPE, substring);
            }
            return null;
        } catch (IOException e) {
            throw Exceptions.sneakyThrow(e);
        }
    }

    @Override
    public Set<String> getParameterKeys() {
        return Collections.unmodifiableSet(parameters.keySet());
    }

    @Override
    public String getParameter(String key) {
        return parameters.get(key);
    }

    @Override
    public ISession getSession() {
        if (sessionWrapper == null) {
            sessionWrapper = new HttpSessionWrapper(request.getSession(true));
        }
        return sessionWrapper;
    }

    /**
     * @return the request
     */
    public HttpServletRequest getRequest() {
        return request;
    }
}