package io.sapl.interpreter.pip;

import io.sapl.api.validation.Array;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ValidationTypes {
	static final Class<?>[] VALIDATION_ANNOTATION_TYPES = { Number.class, Int.class, Long.class, Bool.class, Text.class,
			Array.class, JsonObject.class };
}
