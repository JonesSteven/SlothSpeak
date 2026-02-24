package com.slothspeak.util

object SpeechTextCleaner {

    // [cite: 1] or [cite: 1, 2, 3]
    private val INLINE_CITATION_REGEX = Regex("""\[cite:\s*[\d,\s]+]""")

    // Sources/References section header to end of text
    private val SOURCES_SECTION_REGEX = Regex(
        """(?m)^(?:\*\*Sources:\*\*|\*\*References:\*\*|#{1,6}\s*Sources|#{1,6}\s*References).*""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Markdown table rows: | col1 | col2 | ... |
    // and table separators: | :--- | :--- |
    private val TABLE_ROW_REGEX = Regex("""(?m)^\|.+\|[ \t]*$""")

    // Horizontal rules: --- on its own line (with optional whitespace)
    private val HORIZONTAL_RULE_REGEX = Regex("""(?m)^[ \t]*---+[ \t]*$""")

    // Markdown headers: # Header text
    private val HEADER_REGEX = Regex("""(?m)^#{1,6}\s+""")

    // Bold: **text**
    private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")

    // Italic: *text* (but not inside a word like file*name)
    private val ITALIC_REGEX = Regex("""(?<!\w)\*(.+?)\*(?!\w)""")

    // Markdown links: [text](url)
    private val LINK_REGEX = Regex("""\[([^\]]+)]\([^)]+\)""")

    // Bare URLs
    private val URL_REGEX = Regex("""https?://\S+""")

    // Multiple blank lines
    private val MULTI_BLANK_LINES_REGEX = Regex("""\n{3,}""")

    fun cleanForSpeech(text: String): String {
        var result = text

        // 1. Remove inline citations
        result = INLINE_CITATION_REGEX.replace(result, "")

        // 2. Remove Sources/References section (everything from header to end)
        result = SOURCES_SECTION_REGEX.replace(result, "")

        // 3. Remove markdown table rows and separators
        result = TABLE_ROW_REGEX.replace(result, "")

        // 4. Remove horizontal rules
        result = HORIZONTAL_RULE_REGEX.replace(result, "")

        // 5. Remove markdown header markers (keep the text)
        result = HEADER_REGEX.replace(result, "")

        // 6. Remove bold markers
        result = BOLD_REGEX.replace(result, "$1")

        // 7. Remove italic markers
        result = ITALIC_REGEX.replace(result, "$1")

        // 8. Remove markdown links (keep link text)
        result = LINK_REGEX.replace(result, "$1")

        // 9. Remove bare URLs
        result = URL_REGEX.replace(result, "")

        // 10. Collapse excessive whitespace
        result = MULTI_BLANK_LINES_REGEX.replace(result, "\n\n")

        return result.trim()
    }
}
