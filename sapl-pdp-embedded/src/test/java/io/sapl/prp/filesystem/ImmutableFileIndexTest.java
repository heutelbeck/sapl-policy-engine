package io.sapl.prp.filesystem;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.util.filemonitoring.FileChangedEvent;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.powermock.api.mockito.PowerMockito.verifyNew;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ImmutableFileIndex.class})
public class ImmutableFileIndexTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static SAPLInterpreter interpreter = new DefaultSAPLInterpreter();


    @Test
    public void test_after_file_event() throws Exception {
        final boolean[] inconsistent = {true};

        for (int i = 1; i <= 5; i++) {
            folder.newFile("file" + i + ".sapl");
        }

        var indexMock = mock(ImmutableFileIndex.class,
                withSettings().useConstructor(folder.getRoot().getPath(), interpreter));


        //WHEN
        doNothing().when(indexMock).load(any());
        doNothing().when(indexMock).unload(any());

        when(indexMock.afterFileEvent(any())).thenCallRealMethod();
        when(indexMock.getUpdateEvent()).thenCallRealMethod();

        //this will break the immutability of the index (the same mock will be returned for each constructor call)
        whenNew(ImmutableFileIndex.class).withAnyArguments().thenReturn(indexMock);

        //DO
        for (File file : folder.getRoot().listFiles()) {
            indexMock = indexMock.afterFileEvent(new FileCreatedEvent(file));
            indexMock = indexMock.afterFileEvent(new FileChangedEvent(file));
            indexMock = indexMock.afterFileEvent(new FileDeletedEvent(file));

            toggleInconsistent(inconsistent, indexMock);
        }

        //THEN
        verify(indexMock, times(5)).load(any());
        verify(indexMock, times(5)).unload(any());
        verify(indexMock, times(5)).change(any());
        verifyNew(ImmutableFileIndex.class, times(15))
                .withArguments(any(ImmutableFileIndex.class));
        assertThat(indexMock.getUpdateEvent().getUpdates()).anyMatch(update -> update.getType() == Type.CONSISTENT);
        assertThat(indexMock.getUpdateEvent().getUpdates()).anyMatch(update -> update.getType() == Type.INCONSISTENT);
    }

    private void toggleInconsistent(boolean[] inconsistent, ImmutableFileIndex indexMock) {
        when(indexMock.becameConsistentComparedTo(any())).thenReturn(!inconsistent[0]);
        when(indexMock.becameInconsistentComparedTo(any())).thenReturn(inconsistent[0]);

        inconsistent[0] = (!inconsistent[0]);
    }

    @Test
    public void return_no_event_for_empty_policy_directory() {
        var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/empty", interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent).isNotNull();
        assertThat(updateEvent.getUpdates()).isEmpty();
    }

    @Test
    public void return_inconsistent_event_for_name_collision() {
        var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/namecollision",
                interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent).isNotNull();
        assertThat(updateEvent.getUpdates()).isNotEmpty();

        assertThat(updateEvent.getUpdates()).anySatisfy(update ->
                assertThat(update.getType()).isEqualTo(Type.INCONSISTENT));

    }

    @Test
    public void return_inconsistent_event_for_invalid_document() {
        var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/invalid",
                interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent).isNotNull();
        assertThat(updateEvent.getUpdates()).isNotEmpty();

        assertThat(updateEvent.getUpdates()).anySatisfy(update ->
                assertThat(update.getType()).isEqualTo(Type.INCONSISTENT));

    }

}
