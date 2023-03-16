package io.sapl.spring.constraints;

import static com.spotify.hamcrest.optional.OptionalMatchers.emptyOptional;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class BlockingPostEnforceConstraintHandlerBundleTests {

	@Test
	void when_filterOptionalFalse_then_returnsEmpty() {
		var filter = (Predicate<Object>) o -> false;
		// @formatter:off
		var sut    = new BlockingPostEnforceConstraintHandlerBundle<Optional<String>>(
							() -> {},__ -> {}, x -> x, __ -> {}, x -> x, filter);
		// @formatter:on
		var result = sut.handleAllOnNextConstraints(Optional.of("Hello"));
		assertThat(result, is(emptyOptional()));
	}

	@Test
	void when_filterOptionalTrue_then_returnsOriginal() {
		var filter = (Predicate<Object>) o -> true;
		// @formatter:off
		var sut    = new BlockingPostEnforceConstraintHandlerBundle<Optional<String>>(
							() -> {},__ -> {}, x -> x, __ -> {}, x -> x, filter);
		// @formatter:on
		var result = sut.handleAllOnNextConstraints(Optional.of("Hello"));
		assertThat(result, is(optionalWithValue(is("Hello"))));

	}

	@Test
	void when_filterList_then_elementsRemoved() {
		var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
		// @formatter:off
		var sut    = new BlockingPostEnforceConstraintHandlerBundle<List<String>>(
							() -> {},__ -> {}, x -> x, __ -> {}, x -> x, filter);
		// @formatter:on
		var result = sut.handleAllOnNextConstraints(List.of("Alice", "Bob", "Ada", "Adam", "Donald"));
		assertThat(result, contains("Alice", "Ada", "Adam"));
	}

	@Test
	void when_filterArray_then_elementsRemoved() {
		var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
		// @formatter:off
		var sut    = new BlockingPostEnforceConstraintHandlerBundle<String[]>(
							() -> {},__ -> {}, x -> x, __ -> {}, x -> x, filter);
		// @formatter:on
		var result = sut.handleAllOnNextConstraints(new String[] { "Alice", "Bob", "Ada", "Adam", "Donald" });
		assertThat(result, arrayContaining("Alice", "Ada", "Adam"));
	}

	@Test
	void when_filterSet_then_elementsRemoved() {
		var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
		// @formatter:off
		var sut    = new BlockingPostEnforceConstraintHandlerBundle<Set<String>>(
							() -> {},__ -> {}, x -> x, __ -> {}, x -> x, filter);
		// @formatter:on
		var result = sut.handleAllOnNextConstraints(Set.of("Alice", "Bob", "Ada", "Adam", "Donald"));
		assertThat(result, containsInAnyOrder("Alice", "Ada", "Adam"));
	}

	@Test
	void when_filterNonContainerType_then_null() {
		var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
		// @formatter:off
		var sut    = new BlockingPostEnforceConstraintHandlerBundle<String>(
							() -> {},__ -> {}, x -> x, __ -> {}, x -> x, filter);
		// @formatter:on
		var result = sut.handleAllOnNextConstraints("Bob");
		assertThat(result, is(nullValue()));
	}

	@Test
	void when_notFilteredNonContainerType_then_original() {
		var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
		// @formatter:off
		var sut    = new BlockingPostEnforceConstraintHandlerBundle<String>(
							() -> {},__ -> {}, x -> x, __ -> {}, x -> x, filter);
		// @formatter:on
		var result = sut.handleAllOnNextConstraints("Alice");
		assertThat(result, is("Alice"));
	}

}
