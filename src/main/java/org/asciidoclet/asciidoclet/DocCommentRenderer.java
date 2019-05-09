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

import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.AuthorTree;
import com.sun.source.doctree.CommentTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocRootTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTreeVisitor;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.HiddenTree;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ProvidesTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SerialDataTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SerialTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.doctree.UsesTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.doctree.VersionTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import jdk.javadoc.doclet.DocletEnvironment;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;

public class DocCommentRenderer {
    public DocCommentRenderer(DocletEnvironment environment, Asciidoctor asciidoctor, Options options, TreePath path, DocCommentTree comment) {
        this.docTrees = environment.getDocTrees();
        this.asciidoctor = asciidoctor;
        this.options = options;
        this.path = path;
        this.comment = comment;
    }

    public String render() {
        try {
            new DocTreeVisitorImpl().visit(comment);
            assert unprocessed.length() == 0;
            return rendered.toString();
        }
        finally {
            rendered.setLength(0);
        }
    }

    public static String render(Asciidoctor asciidoctor, Options options, String text) {
        return render(asciidoctor, options, text, false);
    }

    /**
     * Rendering output format based on {@linkplain com.sun.tools.javac.tree.DocPretty}.
     *
     * This class uses two string buffers:
     *
     * - an unprocessed text buffer whose contents will be passed to asciidoctor when appropriate
     * - a processed text buffer containing asciidoctor-rendered text along with any other raw text
     *   that should not be asciidoctor-fied.
     *
     * Text is initially output to the processed buffer.
     *
     * A call to {@link #startBuffering()} will switch to appending future text to the unprocessed
     * buffer.
     *
     * A call to {@link #renderBuffer(boolean)} will run asciidoctor over the current unprocessed
     * buffer content and the processed output will be appended to the processed text buffer.
     * This method will also switch to appending future text back to the processed text buffer.
     */
    private final class DocTreeVisitorImpl implements DocTreeVisitor<Void,Void> {
        /**
         * Invokes the appropriate visit method specific to the type of the node.
         * @param node the node on which to dispatch
         * @return the value returns from the appropriate visit method
         */
        private Void visit(DocTree node) {
            return (node == null) ? null : node.accept(this, null);
        }

        /**
         * Invokes the appropriate visit method on each of a sequence of nodes.
         * @param nodes the nodes on which to dispatch
         * @return the value return from the last of the visit methods, or null
         *      if none were called.
         */
        private Void visit(Iterable<? extends DocTree> nodes) {
            if (nodes != null) {
                for (DocTree node : nodes) visit(node);
            }
            return null;
        }

        @Override
        public Void visitAttribute(AttributeTree node, Void p) {
            print(node.getName());
            String quote;
            switch (node.getValueKind()) {
                case EMPTY:
                    quote = null;
                    break;
                case UNQUOTED:
                    quote = "";
                    break;
                case SINGLE:
                    quote = "'";
                    break;
                case DOUBLE:
                    quote = "\"";
                    break;
                default:
                    throw new IllegalArgumentException("Unhandled enum constant: " + node.getValueKind());
            }
            if (quote != null) {
                print('=');
                print(quote);
                visit(node.getValue());
                print(quote);
            }
            return null;
        }

        @Override
        public Void visitAuthor(AuthorTree node, Void p) {
            printTagName(node);
            if (!node.getName().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getName());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitComment(CommentTree node, Void p) {
            print(node.getBody());
            return null;
        }

        @Override
        public Void visitDeprecated(DeprecatedTree node, Void p) {
            printTagName(node);
            if (!node.getBody().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getBody());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitDocComment(DocCommentTree node, Void p) {
            List<? extends DocTree> body = node.getFullBody();
            if (!body.isEmpty()) {
                startBuffering();
                visit(body);
                renderBuffer(false);
            }
            List<? extends DocTree> tags = node.getBlockTags();
            if (!body.isEmpty() && !tags.isEmpty()) print('\n');
            boolean first = true;
            for (DocTree tag : tags) {
                if (!first) print('\n');
                visit(tag);
                first = false;
            }
            return null;
        }

        @Override
        public Void visitDocRoot(DocRootTree node, Void p) {
            print('{');
            printTagName(node);
            print('}');
            return null;
        }

        @Override
        public Void visitEndElement(EndElementTree node, Void p) {
            print("</");
            print(node.getName());
            print('>');
            return null;
        }

        @Override
        public Void visitEntity(EntityTree node, Void p) {
            print('&');
            print(node.getName());
            print(';');
            return null;
        }

        @Override
        public Void visitErroneous(ErroneousTree node, Void p) {
            print(node.getBody());
            return null;
        }

        @Override
        public Void visitHidden(HiddenTree node, Void p) {
            printTagName(node);
            if (!node.getBody().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getBody());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void p) {
            print(node.getName());
            return null;
        }

        @Override
        public Void visitIndex(IndexTree node, Void p) {
            print('{');
            printTagName(node);
            printSpace();
            visit(node.getSearchTerm());
            if (!node.getDescription().isEmpty()) {
                printSpace();
                visit(node.getDescription());
            }
            print('}');
            return null;
        }

        @Override
        public Void visitInheritDoc(InheritDocTree node, Void p) {
            print('{');
            printTagName(node);
            print('}');
            return null;
        }

        @Override
        public Void visitLink(LinkTree node, Void p) {
            print('{');
            printTagName(node);
            printSpace();
            visit(node.getReference());
            if (!node.getLabel().isEmpty()) {
                printSpace();
                visit(node.getLabel());
            }
            print('}');
            return null;
        }

        @Override
        public Void visitLiteral(LiteralTree node, Void p) {
            print('{');
            printTagName(node);
            String body = node.getBody().getBody();
            if (!body.isEmpty() && !Character.isWhitespace(body.charAt(0))) {
                printSpace();
            }
            visit(node.getBody());
            print('}');
            return null;
        }

        @Override
        public Void visitParam(ParamTree node, Void p) {
            printTagName(node);
            printSpace();
            if (node.isTypeParameter()) print('<');
            visit(node.getName());
            if (node.isTypeParameter()) print('>');
            if (!node.getDescription().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getDescription());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitProvides(ProvidesTree node, Void p) {
            printTagName(node);
            printSpace();
            visit(node.getServiceType());
            if (!node.getDescription().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getDescription());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitReference(ReferenceTree node, Void p) {
            // pre-processing loses all the imports in the source,
            // so output references fully-qualified
            Element resolved = docTrees.getElement(DocTreePath.getPath(path, comment, node));
            if (resolved != null) {
                TypeElement type = getReferencedType(resolved);
                String member = getReferencedMember(node);
                if (type != null) print(type);
                if (member != null) {
                    print('#');
                    print(member);
                }
            }
            else {
                print(node);
            }
            return null;
        }

        /**
         * @see jdk.javadoc.internal.doclets.toolkit.util.CommentHelper#getReferencedClass(jdk.javadoc.internal.doclets.toolkit.BaseConfiguration, DocTree).
         * @see jdk.javadoc.internal.doclets.toolkit.util.Utils#getEnclosingTypeElement(Element).
         */
        private TypeElement getReferencedType(Element e) {
            if (e instanceof TypeElement) {
                return (TypeElement)e;
            }
            if (e.getKind() != ElementKind.PACKAGE) {
                Element enclosing = e.getEnclosingElement();
                ElementKind kind = enclosing.getKind();
                if (kind != ElementKind.PACKAGE) {
                    while (!kind.isClass() && !kind.isInterface()) {
                        enclosing = enclosing.getEnclosingElement();
                        kind = enclosing.getKind();
                    }
                }
                return (TypeElement)enclosing;
            }
            return null;
        }

        private String getReferencedMember(ReferenceTree ref) {
            String sig = ref.getSignature();
            int n = sig.indexOf("#");
            return n >= 0 ? sig.substring(n+1) : null;
        }

        @Override
        public Void visitReturn(ReturnTree node, Void p) {
            printTagName(node);
            if (node.getDescription() != null) {
                printSpace();
                startBuffering();
                visit(node.getDescription());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitSee(SeeTree node, Void p) {
            printTagName(node);
            List<? extends DocTree> ref = node.getReference();
            if (!ref.isEmpty()) {
                Iterator<? extends DocTree> i = ref.iterator();
                // peek at the first element without removing it yet
                DocTree first = ref.get(0);
                if (first.getKind() == DocTree.Kind.REFERENCE) {
                    // form 3: @see package.class#member label
                    // print reference without reformatting
                    printSpace();
                    visit(i.next());
                }
                else if (first.getKind() == DocTree.Kind.START_ELEMENT) {
                    // form 2: @see <a href="...">label</a>
                    // print link start/end tags without reformatting
                    printSpace();
                    visit(i.next());
                    if (!((StartElementTree)first).isSelfClosing()) {
                        // content between the start/end tags needs to be formatted though
                        startBuffering();
                        while (i.hasNext()) {
                            DocTree next = i.next();
                            if (next.getKind() == DocTree.Kind.END_ELEMENT) {
                                // render the context we've accumulated
                                renderBuffer(true);
                                // print the end element without reformatting
                                visit(next);
                            }
                            else {
                                visit(next);
                            }
                        }
                        if (buffering) {
                            // unclosed link, render what text we have
                            renderBuffer(true);
                        }
                    }
                }

                // reformat any remaining content
                if (i.hasNext()) {
                    printSpace();
                    startBuffering();
                    while (i.hasNext()) visit(i.next());
                    renderBuffer(true);
                }
            }
            return null;
        }

        @Override
        public Void visitSerial(SerialTree node, Void p) {
            printTagName(node);
            if (!node.getDescription().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getDescription());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitSerialData(SerialDataTree node, Void p) {
            printTagName(node);
            if (!node.getDescription().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getDescription());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitSerialField(SerialFieldTree node, Void p) {
            printTagName(node);
            printSpace();
            visit(node.getName());
            printSpace();
            visit(node.getType());
            if (!node.getDescription().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getDescription());
                renderBuffer(buffering);
            }
            return null;
        }

        @Override
        public Void visitSince(SinceTree node, Void p) {
            printTagName(node);
            if (node.getBody() != null) {
                printSpace();
                startBuffering();
                visit(node.getBody());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitStartElement(StartElementTree node, Void p) {
            print('<');
            print(node.getName());
            List<? extends DocTree> attrs = node.getAttributes();
            if (!attrs.isEmpty()) {
                printSpace();
                visit(attrs);
                DocTree last = node.getAttributes().get(attrs.size() - 1);
                if (node.isSelfClosing() && last instanceof AttributeTree && ((AttributeTree)last).getValueKind() == AttributeTree.ValueKind.UNQUOTED) {
                    printSpace();
                }
            }
            if (node.isSelfClosing()) print('/');
            print('>');
            return null;
        }

        @Override
        public Void visitText(TextTree node, Void p) {
            print(node.getBody());
            return null;
        }

        @Override
        public Void visitThrows(ThrowsTree node, Void p) {
            printTagName(node);
            printSpace();
            visit(node.getExceptionName());
            if (!node.getDescription().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getDescription());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
            print('@');
            print(node.getTagName());
            if (node.getContent() != null) {
                printSpace();
                startBuffering();
                visit(node.getContent());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitUnknownInlineTag(UnknownInlineTagTree node, Void p) {
            print("{@");
            print(node.getTagName());
            printSpace();
            visit(node.getContent());
            print('}');
            return null;
        }

        @Override
        public Void visitUses(UsesTree node, Void p) {
            printTagName(node);
            printSpace();
            visit(node.getServiceType());
            if (!node.getDescription().isEmpty()) {
                printSpace();
                startBuffering();
                visit(node.getDescription());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitValue(ValueTree node, Void p) {
            print('{');
            printTagName(node);
            if (node.getReference() != null) {
                printSpace();
                visit(node.getReference());
            }
            print('}');
            return null;
        }

        @Override
        public Void visitVersion(VersionTree node, Void p) {
            printTagName(node);
            if (node.getBody() != null) {
                printSpace();
                startBuffering();
                visit(node.getBody());
                renderBuffer(true);
            }
            return null;
        }

        @Override
        public Void visitOther(DocTree node, Void p) {
            print("(UNKNOWN: " + node + ")");
            println();
            return null;
        }
    }

    private void printTagName(DocTree node) {
        print('@');
        print(node.getKind().tagName);
    }

    private void print(Object o) {
        print(escapeUnicode(o.toString()));
    }

    /**
     * @see {@link com.sun.tools.javac.util.Convert#escapeUnicode(String)}.
     */
    private String escapeUnicode(String s) {
        int len = s.length();
        int i = 0;
        while (i < len) {
            char ch = s.charAt(i);
            if (ch > 255) {
                StringBuilder buf = new StringBuilder();
                buf.append(s, 0, i);
                while (i < len) {
                    ch = s.charAt(i);
                    if (ch > 255) {
                        buf.append("\\u");
                        buf.append(Character.forDigit((ch >> 12) % 16, 16));
                        buf.append(Character.forDigit((ch >>  8) % 16, 16));
                        buf.append(Character.forDigit((ch >>  4) % 16, 16));
                        buf.append(Character.forDigit((ch      ) % 16, 16));
                    } else {
                        buf.append(ch);
                    }
                    i++;
                }
                s = buf.toString();
            }
            else {
                i++;
            }
        }
        return s;
    }

    private void printSpace() {
        print(' ');
    }

    private void println() {
        print(lineSep);
    }

    private void print(char c) {
        StringBuilder b = buffering ? unprocessed : rendered;
        b.append(c);
    }

    private void print(String s) {
        StringBuilder b = buffering ? unprocessed : rendered;
        b.append(s);
    }

    private void startBuffering() {
        buffering = true;
    }

    private void renderBuffer(boolean inline) {
        if (unprocessed.length() > 0) {
            rendered.append(render(asciidoctor, options, unprocessed.toString(), inline));
        }
        unprocessed.setLength(0);
        buffering = false;
    }

    /**
     * Renders the input using Asciidoctor.
     *
     * The source is first cleaned by stripping any trailing space after an
     * end line (e.g., `"\n "`), which gets left behind by the Javadoc
     * processor.
     *
     * @param input AsciiDoc source
     * @return content rendered by Asciidoctor
     */
    private static String render(Asciidoctor asciidoctor, Options options, String input, boolean inline) {
        input = input.trim();
        if (input.isEmpty()) return "";

        options.setDocType(inline ? INLINE_DOCTYPE : null);
        return asciidoctor.render(cleanJavadocInput(input), options);
    }

    protected static String cleanJavadocInput(String input) {
        return input.trim()
            .replaceAll("\n ", "\n") // Newline space to accommodate javadoc newlines.
            .replaceAll("\\{at}", "&#64;") // {at} is translated into @.
            .replaceAll("\\{slash}", "/") // {slash} is translated into /.
            .replaceAll("(?m)^( *)\\*\\\\/$", "$1*/") // Multi-line comment end tag is translated into */.
            .replaceAll("\\{@literal (.*?)}", "$1"); // {@literal _} is translated into _ (standard javadoc).
    }




    private final DocTrees docTrees;
    private final Asciidoctor asciidoctor;
    private final TreePath path;
    private final DocCommentTree comment;

    private final StringBuilder unprocessed = new StringBuilder();
    private final StringBuilder rendered = new StringBuilder();
    private boolean buffering;

    private final Options options;

    protected static final String INLINE_DOCTYPE = "inline";

    private static final String lineSep = System.getProperty("line.separator");
}
