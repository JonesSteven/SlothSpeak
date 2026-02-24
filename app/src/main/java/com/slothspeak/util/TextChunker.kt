package com.slothspeak.util

object TextChunker {

    private const val MAX_CHUNK_SIZE = 4096
    private const val MIN_SEARCH_RANGE = 500

    private val SENTENCE_ENDINGS = setOf('.', '!', '?')

    fun chunkText(text: String): List<String> {
        if (text.length <= MAX_CHUNK_SIZE) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= MAX_CHUNK_SIZE) {
                chunks.add(remaining)
                break
            }

            val boundary = findSplitPoint(remaining, MAX_CHUNK_SIZE)
            chunks.add(remaining.substring(0, boundary).trimEnd())
            remaining = remaining.substring(boundary).trimStart()
        }

        return chunks.filter { it.isNotBlank() }
    }

    fun splitInHalf(text: String): List<String> {
        val midpoint = text.length / 2
        val boundary = findSplitPoint(text, midpoint + MIN_SEARCH_RANGE)
        val first = text.substring(0, boundary).trimEnd()
        val second = text.substring(boundary).trimStart()
        return listOf(first, second).filter { it.isNotBlank() }
    }

    private fun findSplitPoint(text: String, maxLength: Int): Int {
        // Search backward from the max length for a sentence boundary
        val searchStart = (maxLength - MIN_SEARCH_RANGE).coerceAtLeast(0)

        for (i in maxLength - 1 downTo searchStart) {
            if (i >= text.length) continue

            val char = text[i]
            if (char in SENTENCE_ENDINGS) {
                // Check for closing quotes after the punctuation
                val nextIdx = i + 1
                if (nextIdx < text.length && text[nextIdx] == '"') {
                    return nextIdx + 1
                }
                return nextIdx
            }
        }

        // No sentence boundary found - fall back to last space
        for (i in maxLength - 1 downTo searchStart) {
            if (i >= text.length) continue
            if (text[i] == ' ') {
                return i + 1
            }
        }

        // No space found either - hard cut at max length
        return maxLength
    }
}
