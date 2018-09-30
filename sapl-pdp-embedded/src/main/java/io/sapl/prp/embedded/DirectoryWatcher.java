package io.sapl.prp.embedded;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

class DirectoryWatcher {

    private Path watchedDir;

    DirectoryWatcher(Path watchedDir) {
        this.watchedDir = watchedDir;
    }

    void watch(DirectoryWatchEventConsumer eventConsumer) {
        try {
            final WatchService watcher = FileSystems.getDefault().newWatchService();
            watchedDir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            while (! eventConsumer.isCanceled()) {
                WatchKey key;
                try {
                    key = watcher.take();
                }
                catch (InterruptedException x) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    eventConsumer.onEvent(event);

//                        WatchEvent<Path> ev = (WatchEvent<Path>)event;
//                        Path filename = ev.context();
//
//                        if (kind == ENTRY_CREATE)
//                            System.out.format("file %s created%n", filename);
//                        else if (kind == ENTRY_DELETE)
//                            System.out.format("file %s deleted%n", filename);
//                        else if (kind == ENTRY_MODIFY)
//                            System.out.format("file %s modified%n", filename);
                }

                // If the key is no longer valid, the directory is inaccessible.
                boolean valid = key.reset();
                if (! valid) {
                    eventConsumer.cancel();
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        eventConsumer.onComplete();
    }
}
