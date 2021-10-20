package io.sapl.spring.constraints.api;

public interface HasPriority extends Comparable<HasPriority> {
	default int getPriority() {
		return Integer.MIN_VALUE;
	}

	default int compareTo(HasPriority other) {
		return Integer.compare(getPriority(), other.getPriority());
	}
}