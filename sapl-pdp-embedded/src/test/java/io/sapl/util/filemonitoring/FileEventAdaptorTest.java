package io.sapl.util.filemonitoring;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;

import java.io.File;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileEventAdaptorTest {

    @Test
    void doTest() {
        @SuppressWarnings("unchecked")
        var sinkMock = (FluxSink<FileEvent>) mock(FluxSink.class);
        when(sinkMock.next(any())).thenReturn(null);

        var fileMock = mock(File.class);

        var eventAdaptor = new FileEventAdaptor(sinkMock);
        eventAdaptor.onFileCreate(fileMock);
        eventAdaptor.onFileChange(fileMock);
        eventAdaptor.onFileDelete(fileMock);

        verify(sinkMock, times(3)).next(any());
    }

}
