package io.sapl.grammar.tests;

import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Value;

/*package*/ class BasicValueHelper {

	private BasicValueHelper() {
		throw new UnsupportedOperationException("Static utility class");
	}

	static BasicValue basicValueFrom(Value value) {
		BasicValue basicValue = SaplFactory.eINSTANCE.createBasicValue();
		basicValue.setValue(value);
		return basicValue;
	}
}
