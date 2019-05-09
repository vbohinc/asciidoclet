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
import java.io.Reader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.tools.Diagnostic;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Reporter;
import org.asciidoclet.asciidoclet.DocletOptions;
import org.asciidoclet.asciidoclet.Stylesheets;

class StandardDocletReinvoker {
    /**
     * Attempt to reinvoke the standard javadoc doclet to generate the final HTML from the
     * pre-processed source.
     *
     * Reads the command line that this Asciidoclet was invoked with, adds, modifies,and/or
     * removes arguments as needed, then executes the new command line as a sub-process.
     *
     * @param preprocessDir directory contained generated pre-processed source.
     * @param overview optional (possibly pre-processed) overview document.
     * @param docletOptions options for this doclet.
     * @param reporter message reporter
     * @return true if the standard doclet was invoked, false otherwise.
     * @throws Exception if an unexpected error occurs.
     */
    static boolean invokeStandardDoclet(File preprocessDir, Optional<File> overview, DocletOptions docletOptions, Reporter reporter) throws Exception {
        // get the command line we were invoked with
        Map.Entry<String,String[]> commandLine = getCommandLine();
        if (commandLine == null) {
            reporter.print(Diagnostic.Kind.ERROR, "Invoked command line not available, you will need to run the standard doclet over the pre-processed source manually");
            return false;
        }

        reporter.print(Diagnostic.Kind.NOTE, "Pre-processing complete, invoking standard doclet to generate final documentation...");

        // generate a revised command line
        List<String> newCommandLine = new ArrayList<>(commandLine.getValue().length + 3);
        newCommandLine.add(commandLine.getKey());
        // set the source path to the pre-processed output directory
        newCommandLine.add("-sourcepath");
        newCommandLine.add(preprocessDir.getAbsolutePath());
        // add main stylesheet if not previously specified
        if (!docletOptions.stylesheet().isPresent()) {
            newCommandLine.add(DocletOptions.STYLESHEET);
            newCommandLine.add(new File(preprocessDir, Stylesheets.OUTPUT_STYLESHEET).getAbsolutePath());
        }
        if (overview.isPresent()) {
            newCommandLine.add(DocletOptions.OVERVIEW);
            newCommandLine.add(overview.get().getAbsolutePath());
        }
        // check existing args, removing those we don't want, and copying over the remainder
        Iterator<String> oldArgs = Arrays.stream(commandLine.getValue()).iterator();
        while (oldArgs.hasNext()) {
            String arg = oldArgs.next();
            String option = null;

            // check for --arg=value style options
            if (arg.startsWith("--")) {
                int sep = arg.indexOf("=");
                if (sep >= 0) {
                    option = arg.substring(sep + 1);
                    arg = arg.substring(0, sep);
                }
            }
            // remove existing sourcepath args
            if (arg.equals("-sourcepath")) {
                oldArgs.next();
                continue;
            }
            if (arg.equals("--source-path")) {
                if (option == null) oldArgs.next();
                continue;
            }
            // remove existing overview option
            if (arg.equals(DocletOptions.OVERVIEW)) {
                oldArgs.next();
                continue;
            }
            // remove doclet options
            if (arg.equals("-doclet") || arg.equals("-docletpath")) {
                oldArgs.next();
                continue;
            }
            // remove asciidoclet-specific options
            Doclet.Option asciidocletOption = docletOptions.getAsciidocletOption(arg);
            if (asciidocletOption != null) {
                if (!(arg.startsWith("--") && asciidocletOption.getArgumentCount() == 1 && option != null)) {
                    for (int i=0; i<asciidocletOption.getArgumentCount(); i++) {
                        oldArgs.next();
                    }
                }
                continue;
            }
            newCommandLine.add(arg);
        }

        // start a new javadoc sub-process with the generated command line
        reporter.print(Diagnostic.Kind.NOTE, "Invoking standard doclet on pre-processed source with command line: " + String.join(" ", newCommandLine));
        Process p = new ProcessBuilder().inheritIO().command(newCommandLine).start();
        p.waitFor();

        // clean up
        if (!docletOptions.preservePreprocessed()) {
            // delete pre-processed source
            Files.walkFileTree(preprocessDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        // best just to return true here
        // if the standard doclet invocation logs errors then returning anything else here
        // (such as false on a non-zero exit code) leads to confusing output
        return true;
    }

    /**
     * Get the command line this javadoc process was invoked with.
     * @return a map "entry" where the "key" is the invoked executable and the "value" is the
     *        command line arguments, expanded in the case of any "@file" arguments.
     */
    private static Map.Entry<String,String[]> getCommandLine() throws IOException {
        ProcessHandle.Info processInfo = ProcessHandle.current().info();
        Optional<String> command = processInfo.command();
        Optional<String[]> args = processInfo.arguments();
        if (command.isPresent() && args.isPresent()) {
            return new AbstractMap.SimpleEntry<>(command.get(), expand(args.get()));
        }

        // possibly other solutions like the sun.java.command system property,
        // but that does not delineate single args containing spaces

        return null;
    }

    /**
     * @see com.sun.tools.javac.main.CommandLine#appendParsedCommandArgs(java.util.List, java.util.List)
     */
    private static String[] expand(String[] rawArgs) throws IOException {
        List<String> newArgs = new ArrayList<>(rawArgs.length);
        for (String arg : rawArgs) {
            if (arg.length() > 1 && arg.charAt(0) == '@') {
                arg = arg.substring(1);
                if (arg.charAt(0) == '@') {
                    newArgs.add(arg);
                }
                else {
                    loadCmdFile(arg, newArgs);
                }
            }
            else {
                newArgs.add(arg);
            }
        }
        return newArgs.toArray(new String[newArgs.size()]);
    }

    private static void loadCmdFile(String name, List<String> args) throws IOException {
        try (Reader r = Files.newBufferedReader(Paths.get(name))) {
            Tokenizer t = new Tokenizer(r);
            String s;
            while ((s = t.nextToken()) != null) {
                args.add(s);
            }
        }
    }

    /**
     * @see com.sun.tools.javac.main.CommandLine.Tokenizer
     */
    public static class Tokenizer {
        private final Reader in;
        private int ch;

        public Tokenizer(Reader in) throws IOException {
            this.in = in;
            ch = in.read();
        }

        public String nextToken() throws IOException {
            skipWhite();
            if (ch == -1) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            char quoteChar = 0;

            while (ch != -1) {
                switch (ch) {
                    case ' ':
                    case '\t':
                    case '\f':
                        if (quoteChar == 0) {
                            return sb.toString();
                        }
                        sb.append((char) ch);
                        break;

                    case '\n':
                    case '\r':
                        return sb.toString();

                    case '\'':
                    case '"':
                        if (quoteChar == 0) {
                            quoteChar = (char) ch;
                        } else if (quoteChar == ch) {
                            quoteChar = 0;
                        } else {
                            sb.append((char) ch);
                        }
                        break;

                    case '\\':
                        if (quoteChar != 0) {
                            ch = in.read();
                            switch (ch) {
                                case '\n':
                                case '\r':
                                    while (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f') {
                                        ch = in.read();
                                    }
                                    continue;

                                case 'n':
                                    ch = '\n';
                                    break;
                                case 'r':
                                    ch = '\r';
                                    break;
                                case 't':
                                    ch = '\t';
                                    break;
                                case 'f':
                                    ch = '\f';
                                    break;
                            }
                        }
                        sb.append((char) ch);
                        break;

                    default:
                        sb.append((char) ch);
                }

                ch = in.read();
            }

            return sb.toString();
        }

        void skipWhite() throws IOException {
            while (ch != -1) {
                switch (ch) {
                    case ' ':
                    case '\t':
                    case '\n':
                    case '\r':
                    case '\f':
                        break;

                    case '#':
                        ch = in.read();
                        while (ch != '\n' && ch != '\r' && ch != -1) {
                            ch = in.read();
                        }
                        break;

                    default:
                        return;
                }

                ch = in.read();
            }
        }
    }
}
