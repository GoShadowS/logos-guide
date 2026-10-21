import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.logos.navigator"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.logos.navigator"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.0-opendoors"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "OPEN_DOORS", "false")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "OPEN_DOORS", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
            )
        }
    }
}

val releaseApkFileName = "logos-guide-1.0.0-opendoors.apk"
val releaseApkOutputDirectory = layout.buildDirectory.dir("outputs/apk/release")
val androidStudioReleaseApkDirectory = layout.projectDirectory.dir("release").asFile
val copyReleaseApk = tasks.register("copyReleaseApk") {
    doLast {
        val outputDirectory = releaseApkOutputDirectory.get().asFile
        val outputApk = outputDirectory.resolve(releaseApkFileName)
        val searchedDirectories = listOf(outputDirectory, androidStudioReleaseApkDirectory)
        val sourceApk = searchedDirectories.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown() }
            .filter {
                it.isFile &&
                    it.extension.equals("apk", ignoreCase = true) &&
                    it.name != releaseApkFileName
            }
            .maxWithOrNull(
                compareBy<File> { it.lastModified() }
                    .thenBy { !it.name.contains("unsigned", ignoreCase = true) },
            )
            ?: throw GradleException(
                "Сборка release не создала APK. Проверены каталоги: ${searchedDirectories.joinToString()}",
            )
        outputDirectory.mkdirs()
        sourceApk.copyTo(outputApk, overwrite = true)
        logger.lifecycle("Release APK: ${outputApk.absolutePath}")
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyReleaseApk)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.10.1")

    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation("junit:junit:4.13.2")
}
