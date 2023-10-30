package io.sapl.grammar.sapl.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import reactor.util.context.Context;

class ImportsUtilTests {

    @Test
    @SuppressWarnings("unchecked")
    void nullReturnsEmptyImportMap() {
        var ctx = Context.of("attributeCtx", mock(AttributeContext.class), "functionCtx", mock(FunctionContext.class));
        assertThat((Map<String, Object>) ImportsUtil.loadImportsIntoContext(null, ctx).get("imports")).isEmpty();
    }

}
