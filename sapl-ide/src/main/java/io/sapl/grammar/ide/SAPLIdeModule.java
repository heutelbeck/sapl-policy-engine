/**
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.grammar.ide;

import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;

import io.sapl.grammar.ide.contentassist.SAPLContentProposalProvider;
import io.sapl.grammar.ide.spring.SpringDependencyResolver;

/**
 * Use this class to register ide components.
 */
public class SAPLIdeModule extends AbstractSAPLIdeModule {
	public Class<? extends IdeContentProposalProvider> bindIdeContentProposalProvider() {
		return SAPLContentProposalProvider.class;
	}
	
	public Class<SpringDependencyResolver> bindSpringDependencyResolver() {
		return SpringDependencyResolver.class;
	}
}
