/*
 * Copyright 2020 Google, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

pluginManagement {
    repositories {
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
        google()
        maven("https://jitpack.io")
    }
    val spotlessVersion: String by settings
    val ktlintVersion: String by settings
    val kotlinVersion: String by settings
    plugins {
        id("com.diffplug.spotless") version spotlessVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("jvm") version kotlinVersion
        id("androidx.build.gradle.gcpbuildcache") version "e1b210f69afed"
    }
    // TODO remove when that plugin is shipped to gradle plugin portal
    this.resolutionStrategy.eachPlugin {
        if (this.requested.id.id == "androidx.build.gradle.gcpbuildcache") {
            this.useModule("com.github.androidx:gcp-gradle-build-cache:e1b210f69afed")
        }
    }
}

plugins {
    id("androidx.build.gradle.gcpbuildcache")
    id("com.gradle.enterprise") version("3.10")
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        capture {
            isTaskInputFiles = true
        }
    }
}

val gcpKey = providers.environmentVariable("GRADLE_CACHE_KEY").orNull
    ?: providers.environmentVariable("GRADLE_CACHE_FILE").orNull?.let {
        File(it).readText()
    }
val cacheIsPush = providers.environmentVariable("GRADLE_CACHE_PUSH").orNull?.toBoolean() ?: false
if (gcpKey != null) {
    println("setting up remote build cache with push: $cacheIsPush")
    buildCache {
        remote(androidx.build.gradle.gcpbuildcache.GcpBuildCache::class) {
            projectId = "kotlin-sqlite-bindings"
            bucketName = "kotlin-sqlite-bindings-cache"
            credentials = androidx.build.gradle.gcpbuildcache.ExportedKeyGcpCredentials {
                gcpKey
            }
            isPush = cacheIsPush
        }
    }
} else {
    println("not using remote build cache")
}
includeBuild("buildPlugin")
include("sqlitebindings", "sqlitebindings-api", "jnigenerator", "ksqlite3")
