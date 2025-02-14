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

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("maven-publish")
    id("ksqlite-build")
}

ksqliteBuild {
    native(includeAndroidNative = false)
    android()
    publish()
    buildOnServer()
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(project(":sqlitebindings"))
                implementation(kotlin("stdlib"))
                api(project(":sqlitebindings-api"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
//        val androidMain by getting {
//            dependencies {
//                implementation(kotlin("stdlib-jdk8"))
//            }
//        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                com.birbit.ksqlite.build.Dependencies.ANDROID_TEST.forEach {
                    implementation(it)
                }
            }
        }
        // Default source set for JVM-specific sources and dependencies:
//        jvm().compilations["main"].defaultSourceSet {
//            dependencies {
//                implementation(kotlin("stdlib-jdk8"))
//            }
//        }
        // JVM-specific tests and their dependencies:
        jvm().compilations["test"].defaultSourceSet {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val linuxTest by creating
        val windowsTest by creating
        val macTest by creating
        this.forEach { ss ->
            if (ss.name.endsWith("Test")) {
                val osSourceSet = when {
                    ss.name.startsWith("mac") -> macTest
                    ss.name.startsWith("ios") -> macTest
                    ss.name.startsWith("linux") -> linuxTest
                    ss.name.startsWith("mingw") -> windowsTest
                    else -> null
                }
                osSourceSet?.let {
                    if (it.name != ss.name) {
                        ss.dependsOn(it)
                    }
                }
            }
        }
    }
}
