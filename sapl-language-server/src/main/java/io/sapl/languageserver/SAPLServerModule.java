/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.languageserver;

import org.eclipse.xtext.ide.server.ServerModule;
import org.eclipse.xtext.ide.server.contentassist.ContentAssistService;

import io.sapl.grammar.ide.contentassist.SAPLContentAssistService;

/**
 * Custom Xtext ServerModule for the SAPL Language Server that provides markdown
 * documentation support in content assist completions.
 */
public class SAPLServerModule extends ServerModule {

    @Override
    protected void configure() {
        super.configure();
        // Override the ContentAssistService binding to use our markdown-aware version
        bind(ContentAssistService.class).to(SAPLContentAssistService.class);
    }

}
