package io.sapl.pdp.config;

import io.sapl.grammar.sapl.impl.PermitUnlessDenyCombiningAlgorithmImplCustom;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class FixedFunctionsAndAttributesPDPConfigurationProviderTest {

    @Test
    void do_test() {
        var source = new FileSystemVariablesAndCombinatorSource("src/test/resources/policies");
        var attrCtx = new AnnotationAttributeContext();
        var funcCtx = new AnnotationFunctionContext();
        var provider = new FixedFunctionsAndAttributesPDPConfigurationProvider(attrCtx, funcCtx, source);
        var config = provider.pdpConfiguration().blockFirst();
        provider.dispose();


        assertThat(config.getDocumentsCombinator() instanceof PermitUnlessDenyCombiningAlgorithmImplCustom, is(true));
        assertThat(config.getPdpScopedEvaluationContext().getAttributeCtx(), is(attrCtx));
        assertThat(config.getPdpScopedEvaluationContext().getFunctionCtx(), is(funcCtx));
        assertThat(config.isValid(), is(true));
    }
}
