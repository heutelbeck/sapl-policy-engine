package io.sapl.prp.resources;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourcesPrpUpdateEventSourceTest {

    @Test
    void do_stuff() {
        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, null));

        var source = new ResourcesPrpUpdateEventSource("", new DefaultSAPLInterpreter());
        assertThat(source, notNullValue());

        source = new ResourcesPrpUpdateEventSource(this.getClass(), "/policies", new DefaultSAPLInterpreter());
        assertThat(source, notNullValue());

        source.dispose();
    }

    @Test
    void readPoliciesFromDirectory() {
        var source = new ResourcesPrpUpdateEventSource("/policies", new DefaultSAPLInterpreter());
        var update = source.getUpdates().blockFirst();

        assertThat(update, notNullValue());
        assertThat(update.getUpdates().length, is(2));

        assertThrows(RuntimeException.class, () -> new ResourcesPrpUpdateEventSource("/NON-EXISTING-PATH", new DefaultSAPLInterpreter()));

        assertThrows(PolicyEvaluationException.class, () -> new ResourcesPrpUpdateEventSource("/it/invalid", new DefaultSAPLInterpreter()));


    }

    @Test
    @Disabled
    void readPoliciesFromJarTest() {
        var source = new ResourcesPrpUpdateEventSource(this.getClass(), "jar:file:/test.jar!policies", new DefaultSAPLInterpreter());
    }

}
