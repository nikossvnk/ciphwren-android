plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP builds Kotlin itself; this only picks the Kotlin version it uses
    alias(libs.plugins.kotlin.android) apply false
}
