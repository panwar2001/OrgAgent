// Repositories used to resolve the build's own plugins. Everything this build needs is a released
// artifact on Maven Central or the Gradle Plugin Portal, so no snapshot repository is declared.
pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

rootProject.name = "backend"
