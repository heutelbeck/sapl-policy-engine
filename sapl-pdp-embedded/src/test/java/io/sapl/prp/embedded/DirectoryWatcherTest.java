package io.sapl.prp.embedded;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Slf4j
public class DirectoryWatcherTest {

    /**
     * Run this test and create, modify or delete files under the directory target/test-classes/policies.
     * To terminate the test, add a file called "stop.sapl" or delete the whole directory.
     */
    // @Test
    public void watchPoliciesDirectory() {
        try {
            final PathMatchingResourcePatternResolver pm = new PathMatchingResourcePatternResolver();
            final Resource configFile = pm.getResource("classpath:policies/pdp.json");
            final URI configFileURI = configFile.getURI();
            final Path watchDir = Paths.get(configFileURI).getParent();
            final DirectoryWatcher watcher = new DirectoryWatcher(watchDir);
            final CountDownLatch cdl = new CountDownLatch(1);
            watcher.watch(new DirectoryWatchEventConsumer() {

                private boolean isCanceled;

                @Override
                public void onEvent(WatchEvent<?> event) {
                    System.out.println("policy modification event");
                    WatchEvent<Path> ev = (WatchEvent<Path>)event;
                    Path filename = ev.context();
                    if (filename.toString().equals("stop.sapl")) {
                        cancel();
                    }
                }

                @Override
                public void onComplete() {
                    cdl.countDown();
                }

                @Override
                public void cancel() {
                    isCanceled = true;
                }

                @Override
                public boolean isCanceled() {
                    return isCanceled;
                }
            });
            cdl.await();
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage());
        }
    }

    @Test
    public void handleWatchKey() {
        // given
        final WatchEvent createEvent = mock(WatchEvent.class);
        when(createEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_CREATE);
        final WatchEvent modifyEvent = mock(WatchEvent.class);
        when(modifyEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_MODIFY);
        final WatchEvent deleteEvent = mock(WatchEvent.class);
        when(deleteEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_DELETE);
        final WatchEvent overflowEvent = mock(WatchEvent.class);
        when(overflowEvent.kind()).thenReturn(StandardWatchEventKinds.OVERFLOW);

        final WatchKey watchKey = mock(WatchKey.class);
        when(watchKey.pollEvents()).thenReturn(Arrays.asList(createEvent, modifyEvent, deleteEvent, overflowEvent));
        when(watchKey.isValid()).thenReturn(false);

        final DirectoryWatchEventConsumer eventConsumer = spy(DirectoryWatchEventConsumer.class);

        // when
        new DirectoryWatcher(null).handleWatchKey(watchKey, eventConsumer);

        // then
        verify(eventConsumer, times(3)).onEvent(any(WatchEvent.class));
        verify(eventConsumer, times(1)).cancel();

    }
}
