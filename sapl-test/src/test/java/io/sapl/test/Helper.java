package io.sapl.test;

import static org.mockito.Mockito.mock;

import java.util.List;
import org.eclipse.emf.common.util.EList;
import org.mockito.AdditionalAnswers;

public class Helper {
    public static <T> EList<T> mockEList(List<T> delegate) {
        return mock(EList.class, AdditionalAnswers.delegatesTo(delegate));
    }
}
