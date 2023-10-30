package io.sapl.interpreter.pip;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorUtil {

    public static Val causeOrMessage(Throwable t) {
        var cause = t.getCause();
        if (cause != null)
            return Val.error(cause.getMessage());
        return Val.error(t.getMessage());
    }
}
