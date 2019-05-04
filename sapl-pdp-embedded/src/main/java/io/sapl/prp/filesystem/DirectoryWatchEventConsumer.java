package io.sapl.prp.filesystem;

import java.nio.file.WatchEvent;

public interface DirectoryWatchEventConsumer<T> {

    void onEvent(WatchEvent<T> event);

    void onComplete();

    void cancel();

    boolean isCanceled();
}
