package io.sapl.spring.config;

import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.NonNull;

import lombok.extern.slf4j.Slf4j;

/**
 * Dynamically determines which imports to include using the
 * {@link EnableSaplMethodSecurity} annotation.
 */
@Slf4j
final class SaplMethodSecuritySelector implements ImportSelector {

	private final ImportSelector autoProxy = new AutoProxyRegistrarSelector();

	@Override
	public String[] selectImports(@NonNull AnnotationMetadata importMetadata) {
		if (!importMetadata.hasAnnotation(EnableSaplMethodSecurity.class.getName())) {
			return new String[0];
		}
		log.debug("Blocking SAPL method security activated.");
		var imports = new ArrayList<>(Arrays.asList(this.autoProxy.selectImports(importMetadata)));
		imports.add(SaplMethodSecurityConfiguration.class.getName());
		return imports.toArray(new String[0]);
	}

	private static final class AutoProxyRegistrarSelector extends AdviceModeImportSelector<EnableSaplMethodSecurity> {

		private static final String[] IMPORTS         = new String[] { AutoProxyRegistrar.class.getName() };
		private static final String[] ASPECTJ_IMPORTS = new String[] {
				SaplMethodSecurityAspectJAutoProxyRegistrar.class.getName() };

		@Override
		protected String[] selectImports(@NonNull AdviceMode adviceMode) {
			return switch (adviceMode) {
			case PROXY -> IMPORTS;
			case ASPECTJ -> ASPECTJ_IMPORTS;
			default -> throw new IllegalStateException("AdviceMode '" + adviceMode + "' is not supported");
			};
		}

	}

}
