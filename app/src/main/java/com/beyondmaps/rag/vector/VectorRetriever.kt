package com.beyondmaps.rag.vector

import android.util.Log
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class VectorSearchResult(
    val chunk: VectorChunk,
    val vectorScore: Float,
    val keywordScore: Float = 0f,
    val exactMatchBoost: Float = 0f,
    val transitInfoBoost: Float = 0f,
    val transitStopPenalty: Float = 0f,
    val landmarkBoost: Float = 0f,
    val sourceBoost: Float = 0f,
    val finalScore: Float = vectorScore,
    val distanceKm: Double? = null,
)

class VectorRetriever(private val pack: LoadedVectorPack) {
    fun retrieve(
        query: String,
        queryVector: FloatArray,
        userLat: Double? = null,
        userLon: Double? = null,
        topK: Int = 6,
    ): List<VectorSearchResult> {
        require(queryVector.size == EXPECTED_VECTOR_SIZE) {
            "queryVector must have size $EXPECTED_VECTOR_SIZE but was ${queryVector.size}"
        }
        if (topK <= 0) return emptyList()

        val intents = detectIntents(query)
        val transitInfo = isTransitInfoQuery(query)
        val broadAttraction = isBroadAttractionQuery(query)
        val distanceBetweenPlaces = isDistanceBetweenPlacesQuery(query)
        Log.d(TAG, "hybrid retrieve query=$query")
        Log.d(TAG, "hybrid retrieve intents=$intents")
        Log.d(TAG, "hybrid retrieve transitInfo=$transitInfo")
        Log.d(TAG, "hybrid retrieve broadAttraction=$broadAttraction")
        Log.d(TAG, "hybrid retrieve distanceBetweenPlaces=$distanceBetweenPlaces")

        val hardFiltered = pack.chunks.asSequence()
            .filter { it.embedding.size == EXPECTED_VECTOR_SIZE }
            .filter { shouldKeepChunk(it, intents, transitInfo) }
            .filter { !shouldBlockSpecificQueryChunk(it, intents, transitInfo) }
            .toList()

        Log.d(TAG, "hard-filter searched count=${hardFiltered.size}")
        Log.d(TAG, "hard-filter category counts=${categoryCounts(hardFiltered)}")
        Log.d(TAG, "hard-filter source counts=${sourceCounts(hardFiltered)}")

        val fakeQueryEmbedderActive = hardFiltered.any { cosineSimilarity(queryVector, it.embedding) >= 0.9995f }
        Log.d(TAG, "hybrid retrieve likelyFakeEmbedder=$fakeQueryEmbedderActive")
        if (fakeQueryEmbedderActive) {
            Log.w(TAG, "FakeQueryEmbedder active: vector score downweighted")
        }

        val scored = hardFiltered.asSequence()
            .map { chunk ->
                val vectorScore = cosineSimilarity(queryVector, chunk.embedding)
                val cappedVectorScore = if (fakeQueryEmbedderActive) minOf(vectorScore, 1.0f) else vectorScore
                val vectorWeight = if (fakeQueryEmbedderActive) 0.10f else 0.35f
                val categoryScore = categoryScore(intents, chunk)
                val keywordScore = keywordScore(query, chunk)
                val distanceKm = distanceKm(userLat, userLon, chunk.lat, chunk.lon)
                val distanceScore = if (distanceKm != null) (1.0 / (1.0 + distanceKm)).toFloat() else 0f
                val sourceScore = sourceScore(chunk.source)
                val exactMatchBoost = exactMatchBoost(query, chunk, transitInfo)
                val transitInfoBoost = transitInfoBoost(chunk, transitInfo)
                val transitStopPenalty = stopOnlyTransitPenalty(chunk, transitInfo)
                val foodBoost = restaurantFoodBoost(query, chunk, intents)
                val landmarkBoost = landmarkBoost(chunk)
                val sourceBoost = sourceBoost(chunk)
                val genericOsmAttractionPenalty = genericOsmAttractionPenalty(chunk)
                val broadAttractionAdjustment = if (broadAttraction) {
                    landmarkBoost + sourceBoost + genericOsmAttractionPenalty
                } else {
                    0f
                }

                val finalScore = (vectorWeight * cappedVectorScore) +
                    (0.25f * categoryScore) +
                    (0.20f * keywordScore) +
                    (0.10f * distanceScore) +
                    (0.10f * sourceScore) +
                    exactMatchBoost +
                    transitInfoBoost +
                    transitStopPenalty +
                    foodBoost +
                    broadAttractionAdjustment

                VectorSearchResult(
                    chunk = chunk,
                    vectorScore = vectorScore,
                    keywordScore = keywordScore,
                    exactMatchBoost = exactMatchBoost,
                    transitInfoBoost = if (transitInfo) (transitInfoBoost + foodBoost) else foodBoost,
                    transitStopPenalty = if (transitInfo) transitStopPenalty else 0f,
                    landmarkBoost = if (broadAttraction) landmarkBoost else 0f,
                    sourceBoost = if (broadAttraction) (sourceBoost + genericOsmAttractionPenalty) else 0f,
                    finalScore = finalScore,
                    distanceKm = distanceKm,
                )
            }
            .sortedByDescending { it.finalScore }
            .toList()

        val base = if (scored.isEmpty()) {
            Log.w(TAG, "hybrid retrieve filtered to 0, falling back to vector-only ranking")
            hardFiltered.asSequence()
                .map { chunk ->
                    val vectorScore = cosineSimilarity(queryVector, chunk.embedding)
                    VectorSearchResult(
                        chunk = chunk,
                        vectorScore = vectorScore,
                        finalScore = vectorScore,
                        distanceKm = distanceKm(userLat, userLon, chunk.lat, chunk.lon),
                    )
                }
                .sortedByDescending { it.finalScore }
                .toList()
        } else {
            scored
        }

        val syntheticDistanceResult = if (distanceBetweenPlaces) buildDistanceBetweenPlacesResult(query) else null
        val effective = if (syntheticDistanceResult != null) {
            listOf(syntheticDistanceResult) + base
        } else {
            base
        }

        Log.d(TAG, "hybrid retrieve searched count=${effective.size}")
        val top = effective.take(topK)
        top.firstOrNull()?.let { topResult ->
            val preview = topResult.chunk.text.replace(Regex("\\s+"), " ").take(140)
            Log.d(TAG, "hybrid topResult title=${topResult.chunk.title}, source=${topResult.chunk.source}, textPreview=$preview")
        }
        Log.d(TAG, "hybrid top6 titles=${top.take(6).joinToString(" | ") { it.chunk.title }}")
        top.take(6).forEachIndexed { index, result ->
            Log.d(
                TAG,
                "hybrid top[$index] title=${result.chunk.title}, category=${result.chunk.category}, source=${result.chunk.source}, vectorScore=${"%.4f".format(result.vectorScore)}, keywordScore=${"%.4f".format(result.keywordScore)}, exactMatchBoost=${"%.4f".format(result.exactMatchBoost)}, transitInfoBoost=${"%.4f".format(result.transitInfoBoost)}, transitStopPenalty=${"%.4f".format(result.transitStopPenalty)}, landmarkBoost=${"%.4f".format(result.landmarkBoost)}, sourceBoost=${"%.4f".format(result.sourceBoost)}, finalScore=${"%.4f".format(result.finalScore)}, distanceKm=${formatDistance(result.distanceKm)}",
            )
        }
        return top
    }

    fun retrieveByVector(queryVector: FloatArray, topK: Int = 6): List<VectorSearchResult> {
        Log.d(TAG, "retrieveByVector querySize=${queryVector.size}, topK=$topK")
        require(queryVector.size == EXPECTED_VECTOR_SIZE) {
            "queryVector must have size $EXPECTED_VECTOR_SIZE but was ${queryVector.size}"
        }
        if (topK <= 0) return emptyList()

        val ranked = pack.chunks.asSequence()
            .mapNotNull { chunk ->
                if (chunk.embedding.size != EXPECTED_VECTOR_SIZE) return@mapNotNull null
                val score = cosineSimilarity(queryVector, chunk.embedding)
                VectorSearchResult(
                    chunk = chunk,
                    vectorScore = score,
                )
            }
            .sortedByDescending { it.vectorScore }
            .take(topK)
            .toList()

        ranked.forEachIndexed { index, result ->
            Log.d(
                TAG,
                "top[$index] id=${result.chunk.id}, title=${result.chunk.title}, score=${"%.4f".format(result.vectorScore)}",
            )
        }
        return ranked
    }

    private fun detectIntents(query: String): Set<String> {
        val q = query.lowercase(Locale.ROOT)
        val intents = mutableSetOf<String>()
        if (matchesAny(q, RESTAURANT_WORDS)) intents += "restaurant"
        if (matchesAny(q, TRANSIT_WORDS)) intents += "transit"
        if (matchesAny(q, ATTRACTION_WORDS)) intents += "attraction"
        if (matchesAny(q, PHRASE_WORDS)) intents += "phrase"
        if (matchesAny(q, EMERGENCY_WORDS)) intents += "emergency"
        if (matchesAny(q, TOILET_WORDS)) intents += "toilet"
        if (matchesAny(q, ATM_WORDS)) intents += "atm"
        if (matchesAny(q, WATER_WORDS)) intents += "water"
        if (matchesAny(q, ACCOMMODATION_WORDS)) intents += "accommodation"
        return intents
    }

    private fun shouldKeepChunk(chunk: VectorChunk, intents: Set<String>, transitInfo: Boolean): Boolean {
        val category = normalizeCategory(chunk.category)
        val source = chunk.source.lowercase(Locale.ROOT)
        val rawCategory = chunk.category.lowercase(Locale.ROOT)

        if (transitInfo) {
            val transitGuideExists = pack.chunks.any { it.source.lowercase(Locale.ROOT) == "transit_guide" }
            return if (transitGuideExists) source == "transit_guide" else category == "transit"
        }
        if ("restaurant" in intents) return category == "restaurant"
        if ("phrase" in intents) return category == "phrase" || (source == "curated" && rawCategory.contains("phrase"))
        if ("emergency" in intents) return category in setOf("emergency", "phrase", "pharmacy", "hospital", "police")
        if ("transit" in intents) return category == "transit"

        val hasPhraseOrEmergencyIntent = "phrase" in intents || "emergency" in intents
        val hasPoiIntent = intents.any { it in setOf("restaurant", "transit", "attraction", "toilet", "atm", "water") }
        if ("attraction" in intents && category in setOf("restaurant", "hotel", "accommodation", "transit")) return false
        if (hasPoiIntent && !hasPhraseOrEmergencyIntent && category == "phrase") return false
        if ("accommodation" !in intents && category in setOf("accommodation", "hotel")) return false
        return true
    }

    private fun shouldBlockSpecificQueryChunk(chunk: VectorChunk, intents: Set<String>, transitInfo: Boolean): Boolean {
        val specific = transitInfo || intents.any { it in setOf("restaurant", "transit", "phrase", "emergency") }
        if (!specific) return false
        val category = chunk.category.lowercase(Locale.ROOT)
        val title = chunk.title.lowercase(Locale.ROOT)
        val text = chunk.text.trim().lowercase(Locale.ROOT)
        return category == "general" ||
            title.contains("overview") ||
            text.startsWith("redirect") ||
            title.contains("florence (oregon)") ||
            title.contains("florence (alabama)") ||
            title.contains("south carolina") ||
            title.contains("italy — overview") ||
            title.contains("tuscany — overview")
    }

    private fun categoryScore(intents: Set<String>, chunk: VectorChunk): Float {
        if (intents.isEmpty()) return 0f
        val category = normalizeCategory(chunk.category)
        if ("restaurant" in intents && category == "restaurant") return 1f
        if ("transit" in intents && category == "transit") return 1f
        if ("attraction" in intents && category == "attraction") return 1f
        if ("phrase" in intents && category == "phrase") return 1f
        if ("accommodation" in intents && category in setOf("accommodation", "hotel")) return 1f
        if ("emergency" in intents && category in setOf("emergency", "hospital", "police", "pharmacy", "phrase")) return 1f
        if ("toilet" in intents && category == "toilet") return 1f
        if ("atm" in intents && category == "atm") return 1f
        if ("water" in intents && category == "water") return 1f
        return 0f
    }

    private fun normalizeCategory(raw: String): String {
        val c = raw.lowercase(Locale.ROOT)
        return when {
            c.contains("restaurant") || c.contains("cafe") || c.contains("bar") || c.contains("food") -> "restaurant"
            c.contains("transit") || c.contains("bus") || c.contains("train") || c.contains("tram") || c.contains("station") -> "transit"
            c.contains("attraction") || c.contains("museum") || c.contains("church") || c.contains("monument") || c.contains("historic") -> "attraction"
            c.contains("phrase") -> "phrase"
            c.contains("emergency") -> "emergency"
            c.contains("hospital") || c.contains("clinic") -> "hospital"
            c.contains("police") -> "police"
            c.contains("pharmacy") || c.contains("farmacia") -> "pharmacy"
            c.contains("toilet") || c.contains("restroom") || c == "wc" -> "toilet"
            c.contains("atm") || c.contains("bank") -> "atm"
            c.contains("water") || c.contains("fountain") -> "water"
            c.contains("accommodation") || c.contains("airbnb") || c.contains("hotel") || c.contains("hostel") || c.contains("stay") || c.contains("lodging") -> "accommodation"
            else -> c
        }
    }

    private fun sourceScore(source: String): Float = when (source.lowercase(Locale.ROOT)) {
        "curated" -> 1f
        "openstreetmap", "osm" -> 0.9f
        "wikivoyage" -> 0.82f
        "transit_guide" -> 1f
        else -> 0.5f
    }

    private fun isBroadAttractionQuery(query: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        return BROAD_ATTRACTION_QUERY_PATTERNS.any { q.contains(it) }
    }

    private fun landmarkBoost(chunk: VectorChunk): Float {
        val haystack = "${chunk.title} ${chunk.text}".lowercase(Locale.ROOT)
        return if (KNOWN_FLORENCE_LANDMARKS.any { landmark -> haystack.contains(landmark.lowercase(Locale.ROOT)) }) 0.35f else 0f
    }

    private fun sourceBoost(chunk: VectorChunk): Float = when (chunk.source.lowercase(Locale.ROOT)) {
        "wikivoyage" -> 0.25f
        "florence_official" -> 0.20f
        "wikidata" -> 0.15f
        "openstreetmap", "osm" -> 0.0f
        else -> 0f
    }

    private fun genericOsmAttractionPenalty(chunk: VectorChunk): Float {
        val source = chunk.source.lowercase(Locale.ROOT)
        val category = normalizeCategory(chunk.category)
        if (source !in setOf("openstreetmap", "osm") || category != "attraction") return 0f
        return if (landmarkBoost(chunk) > 0f) 0f else -0.25f
    }

    private fun keywordScore(query: String, chunk: VectorChunk): Float {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return 0f
        val haystack = "${chunk.title} ${chunk.text} ${chunk.category}".lowercase(Locale.ROOT)
        val matched = queryTokens.count { token -> haystack.contains(token) }
        return matched.toFloat() / queryTokens.size.toFloat()
    }

    private fun exactMatchBoost(query: String, chunk: VectorChunk, transitInfo: Boolean): Float {
        val haystack = "${chunk.title} ${chunk.text}".lowercase(Locale.ROOT)
        if (transitInfo) {
            var boost = 0f
            if (haystack.contains("ticket")) boost += 0.6f
            if (haystack.contains("€")) boost += 0.6f
            if (haystack.contains("90 minutes")) boost += 0.4f
            if (haystack.contains("day pass")) boost += 0.4f
            if (haystack.contains("validate")) boost += 0.4f
            return boost
        }
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return 0f
        val importantTokensInQuery = queryTokens.intersect(EXACT_MATCH_TERMS)
        if (importantTokensInQuery.isEmpty()) return 0f
        return if (importantTokensInQuery.any { token -> haystack.contains(token) }) 0.35f else 0f
    }

    private fun transitInfoBoost(chunk: VectorChunk, transitInfo: Boolean): Float {
        if (!transitInfo) return 0f
        val source = chunk.source.lowercase(Locale.ROOT)
        val haystack = "${chunk.title} ${chunk.text}".lowercase(Locale.ROOT)
        var boost = 0f
        if (source == "transit_guide") boost += 1.5f else boost -= 0.2f
        if (haystack.contains("€")) boost += 0.35f
        if (haystack.contains("ticket")) boost += 0.35f
        if (haystack.contains("price")) boost += 0.30f
        if (haystack.contains("validity")) boost += 0.25f
        if (haystack.contains("90 minutes")) boost += 0.25f
        if (haystack.contains("day pass")) boost += 0.25f
        if (haystack.contains("validate")) boost += 0.25f
        return boost
    }

    private fun stopOnlyTransitPenalty(chunk: VectorChunk, transitInfo: Boolean): Float {
        if (!transitInfo) return 0f
        val source = chunk.source.lowercase(Locale.ROOT)
        if (source !in setOf("openstreetmap", "osm", "gtfs")) return 0f
        val text = chunk.text.trim()
        val looksLikeStopOnly = Regex("^TRANSIT:\\s*[^.]+\\.$", RegexOption.IGNORE_CASE).matches(text)
        return if (looksLikeStopOnly) -0.4f else 0f
    }

    private fun restaurantFoodBoost(query: String, chunk: VectorChunk, intents: Set<String>): Float {
        if ("restaurant" !in intents) return 0f
        val q = query.lowercase(Locale.ROOT)
        val haystack = "${chunk.title} ${chunk.text}".lowercase(Locale.ROOT)
        val specificFood = RESTAURANT_FOOD_TERMS.firstOrNull { q.contains(it) } ?: return 0f
        return if (haystack.contains(specificFood)) {
            if (specificFood == "gelato") 1.0f else 0.6f
        } else if (specificFood == "gelato") {
            -0.25f
        } else {
            0f
        }
    }

    private fun isTransitInfoQuery(query: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        return TRANSIT_INFO_QUERY_PATTERNS.any { q.contains(it) }
    }

    private fun isDistanceBetweenPlacesQuery(query: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        return q.contains("how far") ||
            q.contains("distance between") ||
            (q.contains("from") && q.contains("to")) ||
            q.contains("can i walk from") ||
            q.contains("walk from")
    }

    private fun buildDistanceBetweenPlacesResult(query: String): VectorSearchResult? {
        val q = query.lowercase(Locale.ROOT)
        val places = DISTANCE_LANDMARK_ALIASES.keys.filter { alias -> q.contains(alias) }
        if (places.size < 2) {
            Log.w(TAG, "DISTANCE QUERY FAILED TO RESOLVE BOTH PLACES")
            return null
        }

        val placeA = places[0]
        val placeB = places.firstOrNull { it != placeA }
        if (placeB == null) {
            Log.w(TAG, "DISTANCE QUERY FAILED TO RESOLVE BOTH PLACES")
            return null
        }

        val chunkA = findChunkForLandmark(placeA)
        val chunkB = findChunkForLandmark(placeB)
        if (chunkA == null || chunkB == null) {
            Log.w(TAG, "DISTANCE QUERY FAILED TO RESOLVE BOTH PLACES")
            return null
        }

        val latA = chunkA.lat
        val lonA = chunkA.lon
        val latB = chunkB.lat
        val lonB = chunkB.lon
        if (latA == null || lonA == null || latB == null || lonB == null) {
            Log.w(TAG, "DISTANCE QUERY FAILED TO RESOLVE BOTH PLACES")
            return null
        }

        val distanceKm = distanceKm(latA, lonA, latB, lonB)
        if (distanceKm == null) {
            Log.w(TAG, "DISTANCE QUERY FAILED TO RESOLVE BOTH PLACES")
            return null
        }

        val walkingMinutes = ((distanceKm / 4.8) * 60.0).roundToInt()
        val distanceText = String.format(Locale.ROOT, "%.2f", distanceKm)

        val displayA = canonicalLandmarkName(placeA)
        val displayB = canonicalLandmarkName(placeB)
        Log.d(
            TAG,
            "DISTANCE SYNTHETIC RESULT ADDED placeA=$displayA placeB=$displayB latA=$latA lonA=$lonA latB=$latB lonB=$lonB distanceKm=$distanceText walkingMinutes=$walkingMinutes",
        )

        val syntheticChunk = VectorChunk(
            id = "distance_calc_${displayA}_${displayB}".lowercase(Locale.ROOT).replace(" ", "_"),
            title = "Distance calculation",
            text = "Approximate straight-line distance between $displayA and $displayB: $distanceText km. Estimated walking time: $walkingMinutes minutes. This is not turn-by-turn routing.",
            category = "general",
            source = "curated",
            lat = null,
            lon = null,
            embedding = FloatArray(EXPECTED_VECTOR_SIZE),
        )

        return VectorSearchResult(
            chunk = syntheticChunk,
            vectorScore = 1f,
            keywordScore = 1f,
            exactMatchBoost = 1f,
            transitInfoBoost = 0f,
            transitStopPenalty = 0f,
            landmarkBoost = 0f,
            sourceBoost = 0f,
            finalScore = 999f,
            distanceKm = distanceKm,
        )
    }

    private fun findChunkForLandmark(alias: String): VectorChunk? {
        val normalizedAlias = alias.lowercase(Locale.ROOT)
        return pack.chunks.asSequence()
            .filter { it.lat != null && it.lon != null }
            .filter {
                val haystack = "${it.title} ${it.text}".lowercase(Locale.ROOT)
                haystack.contains(normalizedAlias)
            }
            .sortedByDescending { chunk ->
                var score = 0
                val category = normalizeCategory(chunk.category)
                val source = chunk.source.lowercase(Locale.ROOT)
                if (category in setOf("attraction", "transit") || chunk.category.lowercase(Locale.ROOT) == "general") score += 2
                if (source in setOf("wikivoyage", "wikidata", "openstreetmap", "osm")) score += 2
                score
            }
            .firstOrNull()
    }

    private fun canonicalLandmarkName(alias: String): String =
        DISTANCE_LANDMARK_ALIASES[alias] ?: alias.replaceFirstChar { it.uppercase() }

    private fun categoryCounts(chunks: List<VectorChunk>): String =
        chunks.groupingBy { normalizeCategory(it.category) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key}=${it.value}" }

    private fun sourceCounts(chunks: List<VectorChunk>): String =
        chunks.groupingBy { it.source.lowercase(Locale.ROOT) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key}=${it.value}" }

    private fun tokenize(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

    private fun distanceKm(userLat: Double?, userLon: Double?, lat: Double?, lon: Double?): Double? {
        if (userLat == null || userLon == null || lat == null || lon == null) return null
        val dLat = Math.toRadians(lat - userLat)
        val dLon = Math.toRadians(lon - userLon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(userLat)) * cos(Math.toRadians(lat)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371.0 * c
    }

    private fun formatDistance(distanceKm: Double?): String =
        if (distanceKm == null) "unknown" else "${"%.2f".format(distanceKm)} km"

    private fun matchesAny(query: String, words: Set<String>): Boolean = words.any { query.contains(it) }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val ai = a[i].toDouble()
            val bi = b[i].toDouble()
            dot += ai * bi
            normA += ai * ai
            normB += bi * bi
        }
        if (normA == 0.0 || normB == 0.0) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    companion object {
        private const val TAG = "BeyondMapsVectorRAG"
        private const val EXPECTED_VECTOR_SIZE = 384
        private val RESTAURANT_WORDS = setOf(
            "food", "eat", "restaurant", "restaurants", "cafe", "coffee", "gelato", "pizza", "breakfast", "lunch", "dinner",
        )
        private val TRANSIT_WORDS = setOf("bus", "train", "tram", "station", "stop", "transit", "transport")
        private val ATTRACTION_WORDS = setOf(
            "see", "visit", "attraction", "museum", "church", "duomo", "uffizi", "ponte vecchio", "viewpoint", "history",
        )
        private val PHRASE_WORDS = setOf("how do i say", "translate", "italian", "phrase", "pronounce")
        private val EMERGENCY_WORDS = setOf(
            "emergency", "help", "police", "hospital", "doctor", "medicine", "pharmacy", "sick",
        )
        private val TOILET_WORDS = setOf("toilet", "bathroom", "restroom", "wc")
        private val ATM_WORDS = setOf("atm", "cash", "money", "bank")
        private val WATER_WORDS = setOf("water", "refill", "fountain")
        private val ACCOMMODATION_WORDS = setOf(
            "hotel", "hostel", "airbnb", "accommodation", "stay", "lodging", "guesthouse",
        )
        private val EXACT_MATCH_TERMS = setOf(
            "gelato", "pizza", "pharmacy", "duomo", "tram", "ticket", "bus", "museum", "toilet", "atm", "water",
        )
        private val RESTAURANT_FOOD_TERMS = setOf("gelato", "pizza", "pasta", "coffee", "dinner", "lunch")
        private val TRANSIT_INFO_QUERY_PATTERNS = setOf(
            "ticket", "price", "cost", "fare", "buy", "validate", "validation", "pass",
            "public transport", "tram ticket", "bus ticket",
        )
        private val DISTANCE_LANDMARK_ALIASES = linkedMapOf(
            "duomo" to "Duomo",
            "uffizi" to "Uffizi",
            "ponte vecchio" to "Ponte Vecchio",
            "santa maria novella" to "Santa Maria Novella",
            "smn" to "Santa Maria Novella",
            "accademia" to "Accademia",
            "palazzo vecchio" to "Palazzo Vecchio",
            "boboli" to "Boboli",
            "pitti" to "Pitti",
            "santa croce" to "Santa Croce",
            "mercato centrale" to "Mercato Centrale",
            "piazza della signoria" to "Piazza della Signoria",
        )
        private val BROAD_ATTRACTION_QUERY_PATTERNS = setOf(
            "best places to visit",
            "what should i see",
            "top attractions",
        )
        private val KNOWN_FLORENCE_LANDMARKS = setOf(
            "Duomo",
            "Uffizi",
            "Ponte Vecchio",
            "Palazzo Vecchio",
            "Accademia",
            "Boboli",
            "Pitti",
            "Santa Maria Novella",
            "Piazza della Signoria",
            "Santa Croce",
            "Mercato Centrale",
        )
    }
}
