// app/src/main/java/com/coffeehouse/model/CustomPreset.kt
package com.coffeehouse.model

/**
 * A user-named snapshot of [CafeSettings] (spec Phase 5, §1).
 *
 * Persisted to DataStore as a hand-rolled JSON string keyed under
 * "custom_preset_{safeName}". A real JSON library is deliberately
 * avoided — the schema below is fixed and controlled, so regex
 * field extraction is sufficient and keeps the dependency footprint
 * unchanged from Phase 4.
 *
 * Serialised format:
 *   {"name":"...","reverbPreset":N,"reverbEnabled":B,
 *    "virtualizerStrength":N,"virtualizerEnabled":B,
 *    "bassBoostStrength":N,"bassBoostEnabled":B,
 *    "eqEnabled":B,"eqBands":[N,N,N,N,N]}
 */
data class CustomPreset(
    val name: String,
    val settings: CafeSettings,
) {
    companion object {

        /**
         * Serialise [preset] to the fixed JSON shape documented above.
         * Uses string templates only — no external JSON library.
         *
         * Note: [preset.name] is escaped for backslash and double-quote so a
         * pathological user-typed name like `She said "hi"` can still
         * round-trip without breaking the parser.
         */
        fun toJson(preset: CustomPreset): String {
            val s = preset.settings
            val escapedName = preset.name
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            val eqBands = s.eqBands.joinToString(separator = ",")
            return "{" +
                "\"name\":\"$escapedName\"," +
                "\"reverbPreset\":${s.reverbPreset}," +
                "\"reverbEnabled\":${s.reverbEnabled}," +
                "\"virtualizerStrength\":${s.virtualizerStrength}," +
                "\"virtualizerEnabled\":${s.virtualizerEnabled}," +
                "\"bassBoostStrength\":${s.bassBoostStrength}," +
                "\"bassBoostEnabled\":${s.bassBoostEnabled}," +
                "\"eqEnabled\":${s.eqEnabled}," +
                "\"eqBands\":[$eqBands]" +
                "}"
        }

        /**
         * Parse a JSON string produced by [toJson] back into a [CustomPreset].
         * Returns null on any parse failure — never throws.
         *
         * Regex field extraction is safe here because the schema is fixed and
         * field order is invariant. A missing or malformed field aborts the
         * whole parse so partial / corrupt records don't silently load with
         * default values.
         */
        fun fromJson(json: String): CustomPreset? {
            return try {
                // Name: capture text between double quotes after "name": —
                // tolerant of escape sequences inside the value.
                val name = Regex(""""name":"((?:\\.|[^"\\])*)"""")
                    .find(json)?.groupValues?.get(1)
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\")
                    ?: return null

                val reverbPreset = Regex(""""reverbPreset":(-?\d+)""")
                    .find(json)?.groupValues?.get(1)?.toShortOrNull() ?: return null
                val reverbEnabled = Regex(""""reverbEnabled":(true|false)""")
                    .find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: return null
                val virtualizerStrength = Regex(""""virtualizerStrength":(-?\d+)""")
                    .find(json)?.groupValues?.get(1)?.toShortOrNull() ?: return null
                val virtualizerEnabled = Regex(""""virtualizerEnabled":(true|false)""")
                    .find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: return null
                val bassBoostStrength = Regex(""""bassBoostStrength":(-?\d+)""")
                    .find(json)?.groupValues?.get(1)?.toShortOrNull() ?: return null
                val bassBoostEnabled = Regex(""""bassBoostEnabled":(true|false)""")
                    .find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: return null
                val eqEnabled = Regex(""""eqEnabled":(true|false)""")
                    .find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: return null

                val eqBandsRaw = Regex(""""eqBands":\[([^\]]*)\]""")
                    .find(json)?.groupValues?.get(1) ?: return null
                val eqBands: List<Int> = if (eqBandsRaw.isBlank()) {
                    emptyList()
                } else {
                    val parsed = mutableListOf<Int>()
                    for (token in eqBandsRaw.split(",")) {
                        parsed.add(token.trim().toIntOrNull() ?: return null)
                    }
                    parsed
                }

                CustomPreset(
                    name = name,
                    settings = CafeSettings(
                        reverbPreset        = reverbPreset,
                        reverbEnabled       = reverbEnabled,
                        virtualizerStrength = virtualizerStrength,
                        virtualizerEnabled  = virtualizerEnabled,
                        bassBoostStrength   = bassBoostStrength,
                        bassBoostEnabled    = bassBoostEnabled,
                        eqBands             = eqBands,
                        eqEnabled           = eqEnabled,
                    ),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
