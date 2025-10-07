import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.power-assert") version "2.0.0"
    `maven-publish`
    `java-library`
}

group = "com.dzikoysk"
version = "0.0.2-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.reposilite.com/snapshots")
}

publishing {
    repositories {
        maven {
            name = "reposilite-repository"
            url = uri("https://maven.reposilite.com/${if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases"}")

            credentials {
                username = getEnvOrProperty("MAVEN_NAME", "mavenUser")
                password = getEnvOrProperty("MAVEN_TOKEN", "mavenPassword")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components.getByName("java"))
        }
    }
}

dependencies {
    val jackson = "2.18.0"
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jackson")

    val javalin = "6.3.0"
    api("io.javalin:javalin:$javalin")
    api("io.javalin.community.ssl:ssl-plugin:$javalin")

    val expressible = "1.3.6"
    api("org.panda-lang:expressible:$expressible")
    api("org.panda-lang:expressible-kt:$expressible")
    testImplementation("org.panda-lang:expressible-junit:$expressible")

    val cdn = "1.14.5"
    api("net.dzikoysk:cdn:$cdn")
    api("net.dzikoysk:cdn-kt:$cdn")

    val picocli = "4.7.6"
    api("info.picocli:picocli:$picocli")

    val awssdk = "2.28.16"
    implementation(platform("software.amazon.awssdk:bom:$awssdk"))
    implementation("software.amazon.awssdk:s3:$awssdk")

    val awsSdkV1 = "1.12.773"
    testImplementation("com.amazonaws:aws-java-sdk-s3:$awsSdkV1")

    val bcrypt = "0.10.2"
    implementation("at.favre.lib:bcrypt:$bcrypt")

    val jdbi = "3.41.3"
    api("org.jdbi:jdbi3-core:$jdbi")
    api("org.jdbi:jdbi3-sqlobject:$jdbi")
    api("org.jdbi:jdbi3-postgres:$jdbi")
    api("org.jdbi:jdbi3-sqlite:$jdbi")
    api("org.jdbi:jdbi3-kotlin:$jdbi")
    api("org.jdbi:jdbi3-kotlin-sqlobject:$jdbi")
    api("org.jdbi:jdbi3-jackson2:$jdbi")

    val unirest = "4.4.4"
    implementation("com.konghq:unirest-java-core:$unirest")
    implementation("com.konghq:unirest-modules-jackson:$unirest")

    val logback = "1.4.12"
    implementation("ch.qos.logback:logback-classic:$logback")

    testImplementation(kotlin("test"))
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf("kotlin.assert", "kotlin.test.assertTrue", "kotlin.test.assertEquals", "kotlin.test.assertNull")
    includedSourceSets = listOf("commonMain", "jvmMain", "jsMain", "nativeMain")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    withJavadocJar()
    withSourcesJar()
}

fun getEnvOrProperty(env: String, property: String): String? =
    System.getenv(env) ?: findProperty(property)?.toString()