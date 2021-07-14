package io.sapl.grammar.ide;

import java.util.Collection;

public interface LibraryAttributeFinder {
	Collection<String> GetAvailableAttributes(String identifier);
}
