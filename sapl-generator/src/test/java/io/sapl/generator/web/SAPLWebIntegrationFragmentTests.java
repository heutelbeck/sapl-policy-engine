/*
 * Streaming Attribute Policy Language (SAPL) Engine
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
package io.sapl.generator.web;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.xtext.xtext.generator.IXtextGeneratorLanguage;
import org.eclipse.xtext.xtext.generator.XtextGeneratorLanguage;
import org.eclipse.xtext.xtext.generator.model.project.IXtextProjectConfig;
import org.eclipse.xtext.xtext.generator.model.project.WebProjectConfig;
import org.eclipse.xtext.xtext.generator.model.project.XtextProjectConfig;
import org.junit.jupiter.api.Test;

class SAPLWebIntegrationFragmentTests {

    public SAPLWebIntegrationFragment createDefaultFragment(Boolean needsAssets) {
        SAPLWebIntegrationFragment fragment = new SAPLWebIntegrationFragment();

        WebProjectConfig webProjectConfig;
        if (needsAssets) {
            webProjectConfig = new TestWebProjectConfig();
        } else {
            webProjectConfig = new WebProjectConfig();
        }
        XtextProjectConfig projectConfig = new XtextProjectConfig();
        projectConfig.setWeb(webProjectConfig);
        fragment.setProjectConfig(projectConfig);

        XtextGeneratorLanguage language = new XtextGeneratorLanguage();
        language.setFileExtensions("sapl");
        fragment.setLanguage(language);
        fragment.setKeywordsFilter("[a-zA-Z0-9_-]+");

        fragment.setFramework("codemirror");

        return fragment;
    }

    @Test
    void setHighlightingModuleNameSetsField() {
        String expectedValue = "myModule";

        SAPLWebIntegrationFragment fragment = new SAPLWebIntegrationFragment();
        fragment.setHighlightingModuleName(expectedValue);
        String actualValue = fragment.getHighlightingModuleName();

        if (!expectedValue.equals(actualValue))
            fail("Actual module name \"" + actualValue + "\" does not match expected module name \"" + expectedValue
                    + "\".");
    }

    @Test
    void setHighlightingPathSetsField() {
        String expectedValue = "myPath";

        SAPLWebIntegrationFragment fragment = new SAPLWebIntegrationFragment();
        fragment.setHighlightingPath(expectedValue);
        String actualValue = fragment.getHighlightingPath();

        if (!expectedValue.equals(actualValue))
            fail("Actual highlighting path \"" + actualValue + "\" does not match expected highlighting path \""
                    + expectedValue + "\".");
    }

    @Test
    void setKeywordsFilterSetsField() {
        String expectedValue = "myFilter";

        SAPLWebIntegrationFragment fragment = new SAPLWebIntegrationFragment();
        fragment.setKeywordsFilter(expectedValue);
        String actualValue = fragment.getKeywordsFilter();

        if (!expectedValue.equals(actualValue))
            fail("Actual keyword filter \"" + actualValue + "\" does not match expected keyword filter \""
                    + expectedValue + "\".");
    }

    @Test
    void getWordKeywordsReturnsEmptyList() {
        SAPLWebIntegrationFragment fragment     = new SAPLWebIntegrationFragment();
        ArrayList<String>          wordKeywords = fragment.getWordKeywords();
        if (wordKeywords != null && wordKeywords.size() != 0)
            fail("getWordKeywords did not return empty list.");
    }

    @Test
    void getNonWordKeywordsReturnsEmptyList() {
        SAPLWebIntegrationFragment fragment        = new SAPLWebIntegrationFragment();
        ArrayList<String>          nonWordKeywords = fragment.getNonWordKeywords();
        if (nonWordKeywords != null && nonWordKeywords.size() != 0)
            fail("getNonWordKeywords did not return empty list.");
    }

    @Test
    void setAllKeywordsSetsField() {
        Set<String> expectedValue = new HashSet<>();
        expectedValue.add("test");

        SAPLWebIntegrationFragment fragment = new SAPLWebIntegrationFragment();
        fragment.setAllKeywords(expectedValue);
        Set<String> actualValue = fragment.getAllKeywords();

        if (!expectedValue.equals(actualValue))
            fail("Actual all keyword set does not match expected all keyword set.");
    }

    @Test
    void setLanguageSetsField() {
        IXtextGeneratorLanguage expectedValue = new XtextGeneratorLanguage();

        SAPLWebIntegrationFragment fragment = new SAPLWebIntegrationFragment();
        fragment.setLanguage(expectedValue);
        IXtextGeneratorLanguage actualValue = fragment.getLanguage();

        if (!expectedValue.equals(actualValue))
            fail("Actual language does not match expected language.");
    }

    @Test
    void setProjectConfigSetsField() {
        IXtextProjectConfig expectedValue = new XtextProjectConfig();

        SAPLWebIntegrationFragment fragment = new SAPLWebIntegrationFragment();
        fragment.setProjectConfig(expectedValue);
        IXtextProjectConfig actualValue = fragment.getProjectConfig();

        if (!expectedValue.equals(actualValue))
            fail("Actual project config does not match expected project config.");
    }

    @Test
    void generateRemovesJsSuffixFromModuleName() {
        String expectedValue = "myModule";

        SAPLWebIntegrationFragment fragment = createDefaultFragment(Boolean.FALSE);
        fragment.setHighlightingModuleName("myModule.js");

        fragment.generate();
        String actualValue = fragment.getHighlightingModuleName();

        if (!expectedValue.equals(actualValue))
            fail("The generate method did not remove the suffix correctly.");
    }

    @Test
    void generateCreatesKeywordHighlightingInReverseOrder() {
        Set<String> keywords = new HashSet<>();
        keywords.add("a");
        keywords.add("a-a");
        keywords.add("a-b");
        keywords.add("b");

        SAPLWebIntegrationFragment fragment = createDefaultFragment(Boolean.TRUE);
        fragment.setAllKeywords(keywords);
        fragment.setGenerateJsHighlighting(true);

        fragment.generate();

        List<String> expectedValue = new ArrayList<>(keywords);
        expectedValue.sort(Collections.reverseOrder());

        List<String> actualValue = fragment.getWordKeywords();

        if (!expectedValue.equals(actualValue))
            fail("The order of the generated word list is not the same.");
    }

    @Test
    void generateCreatesKeywordHighlightingWithoutDashInNonWords() {
        Set<String> keywords = new HashSet<>();
        keywords.add("a-a");

        SAPLWebIntegrationFragment fragment = createDefaultFragment(Boolean.TRUE);
        fragment.setAllKeywords(keywords);
        fragment.setGenerateJsHighlighting(true);

        fragment.generate();

        List<String> words    = fragment.getWordKeywords();
        List<String> nonWords = fragment.getNonWordKeywords();

        if (!words.contains("a-a"))
            fail("Keyword not in word keyword list.");

        if (nonWords.contains("-"))
            fail("Dash in in non word keyword list.");
    }

}
