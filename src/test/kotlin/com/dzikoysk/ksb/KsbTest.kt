@file:Suppress("SpellCheckingInspection")

package com.dzikoysk.ksb

import ksb.IO.readAsObject
import ksb.IO.readText
import ksb.Serialization.convertTo
import ksb.Serialization.toJson
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KsbTest {

    private data class Monke(
        val name: String
    )

    @Nested
    inner class Serialization {
        @Test
        fun `serialization works`() {
            val json = Monke("Młynarz").toJson()
            val deserializedObject = json.convertTo<Monke>()

            assert(Monke("Młynarz") == deserializedObject)
        }
    }

@Nested
inner class Http {
    @Test
    fun `http works`() {
        val response =
            ksb.http.server
                .start { get("/api") { it.json(Monke("Młynarz")) } }
                .use { (_, url) -> ksb.http.get("$url/api") }

        assert(response.statusCode == 200)
        assert(response.body.readAsObject<Monke>() == Monke("Młynarz"))
    }
}

}