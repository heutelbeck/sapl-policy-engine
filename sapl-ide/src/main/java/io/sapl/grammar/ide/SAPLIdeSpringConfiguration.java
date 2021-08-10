package io.sapl.grammar.ide;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.grammar.ide.contentassist.DefaultLibraryAttributeFinder;
import io.sapl.grammar.ide.contentassist.LibraryAttributeFinder;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pip.ClockPolicyInformationPoint;
import lombok.SneakyThrows;

@Configuration
public class SAPLIdeSpringConfiguration {

	@Bean
	public LibraryAttributeFinder libraryAttributeFinder() {
		return new DefaultLibraryAttributeFinder();
	}
	
	@Bean 
	@SneakyThrows
	@ConditionalOnMissingBean
	public FunctionContext functionContext() {
		FunctionContext context = new AnnotationFunctionContext();
		context.loadLibrary(new FilterFunctionLibrary());
		context.loadLibrary(new StandardFunctionLibrary());
		context.loadLibrary(new TemporalFunctionLibrary());
		return context;
	}
	
	@Bean 
	@SneakyThrows
	@ConditionalOnMissingBean
	public AttributeContext attributeContext() {
		AnnotationAttributeContext context = new AnnotationAttributeContext();
		context.loadPolicyInformationPoint(new ClockPolicyInformationPoint());
		return context;
	}
}
