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

import java.util.Collections;
import java.util.List;
import javax.lang.model.element.Name;
import jdk.javadoc.doclet.DocletEnvironment;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTreeVisitor;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author John Ericksen
 */
public class AsciidoctorRendererTest {

    private DocletEnvironment mockEnvironment;
    private Asciidoctor mockAsciidoctor;
    private Options options;

    @Before
    public void setup() {
        mockEnvironment = mock(DocletEnvironment.class);
        mockAsciidoctor = mock(Asciidoctor.class);
        options = OptionsBuilder.options().safe(SafeMode.SAFE).backend("html5").get();
    }

    @Test
    public void testTagRender() {
        String commentText = "input";
        String tagName = "tagName";
        String tagText = "tagText";
        String asciidoctorRenderedString = "rendered";

        // comment body
        TextTree mockCommentText = mockText(commentText);

        // some weird tag
        TextTree mockTagText = mockText(tagText);
        UnknownBlockTagTree mockUnknown = mockTree(UnknownBlockTagTree.class, DocTree.Kind.UNKNOWN_BLOCK_TAG, DocTreeVisitor::visitUnknownBlockTag);
        when(mockUnknown.getTagName()).thenReturn(tagName);
        doReturn(Collections.singletonList(mockTagText)).when(mockUnknown).getContent();

        // comment tree
        DocCommentTree mockTree = mockCommentTree(Collections.singletonList(mockCommentText), Collections.singletonList(mockUnknown));

        // asciidoctor does some transformations
        when(mockAsciidoctor.render(eq(commentText), argThat(new OptionsMatcher(false)))).thenReturn(commentText);
        when(mockAsciidoctor.render(eq(tagText), argThat(new OptionsMatcher(true)))).thenReturn(asciidoctorRenderedString);

        // render
        String rendered = newRenderer(mockTree).render();

        // check results
        verify(mockAsciidoctor).render(eq(tagText), argThat(new OptionsMatcher(true)));
        verify(mockAsciidoctor).render(eq("input"), argThat(new OptionsMatcher(false)));
        assertEquals(commentText + "\n@" + tagName + " " + asciidoctorRenderedString, rendered);
    }

    @Test
    public void testCleanInput() {
        assertEquals("test1\ntest2", DocCommentRenderer.cleanJavadocInput("  test1\n test2\n"));
        assertEquals("@", DocCommentRenderer.cleanJavadocInput("{@literal @}"));
        assertEquals("/*\ntest\n*/", DocCommentRenderer.cleanJavadocInput("/*\ntest\n*\\/"));
        assertEquals("&#64;", DocCommentRenderer.cleanJavadocInput("{at}"));
        assertEquals("/", DocCommentRenderer.cleanJavadocInput("{slash}"));
    }

    @Test
    public void testParamTagWithTypeParameter() {
        String commentText = "comment";
        String param1Name = "T";
        String param1Desc = "";
        String param1Text = "<" + param1Name + ">";
        String param2Name = "X";
        String param2Desc = "description";
        String param2Text = "<" + param2Name + "> " + param2Desc;
        String sourceText = commentText + "\n@param " + param1Text + "\n@param " + param2Text;

        // comment body
        TextTree mockCommentText = mockText(commentText);

        // param 1
        Name mockParam1Name = mock(Name.class);
        when(mockParam1Name.toString()).thenReturn(param1Name);
        IdentifierTree mockParam1Type = mockTree(IdentifierTree.class, DocTree.Kind.IDENTIFIER, DocTreeVisitor::visitIdentifier);
        when(mockParam1Type.getName()).thenReturn(mockParam1Name);

        ParamTree mockParam1 = mockTree(ParamTree.class, DocTree.Kind.PARAM, DocTreeVisitor::visitParam);
        when(mockParam1.isTypeParameter()).thenReturn(true);
        when(mockParam1.getName()).thenReturn(mockParam1Type);

        // param 2
        Name mockParam2Name = mock(Name.class);
        when(mockParam2Name.toString()).thenReturn(param2Name);
        IdentifierTree mockParam2Type = mockTree(IdentifierTree.class, DocTree.Kind.IDENTIFIER, DocTreeVisitor::visitIdentifier);
        when(mockParam2Type.getName()).thenReturn(mockParam2Name);

        ParamTree mockParam2 = mockTree(ParamTree.class, DocTree.Kind.PARAM, DocTreeVisitor::visitParam);
        when(mockParam2.isTypeParameter()).thenReturn(true);
        when(mockParam2.getName()).thenReturn(mockParam2Type);
        TextTree mockParam2Desc = mockText(param2Desc);
        doReturn(Collections.singletonList(mockParam2Desc)).when(mockParam2).getDescription();

        // comment tree
        DocCommentTree mockTree = mockCommentTree(Collections.singletonList(mockCommentText), List.of(mockParam1, mockParam2));

        // asciidoctor just returns what it's given
        when(mockAsciidoctor.render(anyString(), any(Options.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // render
        String rendered = newRenderer(mockTree).render();

        // check results
        verify(mockAsciidoctor).render(eq(commentText), argThat(new OptionsMatcher(false)));
        verify(mockAsciidoctor).render(eq(param2Desc), argThat(new OptionsMatcher(true)));
        assertEquals(sourceText, rendered);
    }


    private DocCommentRenderer newRenderer(DocCommentTree commentTree) {
        return new DocCommentRenderer(mockEnvironment, mockAsciidoctor, options, null, commentTree);
    }

    private <T extends DocTree> T mockTree(Class<T> treeType, DocTree.Kind kind, Visitor<T> visitor) {
        T mockTree = mock(treeType);
        when(mockTree.getKind()).thenReturn(kind);
        doAnswer(new VisitorAdaptor<>(visitor)).when(mockTree).accept(any(), any());
        return mockTree;
    }

    private TextTree mockText(String text) {
        TextTree mockText = mockTree(TextTree.class, DocTree.Kind.TEXT, DocTreeVisitor::visitText);
        when(mockText.getBody()).thenReturn(text);
        return mockText;
    }

    private DocCommentTree mockCommentTree(List<DocTree> body, List<DocTree> tags) {
        DocCommentTree mockTree = mockTree(DocCommentTree.class, DocTree.Kind.DOC_COMMENT, DocTreeVisitor::visitDocComment);
        doReturn(body).when(mockTree).getFullBody();
        doReturn(tags).when(mockTree).getBlockTags();
        return mockTree;
    }


    private interface Visitor<T extends DocTree> {
        public void visit(DocTreeVisitor<Object,Object> visitor, T node, Object arg);
    }

    private static final class VisitorAdaptor<T extends DocTree> implements Answer {
        VisitorAdaptor(Visitor<T> visitor) {
            this.visitor = visitor;
        }

        @Override
        public final Object answer(InvocationOnMock invocation) {
            @SuppressWarnings("unchecked")
            T mock = (T)invocation.getMock();
            Object[] args = invocation.getArguments();
            @SuppressWarnings("unchecked")
            DocTreeVisitor<Object,Object> docTreeVisitor = (DocTreeVisitor<Object,Object>)args[0];
            visitor.visit(docTreeVisitor, mock, args[1]);
            return null;
        }

        private final Visitor<T> visitor;
    }

    private static final class OptionsMatcher implements ArgumentMatcher<Options> {

        private final boolean inline;

        private OptionsMatcher(boolean inline) {
            this.inline = inline;
        }

        @Override
        public boolean matches(Options options) {
            return !inline || (options.map().get(Options.DOCTYPE).equals(AsciidoctorRenderer.INLINE_DOCTYPE));
        }
    }
}
