package com.slothspeak.config

/**
 * System-level instructions sent to LLM providers.
 *
 * Edit these strings to change the behavior of all models.
 * SYSTEM_PROMPT is used for standard Q&A (OpenAI, Gemini, Claude, Grok).
 * DEEP_RESEARCH_SYSTEM_PROMPT is used for Deep Research (OpenAI and Gemini).
 */
object SystemPrompts {

    const val SYSTEM_PROMPT =
        "Please provide an answer to this question. " +
        "The audience is university educated. " +
        "The answer will be read aloud so do not rely on complex formatting such as tables or emojis " +
        "and do not include any URLs or links. " +
        "Prioritize something that will sound natural when read aloud. " +
        "Please define any acronyms the first time they are used. " +
        "Please avoid sycophancy and challenge any inappropriate assumptions made in the question when appropriate."

    const val FOLLOW_UP_PROMPT = "Do you have a follow-up?"

    const val DEEP_RESEARCH_SYSTEM_PROMPT =
        "Please provide a report to answer this question. " +
        "The audience is university educated. " +
        "Please be analytical and look for the most recent sources. " +
        "The report will be read aloud so do not rely on complex formatting such as tables or emojis " +
        "and do not include any URLs or links. " +
        "Prioritize something that will sound natural when read aloud. " +
        "Please define any acronyms the first time they are used. " +
        "Please avoid sycophancy and challenge any inappropriate assumptions made in the question when appropriate."
}
