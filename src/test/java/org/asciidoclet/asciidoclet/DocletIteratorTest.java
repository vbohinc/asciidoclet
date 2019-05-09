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

import java.io.OutputStream;
import java.net.URI;
import javax.tools.FileObject;
import javax.tools.StandardJavaFileManager;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author John Ericksen
 */
public class DocletIteratorTest {

    private DocletEnvironment mockEnvironment;
    private Asciidoctor mockAsciidoctor;
    private Reporter reporter;

    private static final String RENDERED_FILE = "path/to/rendered/file.html";


    @Before
    public void setup() throws Throwable {
        mockEnvironment = mock(DocletEnvironment.class);
        mockAsciidoctor = mock(Asciidoctor.class);
        reporter = mock(Reporter.class);

        FileObject mockFileObject = mock(FileObject.class);
        when(mockFileObject.openOutputStream()).thenReturn(mock(OutputStream.class));
        when(mockFileObject.toUri()).thenReturn(new URI(RENDERED_FILE));
        StandardJavaFileManager mockFileManager = mock(StandardJavaFileManager.class);
        when(mockFileManager.getFileForOutput(any(), any(), any(), any())).thenReturn(mockFileObject);
        when(mockEnvironment.getJavaFileManager()).thenReturn(mockFileManager);
    }

    @Test
    public void testIgnoreNonAsciidocOverview() {
        DocletOptions docletOptions = DocletOptionsTest.newDocletOptions(new String[][] {{DocletOptions.OVERVIEW, "foo.html"}});
        AsciidoctorRenderer renderer = new AsciidoctorRenderer(mockEnvironment, docletOptions, reporter, null, mockAsciidoctor);
        assertTrue(renderer.renderAll());
        verifyZeroInteractions(mockAsciidoctor);
    }

    @Test
    public void testFailIfAsciidocOverviewNotFound() {
        DocletOptions docletOptions = DocletOptionsTest.newDocletOptions(new String[][] {{DocletOptions.OVERVIEW, "notfound.adoc"}});
        AsciidoctorRenderer renderer = new AsciidoctorRenderer(mockEnvironment, docletOptions, reporter, null, mockAsciidoctor);
        assertFalse(renderer.renderAll());
    }

    @Test
    public void testOverviewFound() {
        DocletOptions docletOptions = DocletOptionsTest.newDocletOptions(new String[][] {{DocletOptions.OVERVIEW, "src/main/java/overview.adoc"}});
        AsciidoctorRenderer renderer = new AsciidoctorRenderer(mockEnvironment, docletOptions, reporter, null, mockAsciidoctor);
        assertTrue(renderer.renderAll());
        verify(mockAsciidoctor).render(anyString(), (Options)any());
        assertTrue(renderer.getOverview().isPresent());
        assertEquals(RENDERED_FILE, renderer.getOverview().get().getPath());
    }
}
