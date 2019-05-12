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

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.DocumentationTool;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import com.google.common.io.Files;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.TreePath;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;

import static org.asciidoclet.asciidoclet.SignaturePrinter.printFormalTypeParameters;
import static org.asciidoclet.asciidoclet.SignaturePrinter.printInterfaces;
import static org.asciidoclet.asciidoclet.SignaturePrinter.printModifiers;
import static org.asciidoclet.asciidoclet.SignaturePrinter.printParameters;
import static org.asciidoclet.asciidoclet.SignaturePrinter.printThrows;
import static org.asciidoctor.Asciidoctor.Factory.create;

/**
 * Doclet renderer using and configuring Asciidoctor.
 *
 * @author John Ericksen
 */
public class AsciidoctorRenderer implements DocletRenderer {

    private static AttributesBuilder defaultAttributes() {
        return AttributesBuilder.attributes()
                .attribute("at", "&#64;")
                .attribute("slash", "/")
                .attribute("icons", null)
                .attribute("idprefix", "")
                .attribute("idseparator", "-")
                .attribute("javadoc", "")
                .attribute("showtitle", true)
                .attribute("source-highlighter", "coderay")
                .attribute("coderay-css", "class")
                .attribute("env-asciidoclet")
                .attribute("env", "asciidoclet");
    }

    private static OptionsBuilder defaultOptions() {
        return OptionsBuilder.options()
                .safe(SafeMode.SAFE)
                .backend("html5");
    }

    protected static final String INLINE_DOCTYPE = "inline";
    private static final Pattern ASCIIDOC_FILE_PATTERN = Pattern.compile("(.*\\.(ad|adoc|txt|asciidoc))");
    private static final String OVERVIEW_HTML = "overview.html";

    private final DocletEnvironment environment;
    private final Elements elementUtils;
    private final DocletOptions docletOptions;
    private final Reporter reporter;
    private final StandardJavaFileManager fileManager;
    private final Asciidoctor asciidoctor;
    private final OutputTemplates templates;
    private final Options options;
    private Optional<File> overview;


    public AsciidoctorRenderer(DocletEnvironment environment, DocletOptions docletOptions, Reporter reporter) {
        this(environment, docletOptions, reporter, OutputTemplates.create(reporter), create(docletOptions.gemPath()));
    }

    /**
     * Constructor used directly for testing purposes only.
     */
    protected AsciidoctorRenderer(DocletEnvironment environment, DocletOptions docletOptions, Reporter reporter, OutputTemplates templates, Asciidoctor asciidoctor) {
        this.environment = environment;
        this.elementUtils = environment.getElementUtils();
        this.docletOptions = docletOptions;
        this.reporter = reporter;
        this.fileManager = (StandardJavaFileManager)environment.getJavaFileManager();
        this.asciidoctor = asciidoctor;
        this.templates = templates;
        this.options = buildOptions(docletOptions, reporter);
        this.overview = docletOptions.overview();
    }

    private Options buildOptions(DocletOptions docletOptions, Reporter errorReporter) {
        OptionsBuilder opts = defaultOptions();
        if (docletOptions.baseDir().isPresent()) {
            opts.baseDir(docletOptions.baseDir().get());
        }
        if (templates != null) {
            opts.templateDir(templates.templateDir());
        }
        opts.attributes(buildAttributes(docletOptions, errorReporter));
        if (docletOptions.requires().size() > 0) {
            for (String require : docletOptions.requires()) {
                asciidoctor.rubyExtensionRegistry().requireLibrary(require);
            }
        }
        return opts.get();
    }

    private Attributes buildAttributes(DocletOptions docletOptions, Reporter errorReporter) {
        return defaultAttributes()
            .attributes(new AttributesLoader(asciidoctor, docletOptions, errorReporter).load())
            .get();
    }

    /**
     * Processes all documents elements specified in the {@link DocletEnvironment} provided
     * in the constructor.
     * @return true if rendering was successful, false otherwise.
     */
    public boolean renderAll() {
        if (!processOverview()) return false;

        Set<? extends Element> elements = environment.getIncludedElements();
        for (ModuleElement moduleElement : ElementFilter.modulesIn(elements)) {
            renderModule(moduleElement);
        }
        for (PackageElement packageElement : ElementFilter.packagesIn(elements)) {
            renderPackage(packageElement);
        }
        for (TypeElement typeElement : ElementFilter.typesIn(elements)) {
            renderClass(typeElement);
        }

        return true;
    }

    /**
     * Renders the comment attached to a document element (class, field, method, etc)
     *
     * @param e the document element.
     * @param pw print writer to output the comment to.
     */
    @Override
    public void renderDoc(Element e, PrintWriter pw) {
        DocCommentTree t = environment.getDocTrees().getDocCommentTree(e);

        // does the element even have a comment?
        if (t == null) return;

        TreePath path = environment.getDocTrees().getPath(e);
        DocCommentRenderer renderer = new DocCommentRenderer(environment, asciidoctor, options, path, t);
        pw.println("/**");
        pw.println(renderer.render());
        pw.println("*/");
    }

    /**
     * Get the (possibly pre-processed) overview document file path, if any,
     */
    public Optional<File> getOverview() {
        return overview;
    }

    public void cleanup() {
        if (templates != null) {
            templates.delete();
        }
    }

    /**
     * Process the overview document, if any.
     * @return true if processing was successful, false otherwise.
     */
    private boolean processOverview() {
        if (overview.isPresent()) {
            File overviewFile = overview.get();
            if (isAsciidocFile(overviewFile.getName())) {
                try {
                    String overviewContent = Files.toString(overviewFile, docletOptions.encoding());
                    String rendered = DocCommentRenderer.render(asciidoctor, options, overviewContent);
                    FileObject f = fileManager.getFileForOutput(DocumentationTool.Location.DOCUMENTATION_OUTPUT, "", OVERVIEW_HTML, null);
                    try (PrintWriter fw = new PrintWriter(new OutputStreamWriter(f.openOutputStream()))) {
                        fw.println("<html><body>");
                        fw.println(rendered);
                        fw.println("</body></html>");
                    }
                    overview = Optional.of(new File(f.toUri().getPath()));
                }
                catch (IOException e) {
                    reporter.print(Diagnostic.Kind.ERROR, "Error processing overview file: " + e.getLocalizedMessage());
                    return false;
                }
            }
            else {
                reporter.print(Diagnostic.Kind.NOTE, "Skipping non-AsciiDoc overview " + overviewFile + ", will be processed by standard Doclet.");
            }
        }
        return true;
    }

    private boolean isAsciidocFile(String name) {
        return ASCIIDOC_FILE_PATTERN.matcher(name).matches();
    }

    /**
     * Get the source directories available to us.
     * @param module an optional module name to add source paths for.
     * @return an array of directory paths.
     */
    private File[] getSourceDirs(String module) {
        File[] srcDirs;
        if (docletOptions.moduleSrcDirs().isPresent()) {
            srcDirs = docletOptions.moduleSrcDirs().get();
        }
        else if (docletOptions.srcDirs().isPresent()) {
            srcDirs = docletOptions.srcDirs().get();
        }
        else {
            srcDirs = new File[] { new File(".").getAbsoluteFile() };
        }
        if (module != null) {
            File[] patchDirs = docletOptions.modulePatchDirs(module);
            if (patchDirs != null) {
                srcDirs = Stream.concat(Arrays.stream(patchDirs), Arrays.stream(srcDirs)).toArray(File[]::new);
            }
        }
        return srcDirs;
    }

    /**
     * Copies the module-info.java, if found, from a source directory to the pre-processing directory.
     * @param me a document module element.
     */
    private void renderModule(ModuleElement me) {
        if (me.isUnnamed()) return;

        File[] srcDirs = getSourceDirs(me.getQualifiedName().toString());
        File preprocessDir = docletOptions.preprocessDir().get();
        for (File srcDir : srcDirs) {
            if (copyFile(srcDir, ".", "module-info.java", preprocessDir)) return;
        }
        reporter.print(Diagnostic.Kind.ERROR, "Given a module element but couldn't find module-info.java!?");
    }

    /**
     * Copies the package-info.java or package.html file, if found, from a source directory to the
     * pre-processing directory.
     *
     * Arguably we could look for Asciidoctor variants of these files and render them but currently
     * we don't do that.
     * @param pe a document package element.
     */
    private void renderPackage(PackageElement pe) {
        String packagePath = pe.getQualifiedName().toString().replace(".", File.separator);
        File preprocessDir = docletOptions.preprocessDir().get();
        for (File srcDir : getSourceDirs(null)) {
            if (copyFile(srcDir, packagePath, "package-info.java", preprocessDir) || copyFile(srcDir, packagePath, "package.html", preprocessDir))
                break;
        }
    }

    private boolean copyFile(File srcDir, String packagePath, String filename, File destDir) {
        File filePath = new File(new File(srcDir, packagePath), filename);
        if (filePath.isFile()) {
            File destPath = new File(new File(destDir, packagePath), filename);
            try {
                File parentDir = destPath.getCanonicalFile().getParentFile();
                if (!parentDir.isDirectory() && !parentDir.mkdirs()) throw new IOException("Cannot create directory: " + parentDir);
                Files.copy(filePath, destPath);
                return true;
            }
            catch (IOException ioe) {
                throw new RuntimeException("Unable to copy " + filePath + " to " + destPath, ioe);
            }
        }
        return false;
    }

    /**
     * Renders comments in the top-level class specified by the provided {@linkplain TypeElement}.
     * Output is written to a Java source file in the documentation output directory.
     * @param te a document type element for the class.
     */
    private void renderClass(TypeElement te) {
        assert te.getNestingKind() == NestingKind.TOP_LEVEL;

        StringWriter sw = new StringWriter();
        renderType(te, new PrintWriter(sw));

        try {
            FileObject f = fileManager.getJavaFileForOutput(DocumentationTool.Location.DOCUMENTATION_OUTPUT, te.getQualifiedName().toString(), JavaFileObject.Kind.SOURCE, null);
            try (PrintWriter fw = new PrintWriter(new OutputStreamWriter(f.openOutputStream()))) {
                fw.write(sw.toString());
            }
        }
        catch (IOException ioe) {
            throw new RuntimeException("Unable to write " + te.getQualifiedName(), ioe);
        }
    }

    /**
     * Renders comments in the Java type identified by the specified {@link TypeElement}.
     * Output is written to the specified PrintWriter.
     *
     * This implementation is based on {@link com.sun.tools.javac.processing.PrintingProcessor.PrintingElementVisitor#visitType(TypeElement, Boolean)}
     * @param te a document type element.
     * @param pw rendered output target.
     */
    private void renderType(TypeElement te, PrintWriter pw) {
        if (te.getNestingKind() == NestingKind.TOP_LEVEL) {
            PackageElement pe = elementUtils.getPackageOf(te);
            if (!pe.isUnnamed()) {
                pw.println("package " + pe.getQualifiedName() + ";\n");
            }
        }

        renderDoc(te, pw);
        printModifiers(te, pw);
        pw.print(te.getKind() == ElementKind.ANNOTATION_TYPE ? "@interface" : te.getKind().toString().toLowerCase());
        pw.print(" ");
        pw.print(te.getSimpleName());
        printFormalTypeParameters(te, false, pw);
        if (te.getKind() == ElementKind.CLASS) {
            TypeMirror supertype = te.getSuperclass();
            if (supertype.getKind() != TypeKind.NONE) {
                TypeElement e2 = (TypeElement)((DeclaredType)supertype).asElement();
                if (e2.getSuperclass().getKind() != TypeKind.NONE) {
                    pw.print(" extends " + supertype);
                }
            }
        }

        printInterfaces(te, pw);
        pw.println(" {");

        List<? extends Element> elements = te.getEnclosedElements();
        if (te.getKind() == ElementKind.ENUM) {
            elements = new ArrayList<>(elements);
            Iterator<VariableElement> enumConstants = te.getEnclosedElements().stream().filter(p -> p.getKind() == ElementKind.ENUM_CONSTANT).map(VariableElement.class::cast).iterator();
            while (enumConstants.hasNext()) {
                VariableElement e = enumConstants.next();
                renderDoc(e, pw);
                pw.print(e.getSimpleName());
                pw.println(enumConstants.hasNext() ? ',' : ';');
                elements.remove(e);
            }
            pw.println();
        }

        for (Element e : elements) {
            switch (e.getKind()) {
                case INTERFACE:
                case CLASS:
                case ENUM:
                case ANNOTATION_TYPE:
                    renderType((TypeElement)e, pw);
                    break;
                case CONSTRUCTOR:
                case METHOD:
                    renderMethod((ExecutableElement)e, pw);
                    break;
                case FIELD:
                    renderField((VariableElement)e, pw);
                    break;
                default:
                    reporter.print(Diagnostic.Kind.WARNING, "Unsupported element type " + e.getKind() + " in class " + e);
                    break;
            }
        }

        pw.println("}\n");
    }

    /**
     * Renders comments in the method identified by the specified {@link ExecutableElement}.
     * Output is written to the specified PrintWriter.
     *
     * This implementation is based on {@link com.sun.tools.javac.processing.PrintingProcessor.PrintingElementVisitor#visitExecutable(ExecutableElement, Boolean)}
     * @param ee a document executable element.
     * @param pw rendered output target.
     */
    private void renderMethod(ExecutableElement ee, PrintWriter pw) {
        // ignore implicit methods included in enums
        if (ee.getEnclosingElement().getKind() == ElementKind.ENUM && ee.getModifiers().contains(Modifier.STATIC)) {
            String methodName = ee.getSimpleName().toString();
            // ignore Enum.values() method
            if (methodName.equals("values") && ee.getParameters().isEmpty()) return;
            // ignore Enum.valueOf(String) method
            if (methodName.equals("valueOf")) {
                List<? extends VariableElement> params = ee.getParameters();
                if (params.size() == 1 && params.get(0).asType().toString().equals("java.lang.String")) return;
            }
        }

        renderDoc(ee, pw);
        printModifiers(ee, pw);
        printFormalTypeParameters(ee, true, pw);

        switch (ee.getKind()) {
            case CONSTRUCTOR:
                // Print out simple name of the class
                pw.print(ee.getEnclosingElement().getSimpleName());
                break;

            case METHOD:
                pw.print(ee.getReturnType().toString());
                pw.print(" ");
                pw.print(ee.getSimpleName().toString());
                break;
        }

        pw.print("(");
        printParameters(ee, pw);
        pw.print(")");
        AnnotationValue defaultValue = ee.getDefaultValue();
        if (defaultValue != null)
            pw.print(" default " + defaultValue);

        printThrows(ee, pw);
        pw.println(";\n");
    }

    /**
     * Renders comments in the field identified by the specified {@link VariableElement}.
     * Output is written to the specified PrintWriter.
     *
     * This implementation is based on {@link com.sun.tools.javac.processing.PrintingProcessor.PrintingElementVisitor#visitVariable(VariableElement, Boolean)}
     * @param ve a document variable element.
     * @param pw rendered output target.
     */
    private void renderField(VariableElement ve, PrintWriter pw) {
        renderDoc(ve, pw);
        printModifiers(ve, pw);
        pw.print(ve.asType());
        pw.print(' ');
        pw.print(ve.getSimpleName());

        Object value = ve.getConstantValue();
        if (value != null) {
            pw.print(" = ");
            if (value instanceof String) {
                pw.print('"');
                pw.print(value);
                pw.print('"');
            }
            else {
                pw.print(value);
            }
        }
        pw.println(';');
    }
}
