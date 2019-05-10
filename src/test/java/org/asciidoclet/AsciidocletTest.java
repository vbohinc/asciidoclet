/*
 * Copyright 2013-2019 John Ericksen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.asciidoclet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import org.asciidoclet.asciidoclet.DocletOptions;
import org.asciidoclet.asciidoclet.Stylesheets;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author John Ericksen
 */
public class AsciidocletTest {
    private Reporter mockReporter;
    private Stylesheets mockStylesheets;
    private Asciidoclet asciidoclet;

    @Before
    public void setup() {
        mockReporter = mock(Reporter.class);

        mockStylesheets = mock(Stylesheets.class);
        when(mockStylesheets.copy()).thenReturn(true);

        asciidoclet = new Asciidoclet();
        asciidoclet.init(Locale.getDefault(), mockReporter, mockStylesheets);
    }

    @Test
    public void testVersion() {
        assertEquals(SourceVersion.latestSupported(), asciidoclet.getSupportedSourceVersion());
    }

    @Test
    public void testIncludeBaseDirOptionLength() {
        assertEquals(1, getOption(DocletOptions.BASEDIR).getArgumentCount());
    }

    @Test
    public void testValidBaseDirOption() {
        String[][] inputOptions = new String[][]{{DocletOptions.BASEDIR, ""}};
        processOptions(inputOptions);
        asciidoclet.validateOptions();

        verifyZeroInteractions(mockReporter);
    }

    @Test
    public void testEmptyBaseDirOption() {
        String[][] inputOptions = new String[0][];
        processOptions(inputOptions);
        asciidoclet.validateOptions();

        verify(mockReporter).print(eq(Diagnostic.Kind.WARNING), anyString());
    }

    @Test
    public void testStart() {
        DocletEnvironment mockEnvironment = mock(DocletEnvironment.class);
        when(mockEnvironment.getIncludedElements()).thenReturn(Collections.emptySet());

        String[][] options = new String[][]{{DocletOptions.BASEDIR, "test"}};
        processOptions(options);

        assertNotNull(asciidoclet.start(mockEnvironment));

        verify(mockEnvironment).getIncludedElements();
        verify(mockStylesheets).copy();
    }

    @Test
    public void testStylesheetOverride() {
        DocletEnvironment mockEnvironment = mock(DocletEnvironment.class);
        when(mockEnvironment.getIncludedElements()).thenReturn(Collections.emptySet());

        String[][] options = new String[][]{{DocletOptions.STYLESHEET, "test"}};
        processOptions(options);

        assertNotNull(asciidoclet.start(mockEnvironment));

        verify(mockEnvironment).getIncludedElements();
        verify(mockStylesheets, never()).copy();
    }


    private Doclet.Option getOption(String name) {
        for (Doclet.Option option : asciidoclet.getSupportedOptions()) {
            if (option.getNames().contains(name)) return option;
        }
        throw new IllegalArgumentException("Unknown option: " + name);
    }

    private void processOptions(String[][] options) {
        for (String[] option : options) {
            String optionName = option[0];
            Doclet.Option docletOption = getOption(optionName);
            int argCount = docletOption.getArgumentCount();
            if (option.length != argCount + 1) throw new IllegalArgumentException("Invalid option: " + Arrays.toString(option) + "; this option requires " + argCount + " arguments");
            List<String> args = Arrays.asList(option).subList(1, option.length);
            docletOption.process(optionName, args);
        }
    }
}
