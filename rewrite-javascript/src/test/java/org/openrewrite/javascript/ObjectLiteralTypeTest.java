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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An object literal must carry a resolvable type, and its members must resolve to it as their
 * declaring type.
 * <p>
 * Anonymous object types were mapped to the shared unknown type to avoid circular references, so an
 * object literal declared no type a consumer could reference and its members reported
 * {@code <unknown>} as their owner.
 */
class ObjectLiteralTypeTest {

    @Language("ts")
    private static final String TWO_LITERALS =
            "export const config = {\n" +
            "  handler(): void {\n" +
            "    return;\n" +
            "  },\n" +
            "};\n" +
            "export const other = {\n" +
            "  compute(): number {\n" +
            "    return 1;\n" +
            "  },\n" +
            "};\n";

    private static SourceFile parse(@Language("ts") String source) {
        return JavaScriptParser.builder().build()
                .parse(new InMemoryExecutionContext(Throwable::printStackTrace), source)
                .collect(Collectors.toList())
                .get(0);
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void objectLiteralsCarryDistinctTypesThatTheirMembersResolveTo() {
        SourceFile cu = parse(TWO_LITERALS);

        List<String> literalTypes = new ArrayList<>();
        List<String> memberOwners = new ArrayList<>();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree visit(Tree tree, Integer p) {
                if (tree instanceof J.NewClass) {
                    JavaType type = ((J.NewClass) tree).getType();
                    literalTypes.add(type instanceof JavaType.FullyQualified
                            ? ((JavaType.FullyQualified) type).getFullyQualifiedName() : null);
                } else if (tree instanceof J.MethodDeclaration) {
                    JavaType.Method mt = ((J.MethodDeclaration) tree).getMethodType();
                    memberOwners.add(mt == null || mt.getDeclaringType() == null
                            ? null : mt.getDeclaringType().getFullyQualifiedName());
                }
                return super.visit(tree, p);
            }
        }.visit(cu, 0);

        assertThat(literalTypes).as("both object literals should carry a type").hasSize(2).doesNotContainNull();
        assertThat(literalTypes).as("distinct literals must not share one type").doesNotHaveDuplicates();
        assertThat(memberOwners).as("members resolve to their own literal's type").isEqualTo(literalTypes);
    }
}
