package io.sapl.grammar.ide;

import java.util.Collection;
import java.util.HashSet;

public final class SAPLContentProposalProviderHelper {

	final static String IMPORT_KEYWORD = "import";

	public static Collection<String> createImportProposals(String feature, final String policy, final int offset,
			final LibraryAttributeFinder attributeFinder) {
		feature = feature.toLowerCase();

		if (feature.equals("libsteps")) {
			// remove all text after the cursor
			String importStatement = policy.substring(0, offset);
			// find last import statement and remove all text before it
			int beginning = importStatement.lastIndexOf(IMPORT_KEYWORD);
			importStatement = importStatement.substring(beginning + 1);
			// remove the import keyword
			importStatement = importStatement.substring(IMPORT_KEYWORD.length());
			// remove all new lines
			importStatement = importStatement.replace("\n", " ").trim();
			// remove all spaces we're only interested in statement e.g. "clock.now"
			importStatement = importStatement.replace(" ", "");
			// look up proposals
			return attributeFinder.GetAvailableAttributes(importStatement);
		} else if (feature.equals("libalias")) {
			return new HashSet<String>();
		} else {
			return new HashSet<String>();
		}
	}
}
