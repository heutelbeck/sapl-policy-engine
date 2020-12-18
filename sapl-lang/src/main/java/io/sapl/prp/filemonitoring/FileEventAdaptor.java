package io.sapl.prp.filemonitoring;

import java.io.File;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.FluxSink;

@RequiredArgsConstructor
public class FileEventAdaptor extends FileAlterationListenerAdaptor {

    private final FluxSink<FileEvent> emitter;

    @Override
    public void onFileCreate(File file) {
        emitter.next(new FileCreatedEvent(file));
    }

    @Override
    public void onFileDelete(File file) {
        emitter.next(new FileDeletedEvent(file));
    }

    @Override
    public void onFileChange(File file) {
        emitter.next(new FileChangedEvent(file));
    }
}
