/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.eclipse.xtext.web.server.ISession;
import org.eclipse.xtext.xbase.lib.Functions.Function0;

import jakarta.servlet.http.HttpSession;

/**
 * Provides access to the information stored in a {@link HttpSession}.
 */
public class HttpSessionWrapper implements ISession {
    private final HttpSession session;

    /**
     * Create the Session Wrapper.
     * 
     * @param session a session
     */
    public HttpSessionWrapper(HttpSession session) {
        this.session = session;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key) {
        return (T) session.getAttribute(key.toString());
    }

    @Override
    public <T> T get(Object key, Function0<? extends T> factory) {
        synchronized (session) {
            T sessionValue = get(key);
            if (sessionValue != null) {
                return sessionValue;
            } else {
                T factoryValue = factory.apply();
                put(key, factoryValue);
                return factoryValue;
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        session.setAttribute(key.toString(), value);
    }

    @Override
    public void remove(Object key) {
        session.removeAttribute(key.toString());
    }

    /**
     * @return the session
     */
    public HttpSession getSession() {
        return session;
    }
}
