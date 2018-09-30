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

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
                final WatchKey key = watcher.take();
                handleWatchKey(key, eventConsumer);
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage());
        }
        eventConsumer.onComplete();
    }

    private void handleWatchKey(WatchKey key, DirectoryWatchEventConsumer eventConsumer) {
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == OVERFLOW) {
                continue;
            }
            eventConsumer.onEvent(event);
        }

        // If the key is no longer valid, the directory is inaccessible.
        boolean valid = key.reset();
        if (! valid) {
            eventConsumer.cancel();
        }
    }
}
