package io.sapl.prp.filesystem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class FileSystemPrpUpdateEventSourceTest {

    @Test
    void testProcessFileEvent() {
        var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/empty", new DefaultSAPLInterpreter());

        try (MockedConstruction<ImmutableFileIndex> mocked = Mockito.mockConstruction(ImmutableFileIndex.class,
                (mock, context) -> {
                    when(mock.afterFileEvent(any())).thenReturn(mock);
                    when(mock.getUpdateEvent()).thenReturn(new PrpUpdateEvent(Collections.emptyList()));
                })) {

            try (MockedStatic<FileMonitorUtil> mock = mockStatic(FileMonitorUtil.class)) {
                mock.when(() -> FileMonitorUtil.monitorDirectory(any(), any()))
                        .thenReturn(Flux
                                .just(new FileCreatedEvent(null), new FileDeletedEvent(null)));

                var updates = source.getUpdates();
                StepVerifier.create(updates)
                        .expectNextCount(2L)
                        .thenCancel().verify();


                mock.verify(() -> FileMonitorUtil.monitorDirectory(any(), any()), times(1));
            }
        }

        source.dispose();
    }
}
