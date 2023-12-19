package io.sapl.springdatar2dbc.database;

import lombok.AllArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;

@AllArgsConstructor
@SuppressWarnings({ "EI_EXPOSE_REP2", "NP_NONNULL_RETURN_VIOLATION" })
public class MethodInvocationForTesting implements MethodInvocation {

    String              methodName;
    ArrayList<Class<?>> argumentClasses;
    ArrayList<Object>   argumentValues;
    Object              proceedObject;

    @SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
    @Override
    public Method getMethod() {
        try {
            return R2dbcPersonRepository.class.getMethod(methodName,
                    argumentClasses.toArray(new Class[argumentClasses.size()]));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
    @Override
    public Object[] getArguments() {
        return argumentValues.toArray(new Object[argumentValues.size()]);
    }

    @Override
    public Object proceed() {
        return proceedObject;
    }

    @Override
    public Object getThis() {
        return this;
    }

    @Override
    @SuppressWarnings("NP_NONNULL_RETURN_VIOLATION")
    public AccessibleObject getStaticPart() {
        return null;
    }
}
