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
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.UpdateSourcePositions;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UpdateSourcePositions} must give every element a {@link Range}, including elements beneath
 * a destructuring pattern.
 * <p>
 * {@code JavaPrinter.visitVariable} previously visited {@code variable.getName()}, which returns a
 * derived value — the first nested identifier, or a synthesized {@code "<dynamic>"} identifier that
 * is not part of the tree. Any {@code VariableDeclarator} that is not a plain {@code J.Identifier} —
 * every destructuring form — therefore had its entire subtree skipped by the printer, so those nodes
 * never entered the position map.
 */
class UpdateSourcePositionsDestructuringTest {

    @Language("ts")
    private static final String DESTRUCTURING =
            "function pick(): number { return 1; }\n" +
            "export function destructure({ a = pick() }: { a?: number } = {}): number {\n" +
            "  return a;\n" +
            "}\n";

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void everyElementBeneathADestructuringPatternIsPositioned() {
        SourceFile cu = JavaScriptParser.builder().build()
                .parse(new InMemoryExecutionContext(Throwable::printStackTrace), DESTRUCTURING)
                .collect(Collectors.toList())
                .get(0);

        TreeVisitor<?, ExecutionContext> positions = new UpdateSourcePositions().getVisitor();
        Tree positioned = positions.visit(cu, new InMemoryExecutionContext());

        List<String> unpositioned = new ArrayList<>();
        new TreeVisitor<Tree, Integer>() {
            @Override
            public Tree visit(Tree tree, Integer p) {
                if (tree instanceof J && !((J) tree).getMarkers().findFirst(Range.class).isPresent()) {
                    unpositioned.add(tree.getClass().getSimpleName());
                }
                return super.visit(tree, p);
            }
        }.visit(positioned, 0);

        // Before the fix: ObjectBindingPattern, BindingElement, the MethodInvocation for pick(),
        // and the identifiers beneath them were all absent from the position map.
        assertThat(unpositioned)
                .as("every element beneath the destructuring pattern should carry a Range")
                .doesNotContain("ObjectBindingPattern", "BindingElement", "MethodInvocation");
    }
}
