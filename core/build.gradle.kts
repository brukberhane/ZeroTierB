plugins {
    id("com.android.library")
}

android {
    namespace = "com.zerotier.sdk"
    compileSdk = 35
    ndkVersion = "25.1.8937393"

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("../externals/ZeroTierOne/java/src")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }
}
