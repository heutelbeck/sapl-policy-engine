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
package io.sapl.grammar.ide;

import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator;
import org.eclipse.xtext.ide.server.contentassist.ContentAssistService;

import com.google.inject.Binder;

import io.sapl.grammar.ide.contentassist.SAPLContentAssistService;
import io.sapl.grammar.ide.contentassist.SAPLContentProposalProvider;
import io.sapl.grammar.ide.highlighting.SAPLSemanticHighlightingCalculator;

/**
 * Use this class to register IDE components.
 */
public class SAPLIdeModule extends AbstractSAPLIdeModule {

    @Override
    public void configure(Binder binder) {
        super.configure(binder);
        binder.bind(ContentAssistService.class).to(SAPLContentAssistService.class);
    }

    /**
     * @return the IdeContentProposalProvider
     */
    public Class<? extends IdeContentProposalProvider> bindIdeContentProposalProvider() {
        return SAPLContentProposalProvider.class;
    }

    /**
     * @return the ISemanticHighlightingCalculator for LSP semantic tokens
     */
    public Class<? extends ISemanticHighlightingCalculator> bindISemanticHighlightingCalculator() {
        return SAPLSemanticHighlightingCalculator.class;
    }

}
