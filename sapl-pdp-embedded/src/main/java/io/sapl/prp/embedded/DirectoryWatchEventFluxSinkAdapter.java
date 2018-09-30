package io.sapl.prp.embedded;

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
        sink.next("policy modification event");
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
