/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Collection;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import io.sapl.grammar.ide.AbstractSaplLanguageServerTest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class uses the xtext test classes to test auto-completion results.
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = SAPLIdeSpringTestConfiguration.class)
public class CompletionTests extends AbstractSaplLanguageServerTest {

	protected void assertProposalsSimple(final Collection<String> expectedProposals,
			final CompletionList completionList) {
		var actualMethods = completionList.getItems().stream().map(CompletionItem::getLabel)
				.collect(Collectors.toList());
		log.info("actual: {}", actualMethods);
		if (!actualMethods.containsAll(expectedProposals))
			throw new AssertionError("Expected: " + expectedProposals + " but got " + actualMethods);
	}

	protected void assertDoesNotContainProposals(final Collection<String> unwantedProposals,
			final CompletionList completionList) {
		Collection<CompletionItem> completionItems = completionList.getItems();
		Collection<String> availableProposals = completionItems.stream().map(CompletionItem::getLabel)
				.collect(Collectors.toSet());

		for (String unwantedProposal : unwantedProposals) {
			if (availableProposals.contains(unwantedProposal))
				throw new AssertionError(
						"Expected not to find " + unwantedProposal + " but found it in " + availableProposals);
		}
	}

}
