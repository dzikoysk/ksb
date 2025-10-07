@file:Suppress("SpellCheckingInspection")

package com.dzikoysk.ksb

import ksb
import ksb.IO.readAsObject
import ksb.IO.readText
import ksb.Serialization.convertTo
import ksb.Serialization.toJson
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files

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

        @Nested
        inner class GraphQL {
            @Test
            fun `graphlql works`() {
                data class Monke(val name: String)
                data class MonkeData(val monke: Monke)

                val response =
                    ksb.http.server
                        .start { post("/graphql") { it.result("""{ "data": { "monke": { "name": "Młynarz" } } } }""") } }
                        .use { (_, url) -> ksb.http.gql.query<MonkeData>("$url/graphql", mapOf("x-api-key" to "123")) { "query Monke { name }" } }

                assert(response.isSuccess)
                assert(response.get().data.monke == Monke("Młynarz"))
            }
        }
    }

    @Nested
    inner class Fs {

        @Test
        fun `fs works`() {
            val testDirectory = Files.createTempDirectory("ksb")
            val path = testDirectory.resolve("monke.txt").toString()

            ksb.fs.override(path) { "Młynarz" }
            assert(ksb.fs.read(path) == listOf("Młynarz"))
        }

    }

}