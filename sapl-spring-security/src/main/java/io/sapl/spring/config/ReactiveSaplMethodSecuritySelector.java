package io.sapl.spring.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;

public class ReactiveSaplMethodSecuritySelector extends AdviceModeImportSelector<EnableReactiveSaplMethodSecurity> {

		@Override
		protected String[] selectImports(AdviceMode adviceMode) {
			if (adviceMode == AdviceMode.PROXY) {
				return getProxyImports();
			}
			throw new IllegalStateException("AdviceMode " + adviceMode + " is not supported");
		}

		/**
		 * Return the imports to use if the {@link AdviceMode} is set to
		 * {@link AdviceMode#PROXY}.
		 * <p>
		 * Take care of adding the necessary JSR-107 import if it is available.
		 */
		private String[] getProxyImports() {
			List<String> result = new ArrayList<>();
			result.add(AutoProxyRegistrar.class.getName());
			result.add(ReactiveSaplMethodSecurityConfiguration.class.getName());
			return result.toArray(new String[0]);
		}

	}