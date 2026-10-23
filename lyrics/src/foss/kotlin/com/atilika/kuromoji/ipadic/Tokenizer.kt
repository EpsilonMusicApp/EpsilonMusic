package com.atilika.kuromoji.ipadic

/**
 * FOSS-variant stand-in for the Kuromoji ipadic tokenizer.
 *
 * The real Kuromoji dependency, with its ~13 MB ipadic dictionary, is
 * GMS-only, so the FOSS build compiles against this stub instead. It
 * performs no dictionary lookup: the whole input comes back as a
 * single token with no reading. LyricsUtils.romanizeJapanese therefore
 * degrades gracefully in FOSS builds - kana is still converted to
 * romaji by the built-in kana map, kanji is left as-is.
 */
class Tokenizer {
    fun tokenize(text: String): List<Token> = listOf(Token(text, null))
}

class Token(
    val surface: String,
    val reading: String?,
)
