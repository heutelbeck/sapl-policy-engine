package io.sapl.util.filemonitoring;

import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.File;
import java.nio.file.NoSuchFileException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Timeout(3)
class FileMonitorUtilTest {

    @Test
    void return_valid_textfile_as_string() throws Exception {
        var validTextFile = new File("src/test/resources/policies/policy_1.sapl");
        var fileAsString = FileMonitorUtil.readFile(validTextFile);

        assertThat(fileAsString, not(emptyString()));
        assertThat(fileAsString, containsString("policy 1"));
    }

    @Test
    void throw_exception_when_reading_non_existent_file() throws Exception {
        var missingFile = new File("src/test/resources/not_existing.txt");

        assertThrows(NoSuchFileException.class, () -> {
            FileMonitorUtil.readFile(missingFile);
        });

    }

    @Test
    void resolve_home_folder_in_valid_path() {
        var props = System.getProperties();
        var homePath = String.format("%shome%sjohndoe", File.separator, File.separator);

        try (MockedStatic<FileMonitorUtil> mock = mockStatic(FileMonitorUtil.class)) {
            mock.when(FileMonitorUtil::getUserHomeProperty).thenReturn(homePath);
            mock.when(() -> FileMonitorUtil.resolveHomeFolderIfPresent(any())).thenCallRealMethod();

            var path = FileMonitorUtil.resolveHomeFolderIfPresent("~" + File.separator);

            assertThat(path, not(emptyString()));
            assertThat(path, containsString(homePath));

            path = FileMonitorUtil.resolveHomeFolderIfPresent("~/");
            assertThat(path, not(emptyString()));
            assertThat(path, containsString(homePath));
        }

    }

    @Test
    void resolve_home_folder_in_path_without_home() {
        var folder = File.separator + "opt" + File.separator;
        var path = FileMonitorUtil.resolveHomeFolderIfPresent(folder);
        assertThat(path, not(emptyString()));
        assertThat(path, is(folder));
    }

    @Test
    void return_no_event_for_non_existent_directory() throws Exception {
        Flux<FileEvent> monitorFlux = FileMonitorUtil.monitorDirectory("src/test/resources/not_existing_dir",
                __ -> true);

        StepVerifier.create(monitorFlux).expectNextCount(0L).thenCancel().verify();
        monitorFlux.take(1L).subscribe(System.out::println);
    }

    @Test
    void return_no_event_when_nothing_changes() throws Exception {
        Flux<FileEvent> monitorFlux = FileMonitorUtil.monitorDirectory("src/test/resources/policies", __ -> true);
        StepVerifier.create(monitorFlux).expectNextCount(0L).thenCancel().verify();
    }

    @Test
    void throw_exception_in_monitor_start() throws Exception {
        try (MockedConstruction<FileAlterationMonitor> mocked = Mockito.mockConstruction(FileAlterationMonitor.class,
                (mock, context) -> {
                    doThrow(new Exception()).when(mock).start();
                    // verify(mock, times(1)).start();
                })) {

            Flux<FileEvent> monitorFlux = FileMonitorUtil.monitorDirectory("~/", __ -> true);
            monitorFlux.take(1L).subscribe();
            assertThat(mocked.constructed().size(), is(1));
            verify(mocked.constructed().get(0), times(1)).start();
        }
    }

}
