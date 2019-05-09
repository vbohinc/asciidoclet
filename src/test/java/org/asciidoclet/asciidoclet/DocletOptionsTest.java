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

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.StandardDoclet;
import com.google.common.base.Charsets;
import org.junit.Before;
import org.junit.Test;

import static org.asciidoclet.asciidoclet.DocletOptions.BASEDIR;
import static org.asciidoclet.asciidoclet.DocletOptions.DESTDIR;
import static org.asciidoclet.asciidoclet.DocletOptions.ENCODING;
import static org.asciidoclet.asciidoclet.DocletOptions.OVERVIEW;
import static org.asciidoclet.asciidoclet.DocletOptions.REQUIRE;
import static org.asciidoclet.asciidoclet.DocletOptions.REQUIRE_LONG;
import static org.asciidoclet.asciidoclet.DocletOptions.STYLESHEET;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DocletOptionsTest {
    @Before
    public void setup() throws Exception {
        docletOptions = new DocletOptions(new StandardDoclet());
    }

    @Test
    public void testGetBaseDir() {
        assertFalse(newDocletOptions(new String[0][]).baseDir().isPresent());
        assertEquals("test", newDocletOptions(new String[][]{{BASEDIR, "test"}}).baseDir().get().getName());
    }

    @Test
    public void testEncoding() {
        assertEquals(Charset.defaultCharset(), docletOptions.encoding());
        assertEquals(Charsets.UTF_8, newDocletOptions(new String[][]{{ENCODING, "UTF-8"}}).encoding());
        assertEquals(Charsets.US_ASCII, newDocletOptions(new String[][]{{ENCODING, "US-ASCII"}}).encoding());
        assertEquals(Charsets.ISO_8859_1, newDocletOptions(new String[][]{{ENCODING, "ISO-8859-1"}}).encoding());
    }

    @Test
    public void testOverview() {
        assertFalse(docletOptions.overview().isPresent());
        assertEquals("test.adoc", newDocletOptions(new String[][]{{OVERVIEW, "test.adoc"}}).overview().get().getName());
    }

    @Test
    public void testStylesheetFile() {
        assertFalse(docletOptions.stylesheet().isPresent());
        assertEquals("foo.css", newDocletOptions(new String[][]{{STYLESHEET, "foo.css"}}).stylesheet().get().getName());
    }

    @Test
    public void testDestDir() {
        assertFalse(docletOptions.destDir().isPresent());
        assertEquals("target", newDocletOptions(new String[][]{{DESTDIR, "target"}}).destDir().get().getName());
    }

    @Test
    public void testRequires() {
        assertTrue(docletOptions.requires().isEmpty());
        assertThat(newDocletOptions(new String[][]{{REQUIRE, "foo"}, {REQUIRE, "bar"}}).requires(), contains("foo", "bar"));
        assertThat(newDocletOptions(new String[][]{
                {REQUIRE, "a , diagrams/awesome"},
                {REQUIRE_LONG, "bar"},
                {REQUIRE_LONG, "baz,noddy"}}).requires(),
                contains("a", "diagrams/awesome", "bar", "baz", "noddy"));
    }


    static DocletOptions newDocletOptions(String[][] options) {
        DocletOptions docletOptions = new DocletOptions(new StandardDoclet());
        for (String[] option : options) {
            String optionName = option[0];
            Doclet.Option docletOption = getOption(docletOptions, optionName);
            int argCount = docletOption.getArgumentCount();
            if (option.length != argCount + 1) throw new IllegalArgumentException("Invalid option: " + Arrays.toString(option) + "; this option requires " + argCount + " arguments");
            List<String> args = Arrays.asList(option).subList(1, option.length);
            docletOption.process(optionName, args);
        }
        return docletOptions;
    }

    static Doclet.Option getOption(DocletOptions docletOptions, String name) {
        for (Doclet.Option option : docletOptions.getSupportedOptions()) {
            if (option.getNames().contains(name)) return option;
        }
        throw new IllegalArgumentException("Unknown option: " + name);
    }


    private DocletOptions docletOptions;
}
