/**
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
package io.sapl.grammar.ide;

import java.util.Collection;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.xtext.testing.TestCompletionConfiguration;

/**
 * This class uses the xtext test classes to test auto completion results.
 * 
 */
public class CompletionTests extends AbstractSaplLanguageServerTest {

	protected String createCompletionString(Collection<String> items, TestCompletionConfiguration config) {
		StringBuilder builder = new StringBuilder();

		for (var item : items) {
			String index = String.format("[%d, %d]", config.getLine(), config.getColumn());
			String fullIndex = String.format("[%s .. %s]", index, index);
			builder.append(String.format("%s -> %s %s\n", item, item, fullIndex));
		}

		return builder.toString();
	}
	
	protected void assertProposalsSimple(Collection<String> expectedProposals, CompletionList completionList) {
		Collection<CompletionItem> completionItems = completionList.getItems();
		String actualProposalStr = completionItems.stream().map(CompletionItem::getLabel).collect(Collectors.joining("\n"));
		String expectedProposalStr = String.join("\n", expectedProposals);
		assertEquals(expectedProposalStr, actualProposalStr);
	}
}
