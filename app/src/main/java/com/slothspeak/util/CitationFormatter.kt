package com.slothspeak.util

import com.slothspeak.api.models.Annotation

data class CitationResult(
    val cleanText: String,
    val richText: String?
)

object CitationFormatter {

    /**
     * Processes raw API text and url_citation annotations into two versions:
     * - cleanText: citations stripped (for TTS)
     * - richText: inline [N] footnotes + Sources section at bottom (for display/clipboard)
     *
     * Returns richText = null when there are no citations (no change to display).
     */
    fun processCitations(text: String, annotations: List<Annotation>?): CitationResult {
        if (annotations.isNullOrEmpty()) {
            return CitationResult(cleanText = text, richText = null)
        }

        val citations = annotations
            .filter { it.type == "url_citation" && it.url != null }

        if (citations.isEmpty()) {
            return CitationResult(cleanText = text, richText = null)
        }

        // Deduplicate sources by URL, assigning a stable footnote number to each unique URL
        val urlToFootnote = LinkedHashMap<String, Int>()
        val urlToTitle = LinkedHashMap<String, String>()
        for (cite in citations) {
            val url = cite.url ?: continue
            if (url !in urlToFootnote) {
                urlToFootnote[url] = urlToFootnote.size + 1
                urlToTitle[url] = cite.title ?: url
            }
        }

        // Build clean text (strip citation marker ranges, reverse order to preserve indices)
        val cleanSb = StringBuilder(text)
        val sortedDesc = citations.sortedByDescending { it.startIndex }
        for (cite in sortedDesc) {
            val start = cite.startIndex.coerceIn(0, cleanSb.length)
            val end = cite.endIndex.coerceIn(start, cleanSb.length)
            if (start < end) {
                cleanSb.delete(start, end)
            }
        }
        val cleanText = cleanSb.toString().replace("  ", " ").trim()

        // Build rich text (replace citation marker ranges with [N], reverse order)
        val richSb = StringBuilder(text)
        for (cite in sortedDesc) {
            val url = cite.url ?: continue
            val footnote = urlToFootnote[url] ?: continue
            val start = cite.startIndex.coerceIn(0, richSb.length)
            val end = cite.endIndex.coerceIn(start, richSb.length)
            if (start < end) {
                richSb.replace(start, end, " [$footnote]")
            }
        }

        // Append sources section
        richSb.append("\n\nSources:")
        for ((url, footnote) in urlToFootnote) {
            val title = urlToTitle[url] ?: url
            richSb.append("\n[$footnote] $title ($url)")
        }

        return CitationResult(
            cleanText = cleanText,
            richText = richSb.toString().trim()
        )
    }
}
