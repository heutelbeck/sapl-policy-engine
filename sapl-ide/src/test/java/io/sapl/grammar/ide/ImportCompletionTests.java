package io.sapl.grammar.ide;

import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

public class ImportCompletionTests extends CompletionTests {
	
	@Test
	public void testCompletion_AtTheBeginningImportStatement_ReturnsLibraries() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import ";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("clock", "filter", "standard", "time");
				assertProposalsSimple(expected, completionList);
			});
		});
	}
	
	@Test
	public void testCompletion_WithPartialLibrary_ReturnsLibrary() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import cl";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("clock");
				assertProposalsSimple(expected, completionList);
			});
		});
	}
	
	@Test
	public void testCompletion_WithFullLibrary_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import clock.";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("now", "ticker");
				assertProposalsSimple(expected, completionList);
			});
		});
	}
	
	@Test
	public void testCompletion_WithFullLibraryAndPartialFunction_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import clock.n";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("now");
				assertProposalsSimple(expected, completionList);
			});
		});
	}
	
	@Test
	public void testCompletion_WithFullLibraryAndPartialFunctionAndNewLinesInBetween_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import\nclock.\nn";
			it.setModel(policy);
			it.setLine(2);
			it.setColumn(1);
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("now");
				assertProposalsSimple(expected, completionList);
			});
		});
	}
	
	@Test
	public void testCompletion_WithPrecedingTextAndFullLibraryAndPartialFunction_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import clock.yesterday\nimport clock.n";
			String cursor = "import clock.n";
			it.setModel(policy);
			it.setLine(1);
			it.setColumn(cursor.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("now");
				assertProposalsSimple(expected, completionList);
			});
		});
	}
	
	@Test
	public void testCompletion_WithPrecedingAndSucceedingAndFullLibraryAndPartialFunction_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import clock.yesterday\nimport clock.n policy \"test policy\" deny";
			String cursor = "import clock.n";
			it.setModel(policy);
			it.setLine(1);
			it.setColumn(cursor.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("now");
				assertProposalsSimple(expected, completionList);
			});
		});
	}
}
