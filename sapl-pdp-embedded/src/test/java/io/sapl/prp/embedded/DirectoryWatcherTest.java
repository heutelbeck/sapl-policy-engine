package io.sapl.prp.embedded;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.concurrent.CountDownLatch;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class DirectoryWatcherTest {

    /**
     * Run this test and create, modify or delete files under the directory target/test-classes/policies.
     * To terminate the test, add a file called "stop.sapl" or delete the whole directory.
     */
    @Test @Ignore
    public void watchPoliciesDirectory() {
        try {
            final PathMatchingResourcePatternResolver pm = new PathMatchingResourcePatternResolver();
            final Resource configFile = pm.getResource("classpath:policies/pdp.json");
            final URI configFileURI = configFile.getURI();
            final Path watchDir = Paths.get(configFileURI).getParent();
            final DirectoryWatcher watcher = new DirectoryWatcher(watchDir);
            final CountDownLatch cdl = new CountDownLatch(1);
            watcher.watch(new DirectoryWatchEventConsumer<String>() {

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
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
