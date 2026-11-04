/*
 * Copyright (c) Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.work.DisableCachingByDefault

plugins {
    alias(libs.plugins.android.application)
}

@DisableCachingByDefault(because = "The R8 rule contains the local dependency cache path")
abstract class GenerateDesugarLibraryApiRules : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val desugarLibrary: RegularFileProperty

    @get:OutputFile
    abstract val rulesFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val rules = rulesFile.get().asFile
        rules.parentFile.mkdirs()
        rules.writeText(
                "-libraryjars \"${desugarLibrary.get().asFile.invariantSeparatorsPath}\"" +
                        "(java/time/zone/ZoneRulesProvider.class)\n"
        )
    }
}

val desugarLibraryApi by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

// ZoneRulesProvider is hidden from Android SDK stubs. Give R8 only its real API
// as a library type, not compileOnly classes that shadow other Android APIs.
// Core-library desugaring still rewrites references and L8 supplies the runtime class.
val generateDesugarLibraryApiRules by tasks.registering(GenerateDesugarLibraryApiRules::class) {
    desugarLibrary.set(layout.file(desugarLibraryApi.elements.map { it.single().asFile }))
    rulesFile.set(layout.buildDirectory.file("generated/r8/desugar-library-api.pro"))
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.proguardFiles.add(generateDesugarLibraryApiRules.flatMap { it.rulesFile })
    }
}

/*
 * Release signing: set these Gradle properties (for instance in ~/.gradle/gradle.properties,
 * with -P on the command line or as ORG_GRADLE_PROJECT_<name> environment variables).
 * If any of them is missing, the release build is not signed.
 */
val releaseSigningProperties = listOf("releaseStoreFile", "releaseStorePassword", "releaseKeyAlias", "releaseKeyPassword")
        .associateWith { providers.gradleProperty(it).orNull }

android {
    namespace = "com.etesync.syncadapter"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.etesync.syncadapter"

        minSdk = 23
        targetSdk = 36

        versionCode = 30000
        versionName = "3.0.0"

        buildConfigField("boolean", "customCerts", "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningProperties.values.all { it != null })
            create("release") {
                storeFile = file(releaseSigningProperties.getValue("releaseStoreFile")!!)
                storePassword = releaseSigningProperties.getValue("releaseStorePassword")
                keyAlias = releaseSigningProperties.getValue("releaseKeyAlias")
                keyPassword = releaseSigningProperties.getValue("releaseKeyPassword")
            }
    }

    buildTypes {
        debug {
            /*
             * To override the server's url (for example when developing),
             * create file gradle.properties in ~/.gradle/ with this content:
             *
             * appDebugRemoteUrl="http://localserver:8080/"
             */
            buildConfigField("String", "DEBUG_REMOTE_URL", providers.gradleProperty("appDebugRemoteUrl").getOrElse("null"))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.txt")
            buildConfigField("String", "DEBUG_REMOTE_URL", "null")

            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        // ical4android and vcard4android use the Java 8 Time API
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        // per-app language support (Android 13+)
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += setOf("LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt")
        }
    }

    lint {
        disable += setOf(
                "GradleDependency", "GradleDynamicVersion",
                "IconColors", "IconLauncherShape", "IconMissingDensityFolder",
                "ImpliedQuantity", "MissingQuantity", "MissingTranslation", "ExtraTranslation",
                "InvalidPackage", "Recycle", "RtlEnabled", "RtlHardcoded", "Typos", "RestrictedApi"
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    add(desugarLibraryApi.name, libs.desugar.jdk.libs)

    implementation(project(":cert4android"))
    implementation(project(":ical4android"))
    implementation(project(":vcard4android"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(project(":etebase-client"))

    implementation(libs.acra.http)
    implementation(libs.acra.dialog)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
