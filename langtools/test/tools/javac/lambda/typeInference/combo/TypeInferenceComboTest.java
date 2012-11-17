/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8003280
 * @summary Add lambda tests
 *  perform automated checks in type inference in lambda expressions in different contexts
 * @compile  TypeInferenceComboTest.java
 * @run main/timeout=360 TypeInferenceComboTest
 */

import com.sun.source.util.JavacTask;
import java.net.URI;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.StandardJavaFileManager;

public class TypeInferenceComboTest {
    enum Context {
        ASSIGNMENT("SAM#Type s = #LBody;"),
        METHOD_CALL("#GenericDeclKind void method1(SAM#Type s) { }\n" +
                    "void method2() {\n" +
                    "    method1(#LBody);\n" +
                    "}"),
        RETURN_OF_METHOD("SAM#Type method1() {\n" +
                "    return #LBody;\n" +
                "}"),
        LAMBDA_RETURN_EXPRESSION("SAM2 s2 = () -> {return (SAM#Type)#LBody;};\n"),
        ARRAY_INITIALIZER("Object[] oarray = {\"a\", 1, (SAM#Type)#LBody};");

        String context;

        Context(String context) {
            this.context = context;
        }

        String getContext(SamKind sk, TypeKind samTargetT, Keyword kw, TypeKind parameterT, TypeKind returnT, LambdaKind lk, ParameterKind pk, GenericDeclKind gdk, LambdaBody lb) {
            String result = context;
            if (sk == SamKind.GENERIC) {
                if(this == Context.METHOD_CALL) {
                    result = result.replaceAll("#GenericDeclKind", gdk.getGenericDeclKind(samTargetT));
                    if(gdk == GenericDeclKind.NON_GENERIC)
                        result = result.replaceAll("#Type", "<" + samTargetT.typeStr + ">");
                    else //#GenericDeclKind is <T> or <T extends xxx>
                        result = result.replaceAll("#Type", "<T>");
                }
                else {
                    if(kw == Keyword.VOID)
                        result = result.replaceAll("#Type", "<" + samTargetT.typeStr + ">");
                    else
                        result = result.replaceAll("#Type", "<? " + kw.keyStr + " " + samTargetT.typeStr + ">");
                }
            }
            else
                result = result.replaceAll("#Type", "").replaceAll("#GenericDeclKind", "");

            return result.replaceAll("#LBody", lb.getLambdaBody(samTargetT, parameterT, returnT, lk, pk));
        }
    }

    enum SamKind {
        GENERIC("interface SAM<T> { #R m(#ARG); }"),
        NON_GENERIC("interface SAM { #R m(#ARG); }");

        String sam_str;

        SamKind(String sam_str) {
            this.sam_str = sam_str;
        }

        String getSam(TypeKind parameterT, TypeKind returnT) {
            return sam_str.replaceAll("#ARG", parameterT == TypeKind.VOID ? "" : parameterT.typeStr + " arg")
                          .replaceAll("#R", returnT.typeStr);
        }
    }

    enum TypeKind {
        VOID("void", ""),
        STRING("String", "\"hello\""),
        INTEGER("Integer", "1"),
        INT("int", "0"),
        COMPARATOR("java.util.Comparator<String>", "(java.util.Comparator<String>)(a, b) -> a.length()-b.length()"),
        SAM("SAM2", "null"),
        GENERIC("T", null);

        String typeStr;
        String valStr;

        TypeKind(String typeStr, String valStr) {
            this.typeStr = typeStr;
            this.valStr = valStr;
        }
    }

    enum LambdaKind {
        EXPRESSION("#VAL"),
        STATEMENT("{return #VAL;}");

        String stmt;

        LambdaKind(String stmt) {
            this.stmt = stmt;
        }
    }

    enum ParameterKind {
        EXPLICIT("#TYPE"),
        IMPLICIT("");

        String paramTemplate;

        ParameterKind(String paramTemplate) {
             this.paramTemplate = paramTemplate;
        }
    }

    enum Keyword {
        SUPER("super"),
        EXTENDS("extends"),
        VOID("");

        String keyStr;

        Keyword(String keyStr) {
            this.keyStr = keyStr;
        }
    }

    enum LambdaBody {
        RETURN_VOID("() -> #RET"),//no parameters, return type is one of the TypeKind
        RETURN_ARG("(#PK arg) -> #RET");//has parameters, return type is one of the TypeKind

        String bodyStr;

        LambdaBody(String bodyStr) {
            this.bodyStr = bodyStr;
        }

        String getLambdaBody(TypeKind samTargetT, TypeKind parameterT, TypeKind returnT, LambdaKind lk, ParameterKind pk) {
            String result = bodyStr.replaceAll("#PK", pk.paramTemplate);

            if(result.contains("#TYPE")) {
                if (parameterT == TypeKind.GENERIC && this != RETURN_VOID)
                    result = result.replaceAll("#TYPE", samTargetT == null? "": samTargetT.typeStr);
                else
                    result = result.replaceAll("#TYPE", parameterT.typeStr);
            }
            if (this == RETURN_ARG && parameterT == returnT)
                return result.replaceAll("#RET", lk.stmt.replaceAll("#VAL", "arg"));
            else {
                if(returnT != TypeKind.GENERIC)
                    return result.replaceAll("#RET", lk.stmt.replaceAll("#VAL", (returnT==TypeKind.VOID && lk==LambdaKind.EXPRESSION)? "{}" : returnT.valStr));
                else
                    return result.replaceAll("#RET", lk.stmt.replaceAll("#VAL", samTargetT.valStr));
            }
        }
    }

    enum GenericDeclKind {
        NON_GENERIC(""),
        GENERIC_NOBOUND("<T>"),
        GENERIC_BOUND("<T extends #ExtendedType>");
        String typeStr;

        GenericDeclKind(String typeStr) {
            this.typeStr = typeStr;
        }

        String getGenericDeclKind(TypeKind et) {
            return typeStr.replaceAll("#ExtendedType", et==null? "":et.typeStr);
        }
    }

    boolean checkTypeInference() {
        if (parameterType == TypeKind.VOID) {
            if (lambdaBodyType != LambdaBody.RETURN_VOID)
                return false;
        }
        else if (lambdaBodyType != LambdaBody.RETURN_ARG)
            return false;
        if (  genericDeclKind == GenericDeclKind.GENERIC_NOBOUND || genericDeclKind == GenericDeclKind.GENERIC_BOUND ) {
            if ( parameterType == TypeKind.GENERIC && parameterKind == ParameterKind.IMPLICIT) //cyclic inference
                return false;
        }
        return true;
    }

    String templateStr = "#C\n" +
                         "interface SAM2 {\n" +
                         "    SAM m();\n" +
                         "}\n";
    SourceFile samSourceFile = new SourceFile("Sam.java", templateStr) {
        public String toString() {
            return template.replaceAll("#C", samKind.getSam(parameterType, returnType));
        }
    };

    SourceFile clientSourceFile = new SourceFile("Client.java",
                                                 "class Client { \n" +
                                                 "    #Context\n" +
                                                 "}") {
        public String toString() {
            return template.replaceAll("#Context", context.getContext(samKind, samTargetType, keyword, parameterType, returnType, lambdaKind, parameterKind, genericDeclKind, lambdaBodyType));
        }
    };

    void test() throws Exception {
        System.out.println("kk:");
        StringBuilder sb = new StringBuilder("SamKind:");
        sb.append(samKind).append(" SamTargetType:").append(samTargetType).append(" ParameterType:").append(parameterType)
            .append(" ReturnType:").append(returnType).append(" Context:").append(context).append(" LambdaKind:").append(lambdaKind)
            .append(" LambdaBodyType:").append(lambdaBodyType).append(" ParameterKind:").append(parameterKind).append(" Keyword:").append(keyword);
        System.out.println(sb);
        DiagnosticChecker dc = new DiagnosticChecker();
        JavacTask ct = (JavacTask)comp.getTask(null, fm, dc, null, null, Arrays.asList(samSourceFile, clientSourceFile));
        ct.analyze();
        if (dc.errorFound == checkTypeInference()) {
            throw new AssertionError(samSourceFile + "\n\n" + clientSourceFile + "\n" + parameterType + " " + returnType);
        }
    }

    abstract class SourceFile extends SimpleJavaFileObject {

        protected String template;

        public SourceFile(String filename, String template) {
            super(URI.create("myfo:/" + filename), JavaFileObject.Kind.SOURCE);
            this.template = template;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return toString();
        }

        public abstract String toString();
    }

    static class DiagnosticChecker implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean errorFound = false;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errorFound = true;
            }
        }
    }

    SamKind samKind;
    TypeKind samTargetType;
    TypeKind parameterType;
    TypeKind returnType;
    Context context;
    LambdaBody lambdaBodyType;
    LambdaKind lambdaKind;
    ParameterKind parameterKind;
    Keyword keyword;
    GenericDeclKind genericDeclKind;

    static JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
    static StandardJavaFileManager fm = comp.getStandardFileManager(null, null, null);

    TypeInferenceComboTest(SamKind sk, TypeKind samTargetT, TypeKind parameterT, TypeKind returnT, LambdaBody lb, Context c, LambdaKind lk, ParameterKind pk, Keyword kw, GenericDeclKind gdk) {
        samKind = sk;
        samTargetType = samTargetT;
        parameterType = parameterT;
        returnType = returnT;
        context = c;
        lambdaKind = lk;
        parameterKind = pk;
        keyword = kw;
        lambdaBodyType = lb;
        genericDeclKind = gdk;
    }

    public static void main(String[] args) throws Exception {
        for(Context ct : Context.values()) {
            for (TypeKind returnT : TypeKind.values()) {
                for (TypeKind parameterT : TypeKind.values()) {
                    for(LambdaBody lb : LambdaBody.values()) {
                        for (ParameterKind parameterK : ParameterKind.values()) {
                            for(LambdaKind lambdaK : LambdaKind.values()) {
                                for (SamKind sk : SamKind.values()) {
                                    if (sk == SamKind.NON_GENERIC) {
                                        if(parameterT != TypeKind.GENERIC && returnT != TypeKind.GENERIC )
                                            new TypeInferenceComboTest(sk, null, parameterT, returnT, lb, ct, lambdaK, parameterK, null, null).test();
                                    }
                                    else if (sk == SamKind.GENERIC) {
                                        for (Keyword kw : Keyword.values()) {
                                            for (TypeKind samTargetT : TypeKind.values()) {
                                                if(samTargetT != TypeKind.VOID && samTargetT != TypeKind.INT && samTargetT != TypeKind.GENERIC
                                                   && (parameterT == TypeKind.GENERIC || returnT == TypeKind.GENERIC)) {
                                                    if(ct != Context.METHOD_CALL) {
                                                        new TypeInferenceComboTest(sk, samTargetT, parameterT, returnT, lb, ct, lambdaK, parameterK, kw, null).test();
                                                    }
                                                    else {//Context.METHOD_CALL
                                                        for (GenericDeclKind gdk : GenericDeclKind.values())
                                                            new TypeInferenceComboTest(sk, samTargetT, parameterT, returnT, lb, ct, lambdaK, parameterK, kw, gdk).test();
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
