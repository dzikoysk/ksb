
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.javalin.Javalin
import io.javalin.router.JavalinDefaultRouting
import ksb.Serialization.convertTo
import java.io.Closeable
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Suppress("unused", "ClassName", "MemberVisibilityCanBePrivate")
object ksb {

    val serialization = Serialization
    val http = Http

    object Serialization {

        val defaultObjectMapper: ObjectMapper by lazy { useObjectMapper() }

        fun useObjectMapper(
            onlyNonNullValues: Boolean = true,
            failOnUnknownProperties: Boolean = false,
        ): ObjectMapper =
            JsonMapper.builder()
                .addModule(JavaTimeModule())
                .addModule(SimpleModule())
                .build()
                .registerKotlinModule()
                .let { if (onlyNonNullValues) it.setSerializationInclusion(JsonInclude.Include.NON_NULL) else it }
                .let { if (failOnUnknownProperties) it else it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) }

        inline fun <reified T> String.convertTo(objectMapper: ObjectMapper = defaultObjectMapper): T =
            objectMapper.readValue(this, T::class.java)

        fun <T> T.toJson(objectMapper: ObjectMapper = defaultObjectMapper): String =
            objectMapper.writeValueAsString(this)

    }

    object Http {

        private val defaultHttpClient by lazy {
            HttpClient.newHttpClient()
        }

        enum class KsbHttpMethod {
            HEAD, GET, POST, PUT, PATCH, UPDATE, DELETE
        }

        data class KsbHttpResponse(
            val body: InputStream,
            val statusCode: Int,
            val headers: Map<String, List<String>>,
        )

        fun head(url: String, headers: Map<String, Any> = emptyMap()): KsbHttpResponse =
            sendRequest(KsbHttpMethod.HEAD, url, null, headers)

        fun get(url: String, headers: Map<String, Any> = emptyMap()): KsbHttpResponse =
            sendRequest(KsbHttpMethod.GET, url, null, headers)

        fun post(url: String, value: String, headers: Map<String, Any> = emptyMap()): KsbHttpResponse =
            sendRequest(KsbHttpMethod.POST, url, value, headers)

        fun put(url: String, value: String, headers: Map<String, Any> = emptyMap()): KsbHttpResponse =
            sendRequest(KsbHttpMethod.PUT, url, value, headers)

        fun patch(url: String, value: String, headers: Map<String, Any> = emptyMap()): KsbHttpResponse =
            sendRequest(KsbHttpMethod.PATCH, url, value, headers)

        fun update(url: String, value: String, headers: Map<String, Any> = emptyMap()): KsbHttpResponse =
            sendRequest(KsbHttpMethod.UPDATE, url, value, headers)

        fun delete(url: String, headers: Map<String, Any> = emptyMap()): KsbHttpResponse =
            sendRequest(KsbHttpMethod.DELETE, url, null, headers)

        private fun sendRequest(method: KsbHttpMethod, url: String, value: String?, headers: Map<String, Any> = emptyMap()): KsbHttpResponse {
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .also { headers.forEach { (key, value) -> it.header(key, value.toString()) } }
                    .let {
                        when(method) {
                            KsbHttpMethod.HEAD -> it.method("HEAD", HttpRequest.BodyPublishers.noBody())
                            KsbHttpMethod.GET -> it.GET()
                            KsbHttpMethod.POST -> it.POST(HttpRequest.BodyPublishers.ofString(value.toString()))
                            KsbHttpMethod.PUT -> it.PUT(HttpRequest.BodyPublishers.ofString(value.toString()))
                            KsbHttpMethod.PATCH -> it.method("PATCH", HttpRequest.BodyPublishers.ofString(value.toString()))
                            KsbHttpMethod.UPDATE -> it.method("UPDATE", HttpRequest.BodyPublishers.ofString(value.toString()))
                            KsbHttpMethod.DELETE -> it.DELETE()
                        }
                    }
                    .build()
            val response = defaultHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

            return KsbHttpResponse(
                statusCode = response.statusCode(),
                headers = response.headers().map(),
                body = response.body(),
            )
        }

        val server = Server

        object Server {

            data class RunningServer(
                val server: Javalin,
                val url: String,
            ) : Closeable {
                override fun close() {
                    server.stop()
                }
            }

            fun start(port: Int = 8080, routing: JavalinDefaultRouting.() -> Unit): RunningServer =
                RunningServer(
                    server = Javalin.createAndStart { config ->
                        config.showJavalinBanner = false
                        config.jetty.defaultPort = port
                        config.router.mount { routing(it) }
                    },
                    url = "http://localhost:$port",
                )

        }
    }

    object IO {

        fun InputStream.readText(): String =
            bufferedReader().use { it.readText() }

        inline fun <reified T> InputStream.readAsObject(): T =
            readText().convertTo()

    }

    object DataTypes {

        data class Quad<A, B, C, D>(
            val first: A,
            val second: B,
            val third: C,
            val fourth: D,
        )

    }
}