plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.faaa97.obdusbtotcpbridge"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.faaa97.obdusbtotcpbridge"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // A debug build with a simulated ELM327 instead of USB, to try the app on an emulator or a
        // PC without a head unit. Its code is only in app/src/mock/. Not built by any workflow (D-014).
        create("mock") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".mock" // Installs next to the real app
            versionNameSuffix = "-mock"
            matchingFallbacks += listOf("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    // AGP only creates unit tests for debug by default; the mock code's tests (app/src/testMock)
    // run with `testMockUnitTest`.
    beforeVariants(selector().withBuildType("mock")) { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

base {
    val commitId = "git rev-parse --short=8 HEAD".runCommand(workingDir = rootDir)
    archivesName = "OBDUSBtoTCPbridge-$commitId"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.usb.serial.for1.android)
}

fun String.runCommand(
    workingDir: File = File("."),
    timeoutAmount: Long = 60,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS
): String = ProcessBuilder(split("\\s(?=(?:[^'\"`]*(['\"`])[^'\"`]*\\1)*[^'\"`]*$)".toRegex()))
    .directory(workingDir)
    .redirectOutput(ProcessBuilder.Redirect.PIPE)
    .redirectError(ProcessBuilder.Redirect.PIPE)
    .start()
    .apply { waitFor(timeoutAmount, timeoutUnit) }
    .run {
        val error = errorStream.bufferedReader().readText().trim()
        if (error.isNotEmpty()) {
            throw Exception(error)
        }
        inputStream.bufferedReader().readText().trim()
    }
