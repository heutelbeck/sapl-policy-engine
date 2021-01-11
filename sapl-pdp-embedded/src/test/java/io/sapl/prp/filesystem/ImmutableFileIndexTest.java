package io.sapl.prp.filesystem;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent.Type;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

public class ImmutableFileIndexTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(3);


    @Test
    public void return_no_event_for_empty_policy_directory() {
        var interpreter = new DefaultSAPLInterpreter();
        var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/empty", interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent).isNotNull();
        assertThat(updateEvent.getUpdates()).isEmpty();
    }

    @Test
    public void return_inconsistent_event_for_name_collision() {
        var interpreter = new DefaultSAPLInterpreter();
        var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/namecollision", interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent).isNotNull();
        assertThat(updateEvent.getUpdates()).isNotEmpty();

        assertThat(updateEvent.getUpdates()).anySatisfy(update ->
                assertThat(update.getType()).isEqualTo(Type.INCONSISTENT));

    }

    @Test
    public void return_inconsistent_event_for_invalid_document() {
        var interpreter = new DefaultSAPLInterpreter();
        var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/invalid", interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent).isNotNull();
        assertThat(updateEvent.getUpdates()).isNotEmpty();

        assertThat(updateEvent.getUpdates()).anySatisfy(update ->
                assertThat(update.getType()).isEqualTo(Type.INCONSISTENT));

    }

}
