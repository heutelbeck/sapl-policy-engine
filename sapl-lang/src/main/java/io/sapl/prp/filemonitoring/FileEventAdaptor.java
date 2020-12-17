package io.sapl.prp.filemonitoring;

import io.sapl.prp.PrpUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import reactor.core.publisher.FluxSink;

import java.io.File;

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
