@file:Suppress("SpellCheckingInspection")

package com.dzikoysk.ksb

import ksb
import ksb.Csv.SortOrder.DESC
import ksb.IO.readAsObject
import ksb.Serialization.convertTo
import ksb.Serialization.toJson
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Duration
import java.util.function.Function
import kotlin.test.assertEquals

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
                assert(response.get().monke == Monke("Młynarz"))
            }

            @Test
            fun `graphql error works`() {
                val response =
                    ksb.http.server
                        .start { post("/graphql") { it.result("""{ "errors": [ { "message": "Bad Request" } ] }""") } }
                        .use { (_, url) -> ksb.http.gql.query<Monke>("$url/graphql", mapOf("x-api-key" to "123")) { "query Monke { name }" } }

                assert(response.isFailure)
                assertEquals("Bad Request", response.error().message)
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

    @Nested
    inner class Csv {
        @Test
        fun `csv rows works`() {
            val elements = listOf(1, 2, 3)

            val result = ksb.csv.rows(elements) { data ->
                cell { "input" to data }
                cell { "double" to data * 2 }
                cell { "triple" to when (data) {
                    1 -> Duration.ofSeconds(1)
                    2 -> Duration.ofSeconds(12)
                    3 -> Duration.ofSeconds(22)
                    else -> error("Unexpected data")
                } }
            }

            ksb.csv.addFormatter<Duration> { duration ->
                duration?.let { "${it.toMinutes() }min" } ?: ""
            }

            assertEquals(
                """
                input,double,triple
                3,6,0min
                2,4,0min
                1,2,0min
                """.trimIndent(),
                result.toString(
                    sortedBy = listOf("triple" to DESC)
                )
            )
        }

        @Test
        fun `csv columns works`() {
            assertEquals(
                """
                input,double,triple
                1,2,3
                2,4,6
                3,6,9
                """.trimIndent(),
                ksb.csv(
                    "input" to listOf(1, 2, 3),
                    "double" to listOf(2, 4, 6),
                    "triple" to listOf(3, 6, 9),
                ).toString()
            )
        }
    }

}