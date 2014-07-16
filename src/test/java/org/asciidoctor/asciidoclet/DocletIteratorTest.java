package org.asciidoctor.asciidoclet;

import com.sun.javadoc.*;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 * @author John Ericksen
 */
public class DocletIteratorTest {

    private DocletRenderer mockRenderer;
    private RootDoc mockDoc;
    private ClassDoc mockClassDoc;
    private PackageDoc mockPackageDoc;
    private FieldDoc mockFieldDoc;
    private ConstructorDoc mockConstructorDoc;
    private MethodDoc mockMethodDoc;

    @Before
    public void setup(){
        mockRenderer = mock(DocletRenderer.class);

        mockDoc = mock(RootDoc.class);
        mockPackageDoc = mock(PackageDoc.class);
        mockFieldDoc = mock(FieldDoc.class);
        mockConstructorDoc = mock(ConstructorDoc.class);
        mockMethodDoc = mock(MethodDoc.class);
        mockClassDoc = mockClassDoc(ClassDoc.class, mockPackageDoc, mockFieldDoc, mockConstructorDoc, mockMethodDoc);

        when(mockDoc.classes()).thenReturn(new ClassDoc[]{mockClassDoc});
        when(mockDoc.options()).thenReturn(new String[][]{});
    }

    private <T extends ClassDoc> T mockClassDoc(Class<T> type, PackageDoc packageDoc, FieldDoc fieldDoc, ConstructorDoc constructorDoc, MethodDoc methodDoc) {
        T classDoc = mock(type);
        when(classDoc.containingPackage()).thenReturn(packageDoc);
        when(classDoc.fields()).thenReturn(new FieldDoc[]{fieldDoc});
        when(classDoc.constructors()).thenReturn(new ConstructorDoc[]{constructorDoc});
        when(classDoc.methods()).thenReturn(new MethodDoc[]{methodDoc});
        return classDoc;
    }

    @Test
    public void testIteration(){
        new DocletIterator(DocletOptions.NONE).render(mockDoc, mockRenderer);

        verify(mockRenderer).renderDoc(mockClassDoc);
        verify(mockRenderer).renderDoc(mockFieldDoc);
        verify(mockRenderer).renderDoc(mockConstructorDoc);
        verify(mockRenderer).renderDoc(mockMethodDoc);
        verify(mockRenderer).renderDoc(mockPackageDoc);
    }

    @Test
    public void testAnnotationIteration(){
        AnnotationTypeDoc mockClassDoc = mockClassDoc(AnnotationTypeDoc.class, mockPackageDoc, mockFieldDoc, mockConstructorDoc, mockMethodDoc);
        AnnotationTypeElementDoc mockAnnotationElement = mock(AnnotationTypeElementDoc.class);

        when(mockDoc.classes()).thenReturn(new ClassDoc[]{mockClassDoc});
        when(mockClassDoc.elements()).thenReturn(new AnnotationTypeElementDoc[]{mockAnnotationElement});

        new DocletIterator(DocletOptions.NONE).render(mockDoc, mockRenderer);

        verify(mockRenderer).renderDoc(mockClassDoc);
        verify(mockRenderer).renderDoc(mockAnnotationElement);
    }

    @Test
    public void testIgnoreNonAsciidocOverview() {
        DocletIterator iterator = new DocletIterator(new DocletOptions(new String[][] {{DocletOptions.OVERVIEW, "foo.html"}}));

        assertTrue(iterator.render(mockDoc, mockRenderer));
        verify(mockDoc, never()).setRawCommentText(any(String.class));
    }

    @Test
    public void testFailIfAsciidocOverviewNotFound() {
        DocletIterator iterator = new DocletIterator(new DocletOptions(new String[][] {{DocletOptions.OVERVIEW, "notfound.adoc"}}));

        assertFalse(iterator.render(mockDoc, mockRenderer));
    }

    @Test
    public void testOverviewFound() {
        DocletIterator iterator = new DocletIterator(new DocletOptions(new String[][] {{DocletOptions.OVERVIEW, "src/main/java/overview.adoc"}}));
        assertTrue(iterator.render(mockDoc, mockRenderer));
        verify(mockRenderer).renderDoc(mockDoc);
    }
}
