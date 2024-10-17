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
package io.sapl.grammar.ide.contentassist;

import java.util.Optional;

import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ProposalCreator {

    public static ContentAssistEntry createEntry(String proposal, String ctxPrefix, String label, String documentation,
            String kind) {
        final var entry = new ContentAssistEntry();
        entry.setProposal(proposal);
        entry.setPrefix(ctxPrefix);
        entry.setDocumentation(documentation);
        entry.setLabel(label);
        entry.setKind(kind);
        return entry;
    }

    public static Optional<ContentAssistEntry> createNormalizedEntry(String proposal, String prefix, String ctxPrefix) {
        return createNormalizedEntry(proposal, prefix, ctxPrefix, null);
    }

    public static Optional<ContentAssistEntry> createNormalizedEntry(String proposal, String prefix, String ctxPrefix,
            String documentation) {
        return normalize(proposal, prefix, ctxPrefix).map(normalizedProposal -> createEntry(normalizedProposal,
                ctxPrefix, proposal, documentation, ContentAssistEntry.KIND_METHOD));
    }

    public static Optional<String> normalize(String proposal, String prefix, String ctxPrefix) {
        if (proposal.startsWith("<") && !prefix.startsWith("<")) {
            proposal = proposal.substring(1);
        }
        if (!proposal.startsWith(prefix) || prefix.equals(proposal)) {
            log.trace("prefix: '{}' ctxPrefix: '{}': proposal: '{}' normalized: <empty> no proposal!", prefix,
                    ctxPrefix, proposal);
            return Optional.empty();
        } else {
            final var normalizedProposal = proposal.substring(Math.max(prefix.length(), ctxPrefix.length()),
                    proposal.length());
            log.trace("prefix: '{}' ctxPrefix: '{}': proposal: '{}' normalized: '{}'", prefix, ctxPrefix, proposal,
                    normalizedProposal);
            return Optional.of(normalizedProposal);
        }
    }

}
