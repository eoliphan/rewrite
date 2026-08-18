/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.javascript;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A declared {@code any}, a declared {@code unknown}, and a genuine type-attribution failure are
 * three different situations and must be distinguishable.
 * <p>
 * All three previously produced the same payload-free unknown value, so a consumer could not tell
 * "the author deliberately opted out of typing" from "we failed to type this" — the two demand
 * opposite responses.
 */
class AnyVersusUnresolvedTypeTest {

    @Language("ts")
    private static final String SOURCE =
            "export function takesAny(a: any): void { void a; }\n" +
            "export function takesUnknown(u: unknown): void { void u; }\n" +
            "export function takesBroken(b: NoSuchTypeAnywhere): void { void b; }\n";

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void declaredAnyDeclaredUnknownAndAttributionFailureAreDistinct() {
        SourceFile cu = JavaScriptParser.builder().build()
                .parse(new InMemoryExecutionContext(Throwable::printStackTrace), SOURCE)
                .collect(Collectors.toList())
                .get(0);

        Map<String, String> paramTypes = new LinkedHashMap<>();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree visit(Tree tree, Integer p) {
                if (tree instanceof J.VariableDeclarations.NamedVariable) {
                    J.VariableDeclarations.NamedVariable nv = (J.VariableDeclarations.NamedVariable) tree;
                    JavaType t = nv.getVariableType() == null ? null : nv.getVariableType().getType();
                    paramTypes.put(nv.getSimpleName(), t instanceof JavaType.FullyQualified
                            ? ((JavaType.FullyQualified) t).getFullyQualifiedName() : String.valueOf(t));
                }
                return super.visit(tree, p);
            }
        }.visit(cu, 0);

        assertThat(paramTypes).containsKeys("a", "u", "b");
        assertThat(paramTypes.get("b"))
                .as("an unresolvable type is still an attribution failure")
                .isEqualTo("<unknown>");
        assertThat(paramTypes.values())
                .as("declared any, declared unknown, and a failure must be three distinct values")
                .doesNotHaveDuplicates();
    }
}
