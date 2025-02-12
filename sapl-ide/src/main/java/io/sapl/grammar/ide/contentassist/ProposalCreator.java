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
package io.sapl.grammar.ide.contentassist;

import java.util.Optional;

import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ProposalCreator {

    public record Proposal(String proposal, String ctxPrefix, String documentation, String label, String kind) {}

    public static Proposal createSimpleEntry(String proposal, ContentAssistContext context) {
        return new Proposal(proposal, context.getPrefix(), proposal, null, ContentAssistEntry.KIND_VALUE);
    }

    public static Optional<Proposal> createNormalizedEntry(String proposal, String prefix, String ctxPrefix) {
        return createNormalizedEntry(proposal, prefix, ctxPrefix, null);
    }

    public static Optional<Proposal> createNormalizedEntry(String proposal, String prefix, String ctxPrefix,
            String documentation) {
        return normalize(proposal, prefix, ctxPrefix).map(normalizedProposal -> new Proposal(normalizedProposal,
                ctxPrefix, proposal, documentation, ContentAssistEntry.KIND_METHOD));
    }

    public static Optional<String> normalize(String proposal, String prefix, String ctxPrefix) {
        if (proposal.startsWith("<") && !prefix.startsWith("<")) {
            proposal = proposal.substring(1);
        }
        if (!proposal.startsWith(prefix) || !prefix.endsWith(ctxPrefix) || prefix.equals(proposal)) {
            return Optional.empty();
        } else {
            final var normalizedProposal = proposal.substring(prefix.length() - ctxPrefix.length());
            return Optional.of(normalizedProposal);
        }
    }

}
