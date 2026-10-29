plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "com.example.adssdk"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Google AdMob (api = the app can also see Google classes such as AdSize)
    api("com.google.android.gms:play-services-ads:23.3.0")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.URMIIV"
                artifactId = "adssdk"
                version = "1.0.0"
            }
        }
    }
}