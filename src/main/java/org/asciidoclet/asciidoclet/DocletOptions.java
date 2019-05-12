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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import com.google.common.base.Splitter;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.StandardDoclet;

/**
 * Provides an interface to the doclet options we are interested in.
 */
public class DocletOptions {

    // Split on comma with optional whitespace
    private static final Splitter COMMA_WS = Splitter.onPattern("\\s*,\\s*").omitEmptyStrings().trimResults();

    public static final String ENCODING = "-encoding";
    public static final String OVERVIEW = "-overview";
    public static final String BASEDIR = "--base-dir";
    public static final String STYLESHEET = "-stylesheetfile";
    public static final String STYLESHEET_LONG = "--main-stylesheet";
    public static final String SOURCE_PATH = "-sourcepath";
    public static final String SOURCE_PATH_LONG = "--source-path";
    public static final String MODULE_SOURCE_PATH_LONG = "--module-source-path";
    public static final String MODULE_SOURCE_PATH = "-p";
    public static final String PATCH_MODULE = "--patch-module";
    public static final String DESTDIR = "-d";
    public static final String ATTRIBUTE = "-a";
    public static final String ATTRIBUTE_LONG = "--attribute";
    public static final String ATTRIBUTES_FILE = "--attributes-file";
    public static final String GEM_PATH = "--gem-path";
    public static final String REQUIRE = "-r";
    public static final String REQUIRE_LONG = "--require";
    public static final String PRESERVE_PREPROCESSED = "-pp";
    public static final String PRESERVE_PREPROCESSED_LONG = "--preserve-preprocessed";

    private final StandardDoclet standardDoclet;
    private final Map<String,Doclet.Option> asciidocletOptions;
    private Optional<File> basedir = Optional.empty();
    private Optional<File> overview = Optional.empty();
    private Optional<File> stylesheet = Optional.empty();
    private Optional<File[]> srcDirs = Optional.empty();
    private Optional<File[]> moduleSrcDirs = Optional.empty();
    private Map<String,File[]> patchModuleDirs = new HashMap<>();
    private Optional<File> destdir = Optional.empty();
    private Optional<File> preprocessDir = Optional.empty();
    private Optional<File> attributesFile = Optional.empty();
    private String gemPath;
    private final List<String> requires = new ArrayList<>();
    private Charset encoding = Charset.defaultCharset();
    private final List<String> attributes = new ArrayList<>();
    private boolean preservePreprocessed;

    public DocletOptions(StandardDoclet standardDoclet) {
        this.standardDoclet = standardDoclet;

        // These source path options are, as standard, tool options rather than doclet options
        // (see jdk.javadoc.internal.tool.ToolOption)
        // It's a little bit ugly making them doclet options here but there's no other way for
        // us to get access to the argument values
        Doclet.Option srcDirOption = new Option(List.of(SOURCE_PATH_LONG, SOURCE_PATH), "Specify where to find source files", 1, "<path>") {
            @Override
            protected void process(List<String> arguments) {
                String[] paths = arguments.get(0).split(File.pathSeparator);
                srcDirs = Optional.of(Arrays.stream(paths).map(File::new).toArray(File[]::new));
            }
        };
        Doclet.Option moduleDirOption = new Option(List.of(MODULE_SOURCE_PATH_LONG, MODULE_SOURCE_PATH), "Specify where to find application modules", 1, "<path>") {
            @Override
            protected void process(List<String> arguments) {
                String[] paths = arguments.get(0).split(File.pathSeparator);
                moduleSrcDirs = Optional.of(Arrays.stream(paths).map(File::new).toArray(File[]::new));
            }
        };
        Doclet.Option patchModuleDirOption = new Option(PATCH_MODULE, "Override or augment a module with classes and resources in JAR files or directories", 1, "<module>=<file>(:<file>)*") {
            @Override
            public Kind getKind() {
                return Kind.EXTENDED;
            }
            @Override
            protected void process(List<String> arguments) {
                String arg = arguments.get(0);
                int i = arg.indexOf('=');
                if (i < 0) return;
                String module = arg.substring(0, i);
                String[] paths = arg.substring(i + 1).split(File.pathSeparator);
                File[] patchDirs = Arrays.stream(paths).map(File::new).toArray(File[]::new);
                patchModuleDirs.put(module, patchDirs);
            }
        };

        Doclet.Option baseDirOption = new Option(BASEDIR, "Sets the base directory that will be used to resolve relative path names in Asciidoc include:: directives", 1, "<directory>") {
            @Override
            protected void process(List<String> arguments) {
                basedir = Optional.of(new File(arguments.get(0)));
            }
        };
        Doclet.Option attributeOption = new Option(List.of(ATTRIBUTE, ATTRIBUTE_LONG), "Sets document attributes that will be expanded in Javadoc comments.  The argument is a string containing a single attribute, or multiple attributes separated by commas", 1, "name[=value], ...") {
            @Override
            public void process(List<String> arguments) {
                COMMA_WS.split(arguments.get(0)).forEach(attributes::add);
            }
        };
        Doclet.Option attributesFileOption = new Option(ATTRIBUTES_FILE, "Read document attributes from an Asciidoc file. The attributes will be expanded in Javadoc comments", 1, "<file>") {
            @Override
            protected void process(List<String> arguments) {
                attributesFile = Optional.of(new File(arguments.get(0)));
            }
        };
        Doclet.Option requireOption = new Option(List.of(REQUIRE, REQUIRE_LONG), "Make the specified RubyGems library available to Asciidoctor’s JRuby runtime", 1, "<library>") {
            @Override
            protected void process(List<String> arguments) {
                COMMA_WS.split(arguments.get(0)).forEach(requires::add);
            }
        };
        Doclet.Option gemPathOption = new Option(GEM_PATH, "Sets the GEM_PATH for Asciidoctor’s JRuby runtime.  This option is only needed when using the --require option to load additional gems on the GEM_PATH", 1, "<path>") {
            @Override
            protected void process(List<String> arguments) {
                gemPath = arguments.get(0);
            }
        };
        Doclet.Option preserveOption = new Option(List.of(PRESERVE_PREPROCESSED, PRESERVE_PREPROCESSED_LONG), "Indicates that pre-processed source should be preserved rather than deleted", 0, "") {
            @Override
            protected void process(List<String> arguments) {
                preservePreprocessed = true;
            }
        };

        TreeMap<String,Doclet.Option> asciidocletOptions = new TreeMap<>();
        asciidocletOptions.put(SOURCE_PATH, srcDirOption);
        asciidocletOptions.put(SOURCE_PATH_LONG, srcDirOption);
        asciidocletOptions.put(MODULE_SOURCE_PATH, moduleDirOption);
        asciidocletOptions.put(MODULE_SOURCE_PATH_LONG, moduleDirOption);
        asciidocletOptions.put(PATCH_MODULE, patchModuleDirOption);
        asciidocletOptions.put(BASEDIR, baseDirOption);
        asciidocletOptions.put(ATTRIBUTE, attributeOption);
        asciidocletOptions.put(ATTRIBUTE_LONG, attributeOption);
        asciidocletOptions.put(ATTRIBUTES_FILE, attributesFileOption);
        asciidocletOptions.put(REQUIRE, requireOption);
        asciidocletOptions.put(REQUIRE_LONG, requireOption);
        asciidocletOptions.put(GEM_PATH, gemPathOption);
        asciidocletOptions.put(PRESERVE_PREPROCESSED, preserveOption);
        asciidocletOptions.put(PRESERVE_PREPROCESSED_LONG, preserveOption);
        this.asciidocletOptions = Collections.unmodifiableMap(asciidocletOptions);
    }

    public Doclet.Option getAsciidocletOption(String name) {
        return asciidocletOptions.get(name);
    }

    public Set<? extends Doclet.Option> getSupportedOptions() {
        Set<Doclet.Option> standardOptions = standardDoclet.getSupportedOptions();
        Map<String,Doclet.Option> optionsMap = new TreeMap<>(standardOptions.stream().collect(Collectors.toMap(option -> option.getNames().get(0), option -> option)));

        // add interceptors for standard doclet options we care about
        optionsMap.put(DESTDIR, new DelegatingOption(optionsMap.get(DESTDIR)) {
            @Override
            protected void process(List<String> arguments) {
                File dir = new File(arguments.get(0));
                destdir = Optional.of(dir);
                preprocessDir = Optional.of(new File(dir.getParentFile(), dir.getName() + ".preprocess"));
            }
        });
        optionsMap.put(ENCODING, new DelegatingOption(optionsMap.get(ENCODING)) {
            @Override
            protected void process(List<String> arguments) {
                encoding = Charset.forName(arguments.get(0));
            }
        });
        optionsMap.put(OVERVIEW, new DelegatingOption(optionsMap.get(OVERVIEW)) {
            @Override
            protected void process(List<String> arguments) {
                overview = Optional.of(new File(arguments.get(0)));
            }
        });
        optionsMap.put(STYLESHEET_LONG, new DelegatingOption(optionsMap.get(STYLESHEET_LONG)) {
            @Override
            protected void process(List<String> arguments) {
                stylesheet = Optional.of(new File(arguments.get(0)));
            }
        });

        // add asciidoclet extension options
        for (Doclet.Option option : asciidocletOptions.values()) {
            optionsMap.put(option.getNames().get(0), option);
        }

        TreeSet<Doclet.Option> options = new TreeSet<>(Comparator.comparing(o -> o.getNames().get(0)));
        options.addAll(optionsMap.values());
        return options;
    }

    public void validate(Reporter reporter) {
        if (!basedir.isPresent()) {
            reporter.print(Diagnostic.Kind.WARNING, BASEDIR + " must be present for includes or file reference features to work properly.");
        }
        if (attributesFile.isPresent() && !attributesFile.get().canRead()) {
            reporter.print(Diagnostic.Kind.WARNING, "Cannot read attributes file: " + attributesFile);
        }
    }

    public Optional<File> overview() {
        return overview;
    }

    public Optional<File> stylesheet() {
        return stylesheet;
    }

    public Optional<File> baseDir() {
        return basedir;
    }

    public Optional<File[]> srcDirs() {
        return srcDirs;
    }

    public Optional<File[]> moduleSrcDirs() {
        return moduleSrcDirs;
    }

    public File[] modulePatchDirs(String module) {
        return patchModuleDirs.get(module);
    }

    public Map<String,File[]> allModulePatchDirs() {
        return Collections.unmodifiableMap(patchModuleDirs);
    }

    public Optional<File> destDir() {
        return destdir;
    }

    public Optional<File> preprocessDir() {
        return preprocessDir;
    }

    public Charset encoding() {
        return encoding;
    }

    public List<String> attributes() {
        return Collections.unmodifiableList(attributes);
    }

    Optional<File> attributesFile() {
        if (!attributesFile.isPresent()) {
            return attributesFile;
        }
        File f = attributesFile.get();
        if (!f.isAbsolute() && basedir.isPresent()) {
            f = new File(basedir.get(), f.getPath());
        }
        return Optional.of(f);
    }

    public String gemPath() {
        return gemPath;
    }

    public List<String> requires() {
        return requires;
    }

    public boolean preservePreprocessed() {
        return preservePreprocessed;
    }


    private abstract class Option implements Doclet.Option {
        protected Option(String name, String description, int argCount, String parameters) {
            this(Collections.singletonList(name), description, argCount, parameters);
        }

        protected Option(List<String> names, String description, int argCount, String parameters) {
            this.names = names;
            this.description = description;
            this.argCount = argCount;
            this.parameters = parameters;
        }

        @Override
        public final List<String> getNames() {
            return names;
        }

        @Override
        public final String getDescription() {
            return description;
        }

        @Override
        public final int getArgumentCount() {
            return argCount;
        }

        @Override
        public final String getParameters() {
            return parameters;
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public final boolean process(String option, List<String> arguments) {
            process(arguments);
            return true;
        }

        protected abstract void process(List<String> arguments);

        private final List<String> names;
        private final String description;
        private final int argCount;
        private final String parameters;
    }

    private abstract class DelegatingOption implements Doclet.Option {
        protected DelegatingOption(Doclet.Option delegate) {
            if (delegate == null) throw new NullPointerException("delegate is null");
            this.delegate = delegate;
        }

        @Override
        public final List<String> getNames() {
            return delegate.getNames();
        }

        @Override
        public final String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public final int getArgumentCount() {
            return delegate.getArgumentCount();
        }

        @Override
        public final String getParameters() {
            return delegate.getParameters();
        }

        @Override
        public final Kind getKind() {
            return delegate.getKind();
        }

        @Override
        public final boolean process(String option, List<String> arguments) {
            process(arguments);
            return delegate.process(option, arguments);
        }

        protected abstract void process(List<String> arguments);

        private final Doclet.Option delegate;
    }
}
