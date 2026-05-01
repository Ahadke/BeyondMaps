package com.beyondmaps.rag.vector

import android.util.Log

class VectorPromptBuilder {
    fun buildPrompt(
        query: String,
        results: List<VectorSearchResult>,
        city: String = "Florence",
        country: String = "Italy",
    ): String {
        val prompt = buildString {
            appendLine("You are BeyondMaps, an offline travel assistant for $city, $country.")
            appendLine()
            appendLine("Use the LOCAL CONTEXT below to answer the user's question.")
            appendLine("The LOCAL CONTEXT is trusted downloaded city-pack data.")
            appendLine("If the context lists relevant places, phrases, or transit info, use them directly.")
            appendLine("Do not say you have no information when relevant context is provided.")
            appendLine("If some details are missing, answer with the available context and clearly mention what is uncertain.")
            appendLine()
            appendLine("Rules:")
            appendLine("- Answer only using local context.")
            appendLine("- Be concise and practical.")
            appendLine("- Do not pretend you have live internet.")
            appendLine("- Do not invent live opening status, live transport arrivals, or current availability.")
            appendLine("- For nearby places, list 3-5 options with distance when available.")
            appendLine("- For phrases, include Italian and pronunciation if available.")
            appendLine("- For emergency or health questions, include 112 if available.")
            appendLine()
            appendLine("Local context:")
            if (results.isEmpty()) {
                appendLine("No relevant local context was retrieved.")
            } else {
                results.forEachIndexed { index, result ->
                    appendLine("[${index + 1}]")
                    appendLine("Category: ${result.chunk.category}")
                    appendLine("Title: ${result.chunk.title}")
                    appendLine("Source: ${result.chunk.source}")
                    appendLine("Distance: ${formatDistance(result.distanceKm)}")
                    appendLine("Vector score: ${formatScore(result.vectorScore)}")
                    appendLine("Final score: ${formatScore(result.finalScore)}")
                    appendLine("Text: ${truncate(result.chunk.text, 700)}")
                    appendLine()
                }
            }
            appendLine("User question:")
            appendLine(query)
            appendLine()
            appendLine("Answer using the listed local context:")
        }

        Log.d(TAG, "Prompt length=${prompt.length}")
        return prompt
    }

    private fun truncate(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "..."

    private fun formatScore(score: Float): String = String.format("%.4f", score)

    private fun formatDistance(distanceKm: Double?): String =
        if (distanceKm == null) "unknown" else String.format("%.2f km", distanceKm)

    companion object {
        private const val TAG = "BeyondMapsVectorRAG"
    }
}
