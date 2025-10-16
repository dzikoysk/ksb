
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.javalin.Javalin
import io.javalin.router.JavalinDefaultRouting
import ksb.IO.readAsObject
import ksb.IO.readText
import ksb.Serialization.convertTo
import ksb.Serialization.toJson
import java.io.Closeable
import java.io.InputStream
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Function
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

typealias ColumnName = String

@Suppress("unused", "ClassName", "MemberVisibilityCanBePrivate")
object ksb {

    val serialization = Serialization

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
            objectMapper.readValue(
                this,
                objectMapper.typeFactory.constructType(typeOf<T>().javaType),
            )

        fun <T> T.toJson(objectMapper: ObjectMapper = defaultObjectMapper): String =
            objectMapper.writeValueAsString(this)

    }

    val http = Http

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

        val gql = GraphQL

        object GraphQL {

            data class Query(
                val query: String,
            )

            data class Response<T : Any?>(
                val data: T?,
                val errors: List<Error>?,
            ) {
                data class Error(
                    val message: String,
                )
            }

            data class ErrorResponse(
                val status: Int,
                val message: String,
            )

            inline fun <reified T : Any?> query(
                url: String,
                headers: Map<String, Any>,
                query: () -> String,
            ): Result<T, ErrorResponse> =
                post(
                    url = url,
                    value = Query(query = query()).toJson(),
                    headers =
                        mapOf(
                            "Content-Type" to "application/json",
                            "Accept" to "application/json",
                        ) + headers,
                ).let { response ->
                    when (response.statusCode) {
                        in 200..299 ->
                            response.body.readAsObject<Response<T>>().let { gqlResponse ->
                                when {
                                    gqlResponse.errors.isNullOrEmpty() -> Result.success(gqlResponse.data!!)
                                    else -> Result.failure(ErrorResponse(response.statusCode, gqlResponse.errors.joinToString { it.message }))
                                }
                            }
                        else -> Result.failure(ErrorResponse(response.statusCode, response.body.readText()))
                    }
                }

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

    val fs = FS

    object FS {

        fun override(path: String, content: () -> String) {
            val path = Path.of(path).toAbsolutePath()
            Files.createDirectories(path.parent)
            Files.writeString(path, content(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        }

        fun read(path: String): List<String> = Files.readAllLines(Path.of(path))

    }

    val csv = Csv

    object Csv {

        val defaultComparators = mutableMapOf<KClass<out Any>, Comparator<out Any?>>()
        val defaultFormatters = mutableMapOf<KClass<out Any>, Function<out Any?, String>>()

        init {
            addComparator<Comparable<Any?>> { a, b -> a?.compareTo(b) ?: if (b == null) 0 else -1 }
            addComparator<Any> { a, b -> a.toString().compareTo(b.toString()) }

            addFormatter<Any> { it.toString() }
        }

        inline fun <reified T : Any> addComparator(comparator: Comparator<T?>) {
            defaultComparators[T::class] = comparator
        }

        inline fun <reified T : Any> addFormatter(formatter: Function<T?, String>) {
            defaultFormatters[T::class] = formatter
        }

        enum class SortOrder {
            ASC, DESC
        }

        operator fun invoke(vararg columns: Pair<ColumnName, List<Any?>>): CsvSource {
            val csvSource = CsvSource()

            val rowCount =
                columns
                    .map { it.second.size }
                    .distinct()
                    .singleOrNull()
                    ?: throw IllegalArgumentException("All columns must have the same number of rows")

            for (idx in 0 until rowCount) {
                columns.forEach { (column, values) ->
                    csvSource.cell { column to values[idx] }
                }
                csvSource.flushRow()
            }

            return csvSource
        }

        fun <T> rows(input: Collection<T>, body: CsvSource.(T) -> Unit): CsvSource {
            val csvSource = CsvSource()
            input.forEach {
                csvSource.apply { body(it) }
                csvSource.flushRow()
            }
            return csvSource
        }

        class CsvSource {

            data class Cell(
                val column: ColumnName,
                val value: Any?,
            )

            private val rows = mutableListOf<List<Cell>>()
            private val currentRow = mutableListOf<Cell>()

            fun cell(body: () -> Pair<ColumnName, Any?>) {
                body().let { (column, value) ->
                    currentRow.add(
                        Cell(
                            column = column,
                            value = when (value) {
                                is String -> "\"${value.replace("\"", "\"\"")}\""
                                else -> value
                            },
                        )
                    )
                }
            }

            internal fun flushRow() {
                rows.add(currentRow.toList())
                currentRow.clear()
            }

            fun toString(
                sortedBy: List<Pair<ColumnName, SortOrder>> = emptyList(),
                default: String = "",
            ): String {
                if (rows.isEmpty()) {
                    return default
                }

                val columns = rows.first().map { it.column }
                val comparators =
                    sortedBy
                        .takeIf { it.isNotEmpty() }
                        ?.map { (column, order) ->
                            if (column !in columns) {
                                throw IllegalArgumentException("Column '$column' not found in the CSV")
                            }

                            val comparators = defaultComparators.toSortedMap(Utils.classComparator)
                            val comparator =
                                Comparator<List<Cell>> { row1, row2 ->
                                    val cell1 = row1.first { it.column == column }.value
                                    val cell2 = row2.first { it.column == column }.value
                                    val cellClass = listOfNotNull(cell1, cell2).firstOrNull()?.let { it::class } ?: Any::class

                                    @Suppress("UNCHECKED_CAST")
                                    val comparator =
                                        comparators
                                            .entries
                                            .firstOrNull { (key, _) -> key == cellClass || key.isSuperclassOf(cellClass) }
                                            ?.value as Comparator<Any?>

                                    comparator.compare(cell1, cell2)
                                }

                            when (order) {
                                SortOrder.ASC -> comparator
                                SortOrder.DESC -> comparator.reversed()
                            }
                        }
                        ?.reduce { acc, comparator -> acc.then(comparator) }
                val sortedRows = comparators?.let { rows.sortedWith(it) } ?: rows

                val formatters = defaultFormatters.toSortedMap(Utils.classComparator)
                val body = sortedRows.joinToString("\n") { row ->
                    row.joinToString(",") { cell ->
                        @Suppress("UNCHECKED_CAST")
                        val formatter =
                            formatters
                                .entries
                                .first { (key, _) -> cell.value != null && (key == cell.value::class || key.isSuperclassOf(cell.value::class)) }
                                .value as Function<Any?, String>

                        formatter.apply(cell.value)
                    }
                }
                val header = columns.joinToString(",")

                return "$header\n$body"
            }

            override fun toString(): String = toString(default = "")
        }

    }

    val utils = Utils

    object Utils {

        val classComparator = Comparator<KClass<*>> { a, b ->
            when {
                a == b -> 0
                a.isSuperclassOf(b) -> 1
                b.isSuperclassOf(a) -> -1
                else -> 0
            }
        }

    }

    @Suppress("DATA_CLASS_COPY_VISIBILITY_WILL_BE_CHANGED_WARNING")
    data class Result<T, E> private constructor(
        private val data: T?,
        private val error: E?,
    ) {
        init {
            require((data == null) != (error == null)) { "Either data or error must be non-null" }
        }

        companion object {
            fun <T, E> success(data: T): Result<T, E> = Result(data = data, error = null)
            fun <T, E> failure(error: E): Result<T, E> = Result(data = null, error = error)
        }

        val isSuccess: Boolean get() = data != null
        val isFailure: Boolean get() = error != null

        fun <M> map(transform: (T) -> M): Result<M, E> = if (isSuccess) success(transform(data!!)) else failure(error!!)
        fun <F> mapError(transform: (E) -> F): Result<T, F> = if (isFailure) failure(transform(error!!)) else success(data!!)

        fun <M> flatMap(transform: (T) -> Result<M, E>): Result<M, E> = if (isSuccess) transform(data!!) else failure(error!!)
        fun <F> flatMapError(transform: (E) -> Result<T, F>): Result<T, F> = if (isFailure) transform(error!!) else success(data!!)

        fun <R> fold(onSuccess: (T) -> R, onFailure: (E) -> R): R = if (isSuccess) onSuccess(data!!) else onFailure(error!!)
        fun peek(onSuccess: (T) -> Unit = {}, onFailure: (E) -> Unit = {}): Result<T, E> {
            if (isSuccess) onSuccess(data!!) else onFailure(error!!)
            return this
        }

        fun orThrow(): T = data ?: throw IllegalStateException("No data present, error: $error")
        fun orElse(defaultValue: (E) -> T): T = data ?: defaultValue(error!!)

        fun get(): T = data!!
        fun error(): E = error!!

        override fun toString(): String = if (isSuccess) "Success($data)" else "Failure($error)"
    }

    data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

}