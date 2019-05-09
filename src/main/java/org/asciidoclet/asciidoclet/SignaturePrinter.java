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

import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import static javax.lang.model.element.ElementKind.ANNOTATION_TYPE;
import static javax.lang.model.element.ElementKind.ENUM_CONSTANT;
import static javax.lang.model.element.ElementKind.PARAMETER;

/**
 * Content extracted from {@link com.sun.tools.javac.processing.PrintingProcessor.PrintingElementVisitor}.
 */
public class SignaturePrinter {
    public static void printModifiers(Element e, PrintWriter pw) {
        ElementKind kind = e.getKind();
        if (kind == PARAMETER) {
            printAnnotationsInline(e, pw);
        }
        else {
            printAnnotations(e, pw);
        }

        if (kind != ENUM_CONSTANT) {
            Set<Modifier> modifiers = new LinkedHashSet<>(e.getModifiers());

            switch (kind) {
                case ANNOTATION_TYPE:
                case INTERFACE:
                    modifiers.remove(Modifier.ABSTRACT);
                    break;

                case ENUM:
                    modifiers.remove(Modifier.FINAL);
                    modifiers.remove(Modifier.ABSTRACT);
                    break;

                case METHOD:
                case FIELD:
                    Element enclosingElement = e.getEnclosingElement();
                    if (enclosingElement != null &&
                        enclosingElement.getKind().isInterface()) {
                        modifiers.remove(Modifier.PUBLIC);
                        modifiers.remove(Modifier.ABSTRACT); // only for methods
                        modifiers.remove(Modifier.STATIC);   // only for fields
                        modifiers.remove(Modifier.FINAL);    // only for fields
                    }
                    break;

            }

            for (Modifier m: modifiers) {
                pw.print(m);
                pw.print(' ');
            }
        }
    }

    public static void printFormalTypeParameters(Parameterizable e, boolean pad, PrintWriter pw) {
        List<? extends TypeParameterElement> typeParams = e.getTypeParameters();
        if (typeParams.size() > 0) {
            pw.print('<');

            boolean first = true;
            for(TypeParameterElement tpe: typeParams) {
                if (!first) pw.print(", ");
                printAnnotationsInline(tpe, pw);
                pw.print(tpe.toString());
                first = false;
            }

            pw.print('>');
            if (pad) pw.print(' ');
        }
    }

    private static void printAnnotationsInline(Element e, PrintWriter pw) {
        for (AnnotationMirror annotationMirror : e.getAnnotationMirrors()) {
            pw.print(annotationMirror);
            pw.print(' ');
        }
    }

    private static void printAnnotations(Element e, PrintWriter pw) {
        for (AnnotationMirror annotationMirror : e.getAnnotationMirrors()) {
            pw.println(annotationMirror);
        }
    }

    public static void printParameters(ExecutableElement e, PrintWriter pw) {
        List<? extends VariableElement> parameters = e.getParameters();
        int size = parameters.size();

        switch (size) {
            case 0:
                break;

            case 1:
                for (VariableElement parameter: parameters) {
                    printModifiers(parameter, pw);

                    if (e.isVarArgs()) {
                        TypeMirror tm = parameter.asType();
                        if (tm.getKind() != TypeKind.ARRAY)
                            throw new AssertionError("Var-args parameter is not an array type: " + tm);
                        pw.print((ArrayType.class.cast(tm)).getComponentType());
                        pw.print("...");
                    }
                    else {
                        pw.print(parameter.asType());
                    }
                    pw.print(' ');
                    pw.print(parameter.getSimpleName());
                }
                break;

            default:
                int i = 1;
                for(VariableElement parameter: parameters) {
                    printModifiers(parameter, pw);

                    if (i == size && e.isVarArgs() ) {
                        TypeMirror tm = parameter.asType();
                        if (tm.getKind() != TypeKind.ARRAY)
                            throw new AssertionError("Var-args parameter is not an array type: " + tm);
                        pw.print((ArrayType.class.cast(tm)).getComponentType() );

                        pw.print("...");
                    }
                    else {
                        pw.print(parameter.asType());
                    }
                    pw.print(' ');
                    pw.print(parameter.getSimpleName());
                    if (i < size) pw.print(", ");
                    i++;
                }
                break;
        }
    }

    public static void printInterfaces(TypeElement e, PrintWriter pw) {
        ElementKind kind = e.getKind();
        if (kind != ANNOTATION_TYPE) {
            List<? extends TypeMirror> interfaces = e.getInterfaces();
            if (interfaces.size() > 0) {
                pw.print(kind.isClass() ? " implements" : " extends");

                boolean first = true;
                for (TypeMirror interf: interfaces) {
                    if (!first) pw.print(',');
                    pw.print(' ');
                    pw.print(interf.toString());
                    first = false;
                }
            }
        }
    }

    public static void printThrows(ExecutableElement e, PrintWriter pw) {
        List<? extends TypeMirror> thrownTypes = e.getThrownTypes();
        int size = thrownTypes.size();
        if (size != 0) {
            pw.print(" throws");

            int i = 1;
            for (TypeMirror thrownType: thrownTypes) {
                if (i == 1) pw.print(' ');
                pw.print(thrownType);
                if (i < size) pw.print(", ");
                i++;
            }
        }
    }
}
