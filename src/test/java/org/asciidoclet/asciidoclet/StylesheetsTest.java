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
package org.asciidoclet.asciidoclet;

import javax.tools.Diagnostic;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.StandardDoclet;
import org.junit.Before;
import org.junit.Test;

import static org.asciidoclet.asciidoclet.Stylesheets.JAVA6_STYLESHEET;
import static org.asciidoclet.asciidoclet.Stylesheets.JAVA8_STYLESHEET;
import static org.asciidoclet.asciidoclet.Stylesheets.JAVA9_STYLESHEET;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class StylesheetsTest {

    private Stylesheets stylesheets;
    private Reporter mockErrorReporter;

    @Before
    public void setup() throws Exception {
        mockErrorReporter = mock(Reporter.class);
        stylesheets = new Stylesheets(new DocletOptions(new StandardDoclet()), mockErrorReporter);
    }

    @Test
    public void java11dot0dot2SelectStylesheet9() throws Exception {
        assertEquals(JAVA9_STYLESHEET, stylesheets.selectStylesheet("11.0.2"));
        verifyNoMoreInteractions(mockErrorReporter);
    }

    @Test
    public void java11SelectStylesheet9() throws Exception {
        assertEquals(JAVA9_STYLESHEET, stylesheets.selectStylesheet("11"));
        verifyNoMoreInteractions(mockErrorReporter);
    }

    @Test
    public void java10dot0dot1ShouldSelectStylesheet9    () throws Exception {
        assertEquals(JAVA9_STYLESHEET, stylesheets.selectStylesheet("10.0.1"));
        verifyNoMoreInteractions(mockErrorReporter);
    }

    @Test
    public void java10SelectStylesheet9() throws Exception {
        assertEquals(JAVA9_STYLESHEET, stylesheets.selectStylesheet("10"));
        verifyNoMoreInteractions(mockErrorReporter);
    }

    @Test
    public void java9ShouldSelectStylesheet9() throws Exception {
        assertEquals(JAVA9_STYLESHEET, stylesheets.selectStylesheet("9"));
        verifyNoMoreInteractions(mockErrorReporter);
    }

    @Test
    public void java8ShouldSelectStylesheet8() throws Exception {
        assertEquals(JAVA8_STYLESHEET, stylesheets.selectStylesheet("1.8.0_11"));
        verifyNoMoreInteractions(mockErrorReporter);
    }

    @Test
    public void java7ShouldSelectStylesheet8() throws Exception {
        assertEquals(JAVA8_STYLESHEET, stylesheets.selectStylesheet("1.7.0_51"));
        verifyNoMoreInteractions(mockErrorReporter);
    }

    @Test
    public void java6ShouldSelectStylesheet6() throws Exception {
        assertEquals(JAVA6_STYLESHEET, stylesheets.selectStylesheet("1.6.0_45"));
        verifyNoMoreInteractions(mockErrorReporter);
    }

    @Test
    public void java5ShouldSelectStylesheet6() throws Exception {
        assertEquals(JAVA6_STYLESHEET, stylesheets.selectStylesheet("1.5.0_22"));
        verifyNoMoreInteractions(mockErrorReporter);
    }

    @Test
    public void unknownJavaShouldSelectStylesheet8AndWarn() throws Exception {
        assertEquals(JAVA9_STYLESHEET, stylesheets.selectStylesheet("42.3.0_12"));
        verify(mockErrorReporter).print(eq(Diagnostic.Kind.WARNING), anyString());
    }
}
