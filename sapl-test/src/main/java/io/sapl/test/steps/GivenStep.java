package io.sapl.test.steps;

import java.time.Duration;
import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.FunctionCall;
import io.sapl.test.mocking.FunctionParameters;
import io.sapl.test.verification.TimesCalledVerification;

/**
 * First Step in charge of registering mock values, ... . 
 * Next Step available : {@link WhenStep} or again a {@link GivenStep} -> therefore returning composite {@link GivenOrWhenStep}
 */
public interface GivenStep {
	
		/**
		 * Mock the return value of a Function in the SAPL policy always with this {@link Val}
		 * @param importName the reference in the SAPL policy to the function
		 * @param returns the mocked return value
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
		 */
        GivenOrWhenStep givenFunction(String importName, Val returns);
 
		/**
		 * Mock the return value of a Function in the SAPL policy
		 * @param importName the reference in the SAPL policy to the function
		 * @param returns the mocked return value
		 * @param verification verification for this mocking. See {@link MockingVerifications}
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
		 */
        GivenOrWhenStep givenFunction(String importName, Val returns, TimesCalledVerification verification);
        
		/**
		 * Mock the return value of a Function in the SAPL policy. Implicit verification, that this mocked return value has been returned.
		 * @param importName the reference in the SAPL policy to the function
		 * @param returns the mocked return value
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
		 */
        GivenOrWhenStep givenFunctionOnce(String importName, Val returns);
        
        
        /**
         * Mock the return value of a Function in the SAPL policy
         * @param importName the reference in the SAPL policy to the function
         * @param returns a sequence of {@link Val} to be returned by this function
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
         */
        GivenOrWhenStep givenFunctionOnce(String importName, Val... returns);
        
        
		/**
		 * Mock the return value of a Function in the SAPL policy. 
		 * With every call of this method you register a mocked return value for this combination of parameters of the function call.
		 * Ordering matters. The first matching parameters-Matcher's value will be returned 
		 * Example:
         * <pre>
         * {@code
		 *	.givenFunction("time.dayOfWeekFrom", Val.of("MONDAY"), whenParameters(argThat(Val.of(0)), anyVal()))
		 *	.givenFunction("time.dayOfWeekFrom", Val.of("TUESDAY"),	whenParameters(argThat(Val.of(0)), argThat(Val.of("foo"))))
		 * }
		 * </pre>
		 * 
		 * 
		 * @param importName the reference in the SAPL policy to the function
		 * @param returns the mocked return value
		 * @param parameters only return the specified {@link Val} if the parameters of the call to the function are equal to the Val's specified here. See {@link FunctionParameters#whenParameters(ParameterMatcher...)}
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
		 */
        GivenOrWhenStep givenFunction(String importName, Val returns, FunctionParameters parameters);
        
        
		/**
		 * Mock the return value of a Function in the SAPL policy. 
		 * With every call of this method you register a mocked return value for this combination of parameters of the function call.
		 * Ordering matters. The first matching parameters-Matcher's value will be returned 
		 * Example:
         * <pre>
         * {@code
		 *	.givenFunction("time.dayOfWeekFrom", Val.of("MONDAY"), whenParameters(argThat(Val.of(0)), anyVal()), times(3))
		 *	.givenFunction("time.dayOfWeekFrom", Val.of("TUESDAY"),	whenParameters(argThat(Val.of(0)), argThat(Val.of("foo"))), times(3))
		 * }
		 * </pre>
		 * 
		 * 
		 * @param importName the reference in the SAPL policy to the function
		 * @param returns the mocked return value
		 * @param parameters only return the specified {@link Val} if the parameters of the call to the function are equal to the Val's specified here. See {@link FunctionParameters#whenParameters(ParameterMatcher...)}
		 * @param verification verification for this mocking. See {@link MockingVerifications}
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
		 */
        GivenOrWhenStep givenFunction(String importName, Val returns, FunctionParameters parameters, TimesCalledVerification verification);

        
        /**
         * Mock the return value of a Function in the SAPL policy
         * 
         * You can apply some complex logic in this lambda to return a {@link Val} dependent on the function parameter values
         * Parameter to this Lambda-Expression is a {@link FunctionCall} representing the call of your function. 
         * You can access the parameter values via this object.
         * Example:
         * <pre>
         * {@code
         * .givenFunction("time.dayOfWeekFrom", (FunctionCall call) -> {
		 *
		 *		Val param1 = call.getArgument(0);
		 *		
		 *		if(param1.equals(Val.of("foo"))) {
		 *			return Val.of("bar");
		 *		} else {
		 *			return Val.of("xyz");
		 *		}
		 *	})
		 * }
		 * </pre>
         * 
         * @param importName the reference in the SAPL policy to the function
         * @param returns  a {@link Val} to be returned by the function 
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
         */
        GivenOrWhenStep givenFunction(String importName, Function<FunctionCall, Val> returns);
        
        
        /**
         * Mock the return value of a Function in the SAPL policy
         * 
         * You can apply some complex logic in this lambda to return a {@link Val} dependent on the function parameter values
         * Parameter to this Lambda-Expression is a {@link FunctionCall} representing the call of your function. 
         * You can access the parameter values via this object.
         * Example:
         * <pre>
         * {@code
         * .givenFunction("time.dayOfWeekFrom", (FunctionCall call) -> {
		 *
		 *		Val param1 = call.getArgument(0);
		 *		
		 *		if(param1.equals(Val.of("foo"))) {
		 *			return Val.of("bar");
		 *		} else {
		 *			return Val.of("xyz");
		 *		}
		 *	})
		 * }
		 * </pre>
         * 
         * @param importName the reference in the SAPL policy to the function
         * @param returns  a {@link Val} to be returned by the function
         * @param verification verification for this mocking. See {@link MockingVerifications}
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
         */
        GivenOrWhenStep givenFunction(String importName, Function<FunctionCall, Val> returns, TimesCalledVerification verification);
        
		/**
		 * Mock the return value of a PIP in the SAPL policy
		 * @param importName the reference in the SAPL policy to the PIP
		 * @param returns the mocked return value
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
		 */
        GivenOrWhenStep givenAttribute(String importName, Val... returns);
        
		/**
		 * Mock the return value of a PIP in the SAPL policy
		 * @param importName the reference in the SAPL policy to the PIP
		 * @param timing the the duration between emitting every return value
		 * @param returns the mocked return value
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
		 */
        GivenOrWhenStep givenAttribute(String importName, Duration timing, Val... returns);
        
		/**
		 * Mock the return value of a PIP in the SAPL policy. With this method you mark this PIP to be mocked. 
		 * Specify the mocked return value(s) at the Expect-Step for example via {@link ExpectStep#thenAttribute(String, Val)} 
		 * @param importName the reference in the SAPL policy to the PIP
		 * @param returns the mocked return value
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
		 */
        GivenOrWhenStep givenAttribute(String importName);
        
        /**
         * Allow control of virtual time for time-based streams
         * @return {@link GivenOrWhenStep} to define another {@link GivenStep} or go to the {@link WhenStep}
         */
        GivenOrWhenStep withVirtualTime();
}
