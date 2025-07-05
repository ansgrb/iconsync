plugins {
	`kotlin-dsl`
	`maven-publish`
	signing
	kotlin("jvm") version "2.1.21"
}

group = "com.github.ansgrb"
version = "1.0-SNAPSHOT"

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
			id = "com.github.ansgrb.iconsync"
			implementationClass = "com.github.ansgrb.iconsync.IconSyncPlugin"
		}
	}
}

publishing {
	repositories {
		// This tells Gradle to publish to the local Maven repository
		// typically located at ~/.m2/repository
		mavenLocal()
	}
}