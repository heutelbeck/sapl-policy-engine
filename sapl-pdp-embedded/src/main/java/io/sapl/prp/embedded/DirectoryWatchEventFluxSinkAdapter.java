package io.sapl.prp.embedded;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

import reactor.core.publisher.FluxSink;

public class DirectoryWatchEventFluxSinkAdapter implements DirectoryWatchEventConsumer {

    private FluxSink<String> sink;
    private boolean canceled;

    public void setSink(FluxSink<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(WatchEvent<?> event) {
        final WatchEvent<Path> ev = (WatchEvent<Path>)event;
        final Path filename = ev.context();
        if (filename.toString().endsWith(ResourcesPolicyRetrievalPoint.POLICY_FILE_EXTENSION)) {
            sink.next("policy modification event");
        }
    }

    @Override
    public void onComplete() {
        sink.complete();
    }

    @Override
    public void cancel() {
        canceled = true;
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }
}
