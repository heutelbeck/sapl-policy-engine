package io.sapl.prp.resources;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent.Update;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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

        source = new ResourcesPrpUpdateEventSource("/policies", new DefaultSAPLInterpreter());
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
    void readPoliciesFromJarTest() throws Exception {
        //        var source = new ResourcesPrpUpdateEventSource(this.getClass(),
        //                "jar:file:C:/Users/mlbau/IdeaProjects/sapl-policy-engine/sapl-pdp-embedded/src/test/resources/policies/policies.jar!/policies", new DefaultSAPLInterpreter());

        var absolutePath = "jar:file:C:/Users/mlbau/IdeaProjects/sapl-policy-engine/sapl-pdp-embedded/src/test/resources/policies/policies.jar!/policies";
        //        var relativePath = "jar:file:/policies.jar!/";

        var source = new ResourcesPrpUpdateEventSource(absolutePath, new DefaultSAPLInterpreter());

        var update = source.getUpdates().blockFirst();

        assertThat(update, notNullValue());
        assertThat(update.getUpdates().length, is(2));

        Arrays.stream(update.getUpdates())
                .map(Update::getRawDocument)
                .forEach(System.out::println);
    }


}
