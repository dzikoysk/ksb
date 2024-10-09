# KSB [![Reposilite](https://maven.reposilite.com/api/badge/latest/snapshots/com%2Fdzikoysk%2Fksb?name=Reposilite)](https://maven.reposilite.com/#/snapshots/com/dzikoysk/ksb)

**K**otlin **S**cripting **B**undle - all-in-one set of tools for rapid prototyping & scripting. Use `ksb` object as an entry point to the whole API.
You don't need to remember nor import anything from the library, just discover all you need via the intellisense.

```kotlin
repository {
    maven("https://maven.reposilite.com/snapshots")
}

dependencies {
    implementation("com.dzikoysk:ksb:0.0.1-SNAPSHOT")
}
```

## Docs

### Serialization

* JSON

```kotlin
data class Monke(
    val name: String
)

val json: String = Monke("Młynarz").toJson() // available for any object
val deserializedObject: Monke = json.convertTo()
```

* CSV _(todo)_

### HTTP

Serving the API:

```kotlin
val (server, address) = ksb.http.server.start { 
    get("/api/read") { it.result("Monke") }
    post("/api/write") { println(it.body()) }
}
```

Interacting with the API:

```kotlin
val (_, statusCode, headers) = ksb.http.head("https://github.com/dzikoysk/ksb")
val (body, statusCode, headers) = ksb.http.get("https://github.com/dzikoysk/ksb")
val (body, statusCode, headers) = ksb.http.delete("https://github.com/dzikoysk/ksb")

val (body, statusCode, headers) = ksb.http.post("https://github.com/dzikoysk/ksb", Monke("Młynarz").toJson())
val (body, statusCode, headers) = ksb.http.update("https://github.com/dzikoysk/ksb", Monke("Młynarz").toJson())

val (body, statusCode, headers) = ksb.http.patch(
    url = "https://github.com/dzikoysk/ksb", 
    value = Monke("Młynarz").toJson(),
    headers = mapOf("X-API-KEY" to "123")
)

val monke = body.readText() / body.readAsObject<Monke>()
```

#### Cloud

* AWS S3 _(todo)_
* Google Drive _(todo)_
