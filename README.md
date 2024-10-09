# KSB [![Reposilite](https://maven.reposilite.com/api/badge/latest/snapshots/com%2Fdzikoysk%2Fksb?name=Reposilite)](https://maven.reposilite.com/#/snapshots/com/dzikoysk/ksb)

**K**otlin **S**cripting **B**undle - all-in-one set of tools for rapid prototyping & scripting. Use `ksb` object as an entry point to the library.
You don't need to remember nor import anything from the library, just discover the API via the intellisense.

```kotlin
repository {
    maven("https://maven.reposilite.com/snapshots")
}

dependencies {
    implementation("com.dzikoysk:ksb:0.0.1-SNAPSHOT")
}
```

### Docs

#### Serialization

```kotlin
data class Monke(
    val name: String
)

val json: String = Monke("Młynarz").toJson() // available for any object
val deserializedObject: Monke = json.convertTo()
```

#### HTTP

Serving the API:

```kotlin
val (server, address) = ksb.http.server.start { 
    get("/api/read") { it.result("Monke") }
    post("/api/write") { println(it.body()) }
}
```

Interacting with the API:

* `val (body, statusCode, headers) = ksb.http.get("https://github.com/dzikoysk/ksb")`
* `val (body, statusCode, headers) = ksb.http.post("https://github.com/dzikoysk/ksb")`

```kotlin
val monke = body.readText() / body.readAsObject<Monke>()
```