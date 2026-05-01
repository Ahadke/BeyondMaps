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
            appendLine("If relevant context is listed, answer using it directly.")
            appendLine("Do not answer from general knowledge.")
            appendLine("Do not say you lack information if the context contains relevant places, phrases, or transit info.")
            appendLine("Copy prices, times, place names, and ticket validity exactly as written in the context.")
            appendLine("If multiple useful facts appear, give a short traveler-friendly answer with the exact numbers.")
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

    private fun formatDistance(distanceKm: Double?): String =
        if (distanceKm == null) "unknown" else String.format("%.2f km", distanceKm)

    companion object {
        private const val TAG = "BeyondMapsVectorRAG"
    }
}
