package io.sapl.prp.filesystem;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.util.filemonitoring.FileChangedEvent;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;

import static com.spotify.hamcrest.pojo.IsPojo.pojo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;


public class ImmutableFileIndexTest {

    @TempDir
    File folder;

    private static SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

    @Test
    @Disabled
    public void test_after_file_event() throws Exception {
        final boolean[] inconsistent = {true};

        for (int i = 1; i <= 5; i++) {
            new File(folder, "file" + i + ".sapl").createNewFile();
        }

        var indexMock = mock(ImmutableFileIndex.class, withSettings().useConstructor(folder.getPath(), interpreter));

        // WHEN

        // this will break the immutability of the index (the same mock will be returned
        // for each constructor call)
        try (MockedConstruction<ImmutableFileIndex> mocked = Mockito.mockConstruction(ImmutableFileIndex.class,
                (mock, context) -> {
                    doNothing().when(mock).load(any());
                    doNothing().when(mock).unload(any());
                    doCallRealMethod().when(mock).change(any());

                    when(mock.afterFileEvent(any())).thenCallRealMethod();
                    when(mock.getUpdateEvent()).thenCallRealMethod();
                })) {

            // DO
            for (File file : folder.listFiles()) {
                indexMock = indexMock.afterFileEvent(new FileCreatedEvent(file));
                indexMock = indexMock.afterFileEvent(new FileChangedEvent(file));
                indexMock = indexMock.afterFileEvent(new FileDeletedEvent(file));

                toggleInconsistent(inconsistent, indexMock);
            }
        }

        //        whenNew(ImmutableFileIndex.class).withAnyArguments().thenReturn(indexMock);

        // THEN
        verify(indexMock, times(10)).load(any());
        verify(indexMock, times(10)).unload(any());
        verify(indexMock, times(5)).change(any());
//        verifyNew(ImmutableFileIndex.class, times(15)).withArguments(any(ImmutableFileIndex.class));


        assertThat(indexMock.getUpdateEvent().getUpdates(), arrayContaining(pojo(PrpUpdateEvent.Update.class)
                .withProperty("type", is(Type.CONSISTENT))));
        assertThat(indexMock.getUpdateEvent().getUpdates(), arrayContaining(pojo(PrpUpdateEvent.Update.class)
                .withProperty("type", is(Type.INCONSISTENT))));
    }

    @Test
    public void test_internal_copy_constructor() throws Exception {
        var p1 = new File(folder, "policy1.sapl");
        FileUtils.writeStringToFile(p1, String
                        .format("policy \"%s\"\n" + "permit\n" + "    action == \"read\"\n" + "\n" + "\n" + "\n", "policy1"),
                Charset.defaultCharset());

        var p2 = new File(folder, "policy2.sapl");
        FileUtils.writeStringToFile(p2, String
                        .format("policy \"%s\"\n" + "permit\n" + "    action == \"read\"\n" + "\n" + "\n" + "\n", "policy1"),
                Charset.defaultCharset());

        var fileIndex = new ImmutableFileIndex(folder.getPath(), interpreter);


        assertThat(fileIndex, notNullValue());

        assertThat(Arrays.stream(fileIndex.getUpdateEvent().getUpdates())
                .anyMatch(update -> update.getType() == Type.PUBLISH), is(true));

        var newIndex = fileIndex.afterFileEvent(new FileCreatedEvent(new File("NOTFOUND")));

        // in case of collision published file is randomly selected depending on OS
        // behavior
        var fileToDelete = newIndex.pathToDocuments.get(p1.getAbsolutePath()).isPublished() ? p1 : p2;

        newIndex = fileIndex.afterFileEvent(new FileDeletedEvent(fileToDelete));

        assertThat(newIndex, notNullValue());
        assertThat(Arrays.stream(newIndex.getUpdateEvent().getUpdates())
                .anyMatch(update -> update.getType() == Type.UNPUBLISH), is(true));
    }

    private void toggleInconsistent(boolean[] inconsistent, ImmutableFileIndex indexMock) {
        when(indexMock.becameConsistentComparedTo(any())).thenReturn(!inconsistent[0]);
        when(indexMock.becameInconsistentComparedTo(any())).thenReturn(inconsistent[0]);

        inconsistent[0] = (!inconsistent[0]);
    }

    @Test
    public void should_not_throw_exception_when_watchdir_can_not_be_opened() throws Exception {
        File notADirectoryFile = new File(folder, "not_a_directory_file");

        var fileIndex = new ImmutableFileIndex(notADirectoryFile.getPath(), interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent, notNullValue());
        assertThat(updateEvent.getUpdates().length, not(is(0)));

        assertThat(updateEvent.getUpdates(), arrayContaining(pojo(PrpUpdateEvent.Update.class)
                .withProperty("type", is(Type.INCONSISTENT))));
    }

    @Test
    public void return_no_event_for_empty_policy_directory() {
        var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/empty", interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent, notNullValue());
        assertThat(updateEvent.getUpdates(), emptyArray());
    }

    @Test
    public void return_inconsistent_event_for_name_collision() {
        var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/namecollision", interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent, notNullValue());
        assertThat(updateEvent.getUpdates().length, not(is(0)));

        assertThat(Arrays.stream(updateEvent.getUpdates())
                .anyMatch(update -> update.getType() == Type.INCONSISTENT), is(true));
    }

    @Test
    public void return_inconsistent_event_for_invalid_document() {
        var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/invalid", interpreter);
        var updateEvent = fileIndex.getUpdateEvent();

        assertThat(updateEvent, notNullValue());
        assertThat(updateEvent.getUpdates().length, not(is(0)));

        assertThat(updateEvent.getUpdates(), arrayContaining(pojo(PrpUpdateEvent.Update.class)
                .withProperty("type", is(Type.INCONSISTENT))));
    }

}
