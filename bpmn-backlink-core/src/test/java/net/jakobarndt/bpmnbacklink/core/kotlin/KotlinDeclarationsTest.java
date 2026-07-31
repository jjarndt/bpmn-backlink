/*
 * Copyright the bpmn-backlink authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.jakobarndt.bpmnbacklink.core.kotlin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shape recognition of the structural scan: which declarations exist,
 * what their supertypes are, and where their header begins.
 */
class KotlinDeclarationsTest {

    private List<KotlinDeclaration> parse(String source) {
        return KotlinDeclarations.parse(KotlinSanitizer.sanitize(source));
    }

    private List<String> names(String source) {
        return parse(source).stream().map(KotlinDeclaration::name).toList();
    }

    private KotlinDeclaration single(String source) {
        List<KotlinDeclaration> declarations = parse(source);
        assertEquals(1, declarations.size(), "expected exactly one declaration in:\n" + source);
        return declarations.get(0);
    }

    private List<String> supertypes(String source) {
        return single(source).supertypes();
    }

    // ---------------------------------------------------------------------
    // What counts as a declaration.
    // ---------------------------------------------------------------------

    @Test
    void findsClassAndObjectDeclarations() {
        assertEquals(List.of("Alpha", "Beta"), names("class Alpha : X\n\nobject Beta : Y\n"));
    }

    @Test
    void findsADeclarationThatStartsAtTheVeryFirstCharacter() {
        assertEquals(List.of("Alpha"), names("class Alpha : JavaDelegate"));
    }

    @Test
    void ignoresAnAnonymousObjectExpression() {
        assertEquals(List.of(), names("val handler = object : JavaDelegate {\n}\n"));
    }

    @Test
    void ignoresTheClassReferenceOperator() {
        assertEquals(List.of(), names("val reference = Alpha::class\n"));
    }

    @Test
    void ignoresAKeywordAtTheVeryEndOfTheFile() {
        assertEquals(List.of(), names("class"));
    }

    @Test
    void ignoresADeclarationWhoseNameIsNotAnIdentifier() {
        assertEquals(List.of(), names("class 1Alpha : X\n"));
    }

    @Test
    void ignoresAnIdentifierThatMerelyContainsTheKeyword() {
        assertEquals(List.of(), names("val classic = 1\nval subobject = 2\n"));
    }

    @Test
    void readsABacktickQuotedNameWithoutItsDelimiters() {
        assertEquals(List.of("spaced out"), names("class `spaced out` : JavaDelegate\n"));
    }

    @Test
    void ignoresADeclarationWhoseBacktickNameIsUnpaired() {
        // Hand-built input: the sanitizer never leaves a single backtick behind,
        // so this pins the parser's own guard rather than a reachable file shape.
        String text = "class `oops : JavaDelegate\n";
        assertEquals(List.of(), KotlinDeclarations.parse(new SanitizedSource(text, text, List.of())).stream()
            .map(KotlinDeclaration::name)
            .toList());
    }

    // ---------------------------------------------------------------------
    // Type parameters must never be read as supertypes.
    // ---------------------------------------------------------------------

    @Test
    void typeParameterBoundIsNotASupertype() {
        assertEquals(List.of(), supertypes("class Registry<T : JavaDelegate>(val types: List<T>)\n"));
    }

    @Test
    void skipsTypeParametersContainingAFunctionType() {
        assertEquals(List.of("JavaDelegate"), supertypes("class Registry<T : (Int) -> Unit> : JavaDelegate\n"));
    }

    @Test
    void skipsTypeParametersContainingANegativeConstant() {
        assertEquals(List.of("JavaDelegate"), supertypes("class Registry<@Size(-1) T> : JavaDelegate\n"));
    }

    @Test
    void skipsNestedTypeParameterBrackets() {
        assertEquals(List.of("JavaDelegate"), supertypes("class Registry<T : List<Map<String, Int>>> : JavaDelegate\n"));
    }

    @Test
    void unterminatedTypeParameterListEndsTheDeclaration() {
        assertEquals(List.of(), supertypes("class Registry<T : JavaDelegate"));
    }

    // ---------------------------------------------------------------------
    // Primary constructor.
    // ---------------------------------------------------------------------

    @Test
    void skipsAMultilinePrimaryConstructorWithDefaultLambdaArguments() {
        assertEquals(List.of("JavaDelegate"), supertypes("""
            class Alpha(
                private val label: String = "class Fake : JavaDelegate",
                private val onDone: (String) -> Unit = { value -> println(value) },
            ) : JavaDelegate
            """));
    }

    @Test
    void skipsAnAnnotatedPrimaryConstructor() {
        assertEquals(List.of("JavaDelegate"), supertypes("class Alpha @Inject constructor(val a: Int) : JavaDelegate\n"));
    }

    @Test
    void skipsAnAnnotatedPrimaryConstructorWithAnnotationArguments() {
        assertEquals(List.of("JavaDelegate"),
            supertypes("class Alpha @Named(\"a\") private constructor(val a: Int) : JavaDelegate\n"));
    }

    @Test
    void skipsAPrimaryConstructorWithNestedParentheses() {
        assertEquals(List.of("JavaDelegate"), supertypes("class Alpha(val a: Int = f(g(1))) : JavaDelegate\n"));
    }

    @Test
    void anAnnotationAtTheVeryEndOfTheFileEndsTheDeclaration() {
        assertEquals(List.of(), supertypes("class Alpha @Inject"));
    }

    @Test
    void unterminatedPrimaryConstructorEndsTheDeclaration() {
        assertEquals(List.of(), supertypes("class Alpha(val a: Int\n"));
    }

    @Test
    void aDeclarationWithoutABodyAtTheEndOfTheFileHasNoSupertypes() {
        assertEquals(List.of(), supertypes("class Alpha"));
    }

    @Test
    void aBodyLessDeclarationDoesNotSwallowTheFollowingDeclaration() {
        List<KotlinDeclaration> declarations = parse("class Alpha\n\nclass Beta : JavaDelegate\n");
        assertEquals(List.of("Alpha", "Beta"),
            declarations.stream().map(KotlinDeclaration::name).toList());
        assertEquals(List.of(), declarations.get(0).supertypes());
    }

    // ---------------------------------------------------------------------
    // Supertype list.
    // ---------------------------------------------------------------------

    @Test
    void readsTheSimpleNameOfAQualifiedSupertype() {
        assertEquals(List.of("JavaDelegate"), supertypes("class Alpha : org.camunda.JavaDelegate\n"));
    }

    @Test
    void readsSupertypeDelegation() {
        assertEquals(List.of("JavaDelegate"), supertypes("class Alpha(val t: JavaDelegate) : JavaDelegate by t\n"));
    }

    @Test
    void readsOnlyTheHeadOfAGenericSupertypeAndSkipsBracketedArguments() {
        assertEquals(List.of("Base", "JavaDelegate"),
            supertypes("class Alpha : Base<Map<String, Int>>(items[0]), JavaDelegate {\n}\n"));
    }

    @Test
    void arithmeticInASupertypeConstructorCallIsNotAnArrow() {
        assertEquals(List.of("Base", "JavaDelegate"), supertypes("class Alpha : Base(1 - 2), JavaDelegate {\n}\n"));
    }

    @Test
    void readsASupertypeCarryingAFunctionTypeArgument() {
        assertEquals(List.of("Base", "JavaDelegate"),
            supertypes("class Alpha : Base<(Int) -> Unit>, JavaDelegate {\n}\n"));
    }

    @Test
    void aTrailingMinusAtTheEndOfTheFileIsNoArrow() {
        assertEquals(List.of("Base"), supertypes("class Alpha : Base-"));
    }

    @Test
    void readsNamesThatStartWithAnUnderscore() {
        assertEquals(List.of("_Base"), supertypes("class _Alpha_1 : _Base\n"));
    }

    @Test
    void findsADeclarationNestedInAnotherOne() {
        String source = "class Outer {\n    class Inner : JavaDelegate\n}\n";
        assertEquals(List.of("Outer", "Inner"), names(source));
        assertEquals(List.of("JavaDelegate"), parse(source).get(1).supertypes());
    }

    @Test
    void findsANamedCompanionObject() {
        String source = "class Outer {\n    companion object Named : JavaDelegate\n}\n";
        assertEquals(List.of("JavaDelegate"), parse(source).get(1).supertypes());
    }

    @Test
    void separatesDeclarationsJoinedBySemicolons() {
        List<KotlinDeclaration> declarations = parse("class Alpha : JavaDelegate; class Beta : Runnable\n");
        assertEquals(List.of("JavaDelegate"), declarations.get(0).supertypes());
        assertEquals(List.of("Runnable"), declarations.get(1).supertypes());
    }

    @Test
    void readsASupertypeListWithATrailingComma() {
        assertEquals(List.of("Runnable", "JavaDelegate"),
            supertypes("class Alpha : Runnable,\n    JavaDelegate,\n{\n}\n"));
    }

    @Test
    void readsASupertypeListInterruptedByAComment() {
        assertEquals(List.of("Runnable", "JavaDelegate"),
            supertypes("class Alpha : Runnable, /* and */ JavaDelegate {\n}\n"));
    }

    @Test
    void stopsTheSupertypeListAtAWhereClause() {
        assertEquals(List.of("Runnable"), supertypes("class Alpha<T> : Runnable where T : JavaDelegate {\n}\n"));
    }

    @Test
    void continuesTheSupertypeListOverATrailingComma() {
        assertEquals(List.of("Runnable", "JavaDelegate"), supertypes("class Alpha : Runnable,\n    JavaDelegate {\n}\n"));
    }

    @Test
    void continuesTheSupertypeListOverALeadingComma() {
        assertEquals(List.of("Runnable", "JavaDelegate"), supertypes("class Alpha : Runnable\n    , JavaDelegate {\n}\n"));
    }

    @Test
    void continuesTheSupertypeListWhenTheColonEndsTheLine() {
        assertEquals(List.of("JavaDelegate"), supertypes("class Alpha :\n    JavaDelegate {\n}\n"));
    }

    @Test
    void stopsTheSupertypeListAtTheEndOfTheDeclarationLine() {
        List<KotlinDeclaration> declarations = parse("class Alpha : Runnable\n\nclass Beta : JavaDelegate\n");
        assertEquals(List.of("Runnable"), declarations.get(0).supertypes(),
            "a body-less declaration must not read the next declaration's supertypes");
        assertEquals(List.of("JavaDelegate"), declarations.get(1).supertypes());
    }

    @Test
    void stopsTheSupertypeListAtTheEndOfTheFile() {
        assertEquals(List.of("JavaDelegate"), supertypes("class Alpha : JavaDelegate\n"));
    }

    // ---------------------------------------------------------------------
    // Modifiers.
    // ---------------------------------------------------------------------

    @Test
    void readsModifiersAndReportsNonConcreteDeclarations() {
        assertFalse(single("abstract class Alpha : JavaDelegate\n").isConcrete());
        assertFalse(single("public sealed class Alpha : JavaDelegate\n").isConcrete());
        assertTrue(single("internal data class Alpha(val a: Int) : JavaDelegate\n").isConcrete());
    }

    @Test
    void readsModifiersOfADeclarationThatStartsTheFile() {
        assertEquals(List.of("public"), single("public class Alpha : JavaDelegate\n").modifiers());
    }

    // ---------------------------------------------------------------------
    // Header start and annotations.
    // ---------------------------------------------------------------------

    @Test
    void headerStartsAtTheFirstAnnotationAboveTheDeclaration() {
        String source = "@Component(\"alpha\")\n@Deprecated\ninternal class Alpha : JavaDelegate\n";
        KotlinDeclaration declaration = single(source);
        assertEquals(0, declaration.headerStart(), "the header starts at the topmost annotation");
        assertEquals(List.of("Deprecated", "Component"),
            declaration.annotations().stream().map(KotlinDeclaration.AnnotationRef::name).toList());
        assertEquals(List.of("internal"), declaration.modifiers());
    }

    @Test
    void locatesAnAnnotationWhoseArgumentsContainNestedParentheses() {
        String source = "@Component(value = f(1))\nclass Alpha : JavaDelegate\n";
        KotlinDeclaration declaration = single(source);
        assertEquals(0, declaration.headerStart());
        assertEquals(List.of("Component"),
            declaration.annotations().stream().map(KotlinDeclaration.AnnotationRef::name).toList());
    }

    @Test
    void locatesAFullyQualifiedAnnotationAndItsArguments() {
        String source = "@net.jakobarndt.bpmnbacklink.annotation.CalledFrom(\"a.bpmn\")\nclass Alpha : JavaDelegate\n";
        Optional<KotlinDeclaration.AnnotationRef> annotation = single(source).annotation("CalledFrom");
        assertTrue(annotation.isPresent());
        assertEquals(0, annotation.get().start());
        assertEquals(source.indexOf('\n'), annotation.get().end());
        assertEquals(List.of("a.bpmn"), KotlinSanitizer.sanitize(source)
            .literalValuesIn(annotation.get().argumentsStart(), annotation.get().argumentsEnd()));
    }

    @Test
    void anAnnotationWithoutArgumentsHasAnEmptyArgumentRange() {
        KotlinDeclaration declaration = single("@Deprecated\nclass Alpha : JavaDelegate\n");
        KotlinDeclaration.AnnotationRef annotation = declaration.annotation("Deprecated").orElseThrow();
        assertEquals(annotation.end(), annotation.argumentsStart());
        assertEquals(annotation.end(), annotation.argumentsEnd());
        assertTrue(declaration.annotation("CalledFrom").isEmpty(), "an absent annotation must not be reported");
    }

    @Test
    void aFileAnnotationIsNotAbsorbedIntoTheDeclarationHeader() {
        String source = "@file:JvmName(\"Alpha\")\n\nclass Alpha : JavaDelegate\n";
        KotlinDeclaration declaration = single(source);
        assertEquals(source.indexOf("class"), declaration.headerStart());
        assertEquals(List.of(), declaration.annotations());
    }

    @Test
    void headerWalkStopsAtAPrecedingCallExpression() {
        String source = "val known = listOf(1)\nclass Alpha : JavaDelegate\n";
        assertEquals(source.indexOf("class"), single(source).headerStart());
    }

    @Test
    void headerWalkStopsAtAPrecedingBlock() {
        String source = "fun bootstrap() {\n}\nclass Alpha : JavaDelegate\n";
        assertEquals(source.indexOf("class Alpha"), single(source).headerStart());
    }

    @Test
    void headerWalkStopsAtAPrecedingNonModifierWord() {
        String source = "val known = 1\nclass Alpha : JavaDelegate\n";
        assertEquals(source.indexOf("class"), single(source).headerStart());
    }

    @Test
    void headerWalkStopsAtAnUnbalancedClosingParenthesis() {
        String source = ")\nclass Alpha : JavaDelegate\n";
        assertEquals(source.indexOf("class"), single(source).headerStart());
    }

    @Test
    void headerWalkStopsAtAParenthesisGroupWithoutAName() {
        String source = "(1)\nclass Alpha : JavaDelegate\n";
        assertEquals(source.indexOf("class"), single(source).headerStart());
    }
}
