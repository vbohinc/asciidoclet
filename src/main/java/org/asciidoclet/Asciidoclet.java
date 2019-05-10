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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.DocumentationTool;
import javax.tools.StandardJavaFileManager;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.StandardDoclet;
import org.asciidoclet.asciidoclet.AsciidoctorRenderer;
import org.asciidoclet.asciidoclet.DocletOptions;
import org.asciidoclet.asciidoclet.Stylesheets;

/**
 * = Asciidoclet
 *
 * https://github.com/asciidoctor/asciidoclet[Asciidoclet] is a Javadoc Doclet
 * that uses http://asciidoctor.org[Asciidoctor] (via the
 * https://github.com/asciidoctor/asciidoctorj[Asciidoctor Java integration])
 * to interpet http://asciidoc.org[AsciiDoc] markup within Javadoc comments.
 *
 * include::README.adoc[tags=usage]
 *
 * == Examples
 *
 * Custom attributes::
 * `+{project_name}+`;; {project_name}
 * `+{project_desc}+`;; {project_desc}
 * `+{project_version}+`;; {project_version}
 *
 * Code block (with syntax highlighting added by CodeRay)::
 * +
 * [source,java]
 * --
 * /**
 *  * = Asciidoclet
 *  *
 *  * A Javadoc Doclet that uses http://asciidoctor.org[Asciidoctor]
 *  * to render http://asciidoc.org[AsciiDoc] markup in Javadoc comments.
 *  *
 *  * @author https://github.com/johncarl81[John Ericksen]
 *  *\/
 * public class Asciidoclet extends Doclet {
 *     private final Asciidoctor asciidoctor = Asciidoctor.Factory.create(); // <1>
 *
 *     @SuppressWarnings("UnusedDeclaration")
 *     public static boolean start(RootDoc rootDoc) {
 *         new Asciidoclet().render(rootDoc); // <2>
 *         return Standard.start(rootDoc);
 *     }
 * }
 * --
 * <1> Creates an instance of the Asciidoctor Java integration
 * <2> Runs Javadoc comment strings through Asciidoctor
 *
 * Inline code:: `code()`
 *
 * Headings::
 * +
 * --
 * [float]
 * = Heading 1
 *
 * [float]
 * == Heading 2
 *
 * [float]
 * === Heading 3
 *
 * [float]
 * ==== Heading 4
 *
 * [float]
 * ===== Heading 5
 * --
 *
 * Links::
 * Doc Writer <doc@example.com> +
 * http://asciidoc.org[AsciiDoc] is a lightweight markup language. +
 * Learn more about it at http://asciidoctor.org. +
 *
 * Bullets::
 * +
 * --
 * .Unnumbered
 * * bullet
 * * bullet
 * - bullet
 * - bullet
 * * bullet
 * ** bullet
 * ** bullet
 * *** bullet
 * *** bullet
 * **** bullet
 * **** bullet
 * ***** bullet
 * ***** bullet
 * **** bullet
 * *** bullet
 * ** bullet
 * * bullet
 * --
 * +
 * --
 * .Numbered
 * . bullet
 * . bullet
 * .. bullet
 * .. bullet
 * . bullet
 * .. bullet
 * ... bullet
 * ... bullet
 * .... bullet
 * .... bullet
 * ... bullet
 * ... bullet
 * .. bullet
 * .. bullet
 * . bullet
 * --
 *
 * Tables::
 * +
 * .An example table
 * |===
 * |Column 1 |Column 2 |Column 3
 * 
 * |1
 * |Item 1
 * |a
 * 
 * |2
 * |Item 2
 * |b
 * 
 * |3
 * |Item 3
 * |c
 * |===
 *
 * Sidebar block::
 * +
 * .Optional Title
 * ****
 * Usage: Notes in a sidebar, naturally.
 * ****
 *
 * Admonitions::
 * +
 * IMPORTANT: Check this out!
 *
 * @author https://github.com/johncarl81[John Ericksen]
 * @version {project_version}
 * @see Asciidoclet
 * @since 0.1.0
 * @serial (or @serialField or @serialData)
 */
public class Asciidoclet implements Doclet {
    @Override
    public void init(Locale locale, Reporter reporter) {
        init(locale, reporter, new Stylesheets(docletOptions, reporter));
    }

    // test use
    void init(Locale locale, Reporter reporter, Stylesheets stylesheets) {
        this.reporter = reporter;
        this.standardDoclet.init(locale, reporter);
        this.stylesheets = stylesheets;
    }

    @Override
    public String getName() {
        return "Asciidoclet";
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return docletOptions.getSupportedOptions();
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        validateOptions();

        StandardJavaFileManager fileManager = (StandardJavaFileManager)environment.getJavaFileManager();;
        try {
            // initialise output directories
            File preprocessDir = docletOptions.preprocessDir().get();
            if (!preprocessDir.isDirectory() && !preprocessDir.mkdirs()) throw new IOException("Cannot create directory: " + preprocessDir);
            File destDir = docletOptions.destDir().get();
            if (!destDir.isDirectory() && !destDir.mkdirs()) throw new IOException("Cannot create directory: " + destDir);

            // do the rendering
            reporter.print(Diagnostic.Kind.NOTE, "Pre-processing source, outputting to directory: " + preprocessDir);
            fileManager.setLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT, Collections.singletonList(preprocessDir));
            Optional<File> overview = start(environment);
            if (overview == null) return false;

            // post-process using the standard doclet
            return StandardDocletReinvoker.invokeStandardDoclet(docletOptions, reporter, overview);
        }
        catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            reporter.print(Diagnostic.Kind.ERROR, sw.toString());
            return false;
        }
    }

    /**
     * Validate the doclet options.
     */
    void validateOptions() {
        docletOptions.validate(reporter);
    }

    /**
     * Start the rendering process for the elements specified in the environment.
     * @param environment doclet environment.
     * @return a non-null value containing the optional path to the overview file, or
     *        null if the rendering process failed.
     */
    Optional<File> start(DocletEnvironment environment) {
        Optional<File> overview = render(environment);
        return overview != null && postProcess() ? overview : null;
    }

    private Optional<File> render(DocletEnvironment environment) {
        AsciidoctorRenderer renderer = new AsciidoctorRenderer(environment, docletOptions, reporter);
        try {
            return renderer.renderAll() ? renderer.getOverview() : null;
        }
        finally {
            renderer.cleanup();
        }
    }

    private boolean postProcess() {
        if (docletOptions.stylesheet().isPresent()) {
            return true;
        }
        return stylesheets.copy();
    }


    private final StandardDoclet standardDoclet = new StandardDoclet();
    private final DocletOptions docletOptions = new DocletOptions(standardDoclet);
    private Reporter reporter;
    private Stylesheets stylesheets;
}
