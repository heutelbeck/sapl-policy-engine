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
