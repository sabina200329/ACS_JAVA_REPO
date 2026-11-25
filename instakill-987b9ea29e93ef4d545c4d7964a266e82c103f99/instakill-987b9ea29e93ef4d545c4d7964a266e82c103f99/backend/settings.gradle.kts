plugins {
    // descarcă automat JDK 21 dacă nu e instalat (ex. pe Windows); în WSL folosește openjdk-21 din apt
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "instakill"
