import com.vanniktech.maven.publish.SonatypeHost

plugins {
	`kotlin-dsl`
	`maven-publish`
	signing
//	kotlin("jvm") version "2.1.21"
	id("com.vanniktech.maven.publish") version "0.33.0"
}

val pluginGroupId = "io.github.ansgrb"
val pluginArtifactId = "iconsync"
val pluginVersion = "1.0.1"

group = "io.github.ansgrb"

repositories {
	mavenCentral()
}

dependencies {
	implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
	implementation(localGroovy())
	testImplementation(kotlin("test"))
}

tasks.test {
	useJUnitPlatform()
}
kotlin {
	jvmToolchain(21)
}

gradlePlugin {
	plugins {
		create("iconsync") {
			id = "io.github.ansgrb.iconsync"
			implementationClass = "io.github.ansgrb.iconsync.IconSyncPlugin"
			displayName = "KMP iOS IconSync Plugin"
			description =
				"A Gradle plugin to automatically sync Android launcher icons to the iOS part of a Kotlin Multiplatform project."

		}
	}
}

mavenPublishing {
	coordinates(
		pluginGroupId,
		pluginArtifactId,
		pluginVersion
	)

	pom {
		name.set("KMP iOS IconSync Plugin")
		description.set("A Gradle plugin to automatically sync Android launcher icons to the iOS part of a Kotlin Multiplatform project.")
		inceptionYear.set("2025")
		url.set("https://github.com/ansgrb/iconsync")
		licenses {
			license {
				name.set("The Apache License, Version 2.0")
				url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
				distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
			}
		}
		developers {
			developer {
				id.set("ansgrb")
				name.set("Anas Ghareib")
				url.set("https://github.com/ansgrb/")
			}
		}
		scm {
			url.set("https://github.com/ansgrb/iconsync")
			connection.set("scm:git:git://github.com/ansgrb/iconsync.git")
			developerConnection.set("scm:git:ssh://git@github.com/ansgrb/iconsync.git")
		}
	}

	publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
	signAllPublications()
}