/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.test.grammar.web;

import com.google.inject.Injector;
import jakarta.servlet.annotation.WebServlet;
import java.io.Serial;
import lombok.SneakyThrows;
import org.eclipse.xtext.util.DisposableRegistry;
import org.eclipse.xtext.web.servlet.XtextServlet;

/**
 * Deploy this class into a servlet container to enable DSL-specific services.
 */
@WebServlet(name = "SaplTestXtextServices", urlPatterns = "/sapl-test/xtext-service/*")
public class SAPLTestServlet extends XtextServlet {
    @Serial
    private static final long serialVersionUID = 6987561491643911622L;

    private transient DisposableRegistry disposableRegistry;

    @Override
    @SneakyThrows
    public void init() {
        super.init();
        final Injector injector = new SAPLTestWebSetup().createInjectorAndDoEMFRegistration();
        disposableRegistry = injector.getInstance(DisposableRegistry.class);
    }

    @Override
    public void destroy() {
        if (disposableRegistry != null) {
            disposableRegistry.dispose();
            disposableRegistry = null;
        }
        super.destroy();
    }

}
