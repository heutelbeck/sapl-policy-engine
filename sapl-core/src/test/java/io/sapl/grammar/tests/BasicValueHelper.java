package io.sapl.grammar.tests;

import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Value;
import lombok.experimental.UtilityClass;

@UtilityClass
class BasicValueHelper {

	static BasicValue basicValueFrom(Value value) {
		BasicValue basicValue = SaplFactory.eINSTANCE.createBasicValue();
		basicValue.setValue(value);
		return basicValue;
	}

}
