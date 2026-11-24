plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.sordina"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.sordina"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "0.3.1"
    }

    // La chiave di caricamento Play non sta mai su disco: la ricava signing/release.sh da una password e la passa qui
    val uploadKey = System.getenv("SORDINA_KEYSTORE")?.let { file(it) }
    signingConfigs {
        if (uploadKey != null) create("upload") {
            storeFile = uploadKey
            storePassword = System.getenv("SORDINA_STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("SORDINA_STORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            if (uploadKey != null) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            applicationIdSuffix = ".debug"
        }
        // Come quella di Play (R8, ottimizzata) ma con la chiave di debug e al posto dell'app di debug: per sentire la fluidità vera
        create("fast") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    // Date, indirizzi e codici di spedizione nei messaggi: un modello piccolo, sul telefono (niente testo esce)
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta5")
    // Lettore QR di Play Services: niente permesso fotocamera, si apre solo quando lo tocchi
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // Sordina sullo schermo dell'auto (Android Auto): solo i modelli di Google, niente disegno libero
    // Il canale di Home Assistant per modificare e cancellare gli impegni (WebSocket)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.car.app:app:1.8.0-rc01")
    implementation("androidx.car.app:app-projected:1.8.0-rc01")
    testImplementation("junit:junit:4.13.2")
    // org.json vero nei test (quello di Android lì è solo uno stub): per provare la copia delle impostazioni
    testImplementation("org.json:json:20240303")
}
