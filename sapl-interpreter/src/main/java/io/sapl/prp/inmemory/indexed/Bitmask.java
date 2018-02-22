package io.sapl.prp.inmemory.indexed;

import java.util.BitSet;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

public class Bitmask {

	private final BitSet impl;

	public Bitmask() {
		impl = new BitSet();
	}

	public Bitmask(final Bitmask mask) {
		impl = (BitSet) mask.impl.clone();
	}

	public void and(Bitmask mask) {
		impl.and(mask.impl);
	}

	public void andNot(Bitmask mask) {
		impl.andNot(mask.impl);
	}

	public void clear(int bitIndex) {
		impl.clear(bitIndex);
	}

	public void flip(int fromIndex, int toIndex) {
		impl.flip(fromIndex, toIndex);
	}

	public boolean intersects(Bitmask mask) {
		return impl.intersects(mask.impl);
	}

	public void or(Bitmask mask) {
		impl.or(mask.impl);
	}

	public void set(int bitIndex) {
		impl.set(bitIndex);
	}

	public void set(int fromIndex, int toIndex) {
		impl.set(fromIndex, toIndex);
	}

	void forEachSetBit(final Consumer<Integer> action) {
		Preconditions.checkNotNull(action);
		for (int i = impl.nextSetBit(0); i >= 0; i = impl.nextSetBit(i + 1)) {
			if (i == Integer.MAX_VALUE) {
				break;
			}
			action.accept(i);
		}
	}
}
