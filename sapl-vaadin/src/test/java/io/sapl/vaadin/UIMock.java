package io.sapl.vaadin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;

public class UIMock {
    public static UI getMockedUI() {
        UI mockedUI = mock(UI.class);
        doAnswer(invocationOnMock -> {
            var f = (Command) invocationOnMock.getArguments()[0];
            f.execute();
            return null;
        }).when(mockedUI).access(any(Command.class));
        return mockedUI;
    }
}
