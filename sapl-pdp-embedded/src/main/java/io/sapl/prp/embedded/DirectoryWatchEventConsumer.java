package io.sapl.prp.embedded;

import java.nio.file.WatchEvent;

public interface DirectoryWatchEventConsumer {

    void onEvent(WatchEvent<?> event);

    void onComplete();

    void cancel();

    boolean isCanceled();
}
