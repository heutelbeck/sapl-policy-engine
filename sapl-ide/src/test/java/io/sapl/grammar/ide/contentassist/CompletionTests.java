/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.grammar.ide.AbstractSaplLanguageServerTest;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;

import java.util.Collection;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

/**
 * This class uses the xtext test classes to test auto completion results.
 */
public class CompletionTests extends AbstractSaplLanguageServerTest {

	protected void assertProposalsSimple(final Collection<String> expectedProposals,
			final CompletionList completionList) {
		var actualMethods = completionList.getItems().stream().map(CompletionItem::getLabel)
				.collect(Collectors.toList());
		assertThat(actualMethods.containsAll(expectedProposals), is(true));
	}

	protected void assertDoesNotContainProposals(final Collection<String> unwantedProposals,
			final CompletionList completionList) {
		Collection<CompletionItem> completionItems = completionList.getItems();
		Collection<String> availableProposals = completionItems.stream().map(CompletionItem::getLabel)
				.collect(Collectors.toSet());

		for (String unwantedProposal : unwantedProposals) {
			assertThat(availableProposals, not(hasItem(unwantedProposal)));
		}
	}

}
