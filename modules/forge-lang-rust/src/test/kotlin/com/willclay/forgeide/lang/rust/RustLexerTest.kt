package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.highlighting.Token
import com.willclay.forgeide.highlighting.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks the colour of each case that makes Rust awkward to lex. Run it with
 * `./gradlew :modules:forge-lang-rust:test`.
 *
 * Most tests name a fragment of the source and the colour it should get, so a
 * failure says which piece of text was coloured wrongly.
 */
class RustLexerTest {
    private val lexer = RustLexer()

    @Test
    fun lifetimesAreNotCharacterLiterals() {
        val source = "fn first<'a>(s: &'a str) -> char { 'a' }"

        assertToken(source, "'a", TokenType.TYPE)
        assertToken(source, "'a'", TokenType.CHARACTER)
        assertToken("let s: &'static str = \"\";", "'static", TokenType.TYPE)
        assertToken("let c = '\\n';", "'\\n'", TokenType.CHARACTER)
        assertToken("let crab = '🦀';", "'🦀'", TokenType.CHARACTER)
    }

    @Test
    fun rawStringsCountTheirHashes() {
        val source = "let s = r##\"a \"# quote\"##; let t = 1;"

        assertToken(source, "r##\"a \"# quote\"##", TokenType.STRING)
        assertToken(source, "let", TokenType.KEYWORD, occurrence = 2)
        assertToken("let p = r\"C:\\path\";", "r\"C:\\path\"", TokenType.STRING)
    }

    @Test
    fun rawIdentifiersAreNeverKeywords() {
        assertPlain("let r#type = 1;", "r#type")
        assertToken("fn r#match() {}", "r#match", TokenType.METHOD_DECLARATION)
    }

    @Test
    fun byteAndCStrings() {
        assertToken("let b = b\"bytes\";", "b\"bytes\"", TokenType.STRING)
        assertToken("let b = b'x';", "b'x'", TokenType.CHARACTER)
        assertToken("let c = c\"text\";", "c\"text\"", TokenType.STRING)
        assertToken("let r = br#\"raw\"#;", "br#\"raw\"#", TokenType.STRING)
    }

    @Test
    fun stringsMaySpanLines() {
        assertToken("let s = \"one\ntwo\";", "\"one\ntwo\"", TokenType.STRING)
    }

    @Test
    fun blockCommentsNest() {
        val source = "/* a /* b */ c */ fn x() {}"

        assertToken(source, "/* a /* b */ c */", TokenType.COMMENT)
        assertToken(source, "fn", TokenType.KEYWORD)
    }

    @Test
    fun docComments() {
        assertToken("/// Docs\nfn x() {}", "/// Docs", TokenType.DOC_COMMENT)
        assertToken("//! Crate docs", "//! Crate docs", TokenType.DOC_COMMENT)
        assertToken("//// Not docs", "//// Not docs", TokenType.COMMENT)
        assertToken("/** Docs */", "/** Docs */", TokenType.DOC_COMMENT)
        assertToken("/**/", "/**/", TokenType.COMMENT)
    }

    @Test
    fun numbersKeepTheirSuffixesButNotRangesOrMethods() {
        assertToken("let n = 1_000u32;", "1_000u32", TokenType.NUMBER)
        assertToken("let f = 2.5e-3_f32;", "2.5e-3_f32", TokenType.NUMBER)
        assertToken("let h = 0xFF_u8;", "0xFF_u8", TokenType.NUMBER)
        assertToken("let b = 0b1010;", "0b1010", TokenType.NUMBER)

        val range = "for i in 0..10 {}"
        assertToken(range, "0", TokenType.NUMBER)
        assertToken(range, "..", TokenType.OPERATOR)
        assertToken(range, "10", TokenType.NUMBER)

        val method = "let m = 1.max(2);"
        assertToken(method, "1", TokenType.NUMBER)
        assertToken(method, "max", TokenType.METHOD_CALL)
    }

    @Test
    fun macroCallsIncludeTheirBang() {
        val source = "println!(\"{}\", x);"

        assertToken(source, "println", TokenType.METHOD_CALL)
        assertToken(source, "!", TokenType.METHOD_CALL)
        assertToken("let v = vec![1, 2];", "vec", TokenType.METHOD_CALL)
        assertToken("if a != b {}", "!=", TokenType.OPERATOR)
        assertToken("if !done {}", "!", TokenType.OPERATOR)
    }

    @Test
    fun macroRulesDeclaresAMacro() {
        val source = "macro_rules! square { (\$x:expr) => { \$x * \$x }; }"

        assertToken(source, "macro_rules", TokenType.KEYWORD)
        assertToken(source, "!", TokenType.KEYWORD)
        assertToken(source, "square", TokenType.METHOD_DECLARATION)
    }

    @Test
    fun declarationsAreNamedByTheirKeyword() {
        val source = """
            struct Player { health: i32 }
            enum State { Idle, Running }
            trait Shape { fn area(&self) -> f64; }
            impl Player {
                fn update(&mut self) { self.health -= 1; other.update(); }
            }
        """.trimIndent()

        assertToken(source, "Player", TokenType.TYPE_DECLARATION)
        assertToken(source, "State", TokenType.TYPE_DECLARATION)
        assertToken(source, "Shape", TokenType.TYPE_DECLARATION)
        assertToken(source, "i32", TokenType.TYPE)
        assertToken(source, "Idle", TokenType.TYPE)
        assertToken(source, "area", TokenType.METHOD_DECLARATION)
        assertToken(source, "update", TokenType.METHOD_DECLARATION)
        assertToken(source, "update", TokenType.METHOD_CALL, occurrence = 2)
        assertToken(source, "Player", TokenType.TYPE, occurrence = 2)
        assertPlain(source, "health")
    }

    @Test
    fun namingConventionsDecideTypes() {
        assertPlain("const MAX_SPEED: f32 = 1.0;", "MAX_SPEED")
        assertToken("let x = Some(5);", "Some", TokenType.TYPE)
        assertToken("fn id<T>(t: T) -> T { t }", "T", TokenType.TYPE)
        assertToken("let v = Vec::new();", "Vec", TokenType.TYPE)
        assertToken("let v = Vec::new();", "new", TokenType.METHOD_CALL)
        assertToken("let n = \"5\".parse::<i32>();", "parse", TokenType.METHOD_CALL)
        assertPlain("use std::io;", "std")
    }

    @Test
    fun attributesColourTheirNameOnly() {
        val derive = "#[derive(Debug, Clone)]"
        assertToken(derive, "#[", TokenType.ANNOTATION)
        assertToken(derive, "derive", TokenType.ANNOTATION)
        assertToken(derive, "Debug", TokenType.TYPE)

        val inner = "#![allow(dead_code)]"
        assertToken(inner, "#![", TokenType.ANNOTATION)
        assertToken(inner, "allow", TokenType.ANNOTATION)
        assertPlain(inner, "dead_code")

        assertToken("#[serde::rename = \"x\"]", "rename", TokenType.ANNOTATION)
    }

    @Test
    fun weakKeywordsDependOnPosition() {
        val declaration = "union IntOrFloat { i: u32, f: f32 }"
        assertToken(declaration, "union", TokenType.KEYWORD)
        assertToken(declaration, "IntOrFloat", TokenType.TYPE_DECLARATION)

        assertPlain("let union = 1;", "union")
        assertToken("let p = &raw const x;", "raw", TokenType.KEYWORD)
        assertPlain("let raw = 1;", "raw")
    }

    @Test
    fun shebangIsACommentButInnerAttributesAreNot() {
        assertToken("#!/usr/bin/env rust-script\nfn main() {}", "#!/usr/bin/env rust-script", TokenType.COMMENT)
        assertToken("#![no_std]", "#![", TokenType.ANNOTATION)
    }

    /**
     * The state of a file being edited is half-typed code, so every prefix of a
     * real program has to lex without throwing, and still produce tokens in
     * order, without overlaps, inside the text.
     */
    @Test
    fun everyPrefixOfAProgramLexesCleanly() {
        val program = """
            #![allow(dead_code)]
            //! A tiny game loop.

            use std::collections::HashMap;

            /// Where something is.
            #[derive(Debug, Clone, Copy)]
            struct Position<'a> { x: f32, y: f32, name: &'a str }

            const MAX_SPEED: f32 = 2.5e-3_f32;

            /* outer /* nested */ still outer */
            fn main() {
                let mut scores: HashMap<&str, u32> = HashMap::new();
                let r#type = r#"raw "string""#;
                for i in 0..=10 { scores.insert("crab", i * 1_000u32); }
                'outer: loop { break 'outer; }
                let c = '🦀'; let b = b'x'; let s = "multi
            line";
                println!("{:?} {} {}", scores, c as u32, b);
                let n = "5".parse::<i32>().unwrap_or(0);
            }
        """.trimIndent()

        for (end in 0..program.length) {
            val prefix = program.substring(0, end)
            val tokens = lexer.tokenize(prefix)

            var previousEnd = 0
            for (token in tokens) {
                assertTrue(token.start >= previousEnd, "Overlapping or unordered token $token in prefix of length $end")
                assertTrue(token.end > token.start, "Empty token $token in prefix of length $end")
                assertTrue(token.end <= prefix.length, "Token $token runs past prefix of length $end")
                previousEnd = token.end
            }
        }

        // And the unterminated forms specifically.
        for (unfinished in listOf("\"abc", "r#\"abc", "/* abc /* def */", "'", "b'", "0x", "1e", "#[", "r#")) {
            lexer.tokenize(unfinished)
        }
    }

    // --- Helpers --- //

    /** Asserts that [fragment] is exactly one token of the [expected] colour. */
    private fun assertToken(source: String, fragment: String, expected: TokenType, occurrence: Int = 1) {
        val start = indexOf(source, fragment, occurrence)
        val token = lexer.tokenize(source).firstOrNull { it.start == start }

        assertNotNull(token, "No token starts at \"$fragment\" in: $source")
        assertEquals(fragment, source.substring(token.start, token.end), "Token covers the wrong text in: $source")
        assertEquals(expected, token.type, "Wrong colour for \"$fragment\" in: $source")
    }

    /** Asserts that nothing colours any part of [fragment]. */
    private fun assertPlain(source: String, fragment: String, occurrence: Int = 1) {
        val start = indexOf(source, fragment, occurrence)
        val end = start + fragment.length
        val overlapping: Token? = lexer.tokenize(source).firstOrNull { it.start < end && it.end > start }

        if (overlapping != null) {
            fail("Expected \"$fragment\" to be plain, but it is ${overlapping.type} in: $source")
        }
    }

    private fun indexOf(source: String, fragment: String, occurrence: Int): Int {
        var index = -1
        repeat(occurrence) {
            index = source.indexOf(fragment, index + 1)
            if (index < 0) fail("\"$fragment\" occurs fewer than $occurrence times in: $source")
        }

        return index
    }
}
