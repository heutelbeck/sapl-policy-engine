/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar;

import org.eclipse.emf.ecore.EPackage;

import com.google.inject.Injector;

import io.sapl.grammar.sapl.SaplPackage;

/**
 * Initialization support for running Xtext languages without Equinox extension
 * registry.
 */
public class SAPLStandaloneSetup extends SAPLStandaloneSetupGenerated {

    public static void doSetup() {
        new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();
    }

    @Override
    public void register(final Injector injector) {
        EPackage.Registry.INSTANCE.computeIfAbsent(SaplPackage.eNS_URI, key -> SaplPackage.eINSTANCE);
        super.register(injector);
    }

}
