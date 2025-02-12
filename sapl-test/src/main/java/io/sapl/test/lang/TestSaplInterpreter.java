/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.test.lang;

import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Injector;

import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.test.coverage.api.CoverageHitRecorder;

public class TestSaplInterpreter extends DefaultSAPLInterpreter {

    protected static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();

    public TestSaplInterpreter(CoverageHitRecorder recorder) {

        String property = System.getProperty("io.sapl.test.coverage.collect");

        if (property == null || Boolean.parseBoolean(property)) {
            INJECTOR.getInstance(XtextResourceSet.class).getPackageRegistry().getEPackage(SaplPackage.eNS_URI)
                    .setEFactoryInstance(new SaplFactoryImplCoverage(recorder));
        } else {
            // if disabled, set default SaplFactory
            INJECTOR.getInstance(XtextResourceSet.class).getPackageRegistry().getEPackage(SaplPackage.eNS_URI)
                    .setEFactoryInstance(new SaplFactoryImpl());
        }
    }

}
