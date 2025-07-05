package com.github.ansgrb.iconsync

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject
import groovy.json.JsonOutput

abstract class IconsyncPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.tasks.register("iconsync", ConvertAndCopyIconsTask::class.java) {
			group = "iOS Icon Sync"
			description = "Converts and syncs Android mipmap icons to the iOS asset catalog."
			androidResDir.set(project.file("composeApp/src/androidMain/res"))
			iosAssetsDir.set(project.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"))
		}
	}
}

abstract class ConvertAndCopyIconsTask @Inject constructor() : DefaultTask() {

	@get:InputDirectory
	abstract val androidResDir: Property<File>

	@get:OutputDirectory
	abstract val iosAssetsDir: Property<File>

	// A map of the required iOS icon filenames and their corresponding pixel dimensions.
	private val iosIcons = mapOf(
		"Icon-App-20x20@2x.png" to 40,
		"Icon-App-20x20@3x.png" to 60,
		"Icon-App-29x29@2x.png" to 58,
		"Icon-App-29x29@3x.png" to 87,
		"Icon-App-40x40@2x.png" to 80,
		"Icon-App-40x40@3x.png" to 120,
		"Icon-App-60x60@2x.png" to 120,
		"Icon-App-60x60@3x.png" to 180
	)

	@TaskAction
	fun execute() {
		val sourceDir = androidResDir.get()
		val destDir = iosAssetsDir.get()

		if (!destDir.exists()) {
			project.logger.warn("iOS assets directory not found at ${destDir.path}. Creating it.")
			destDir.mkdirs()
		}

		project.logger.lifecycle("Starting iOS icon sync. NOTE: This task requires 'dwebp' to be installed and in your PATH.")
		project.logger.lifecycle("You can install it via Homebrew: 'brew install webp'")

		val sourceIcon = findSourceIcon(sourceDir)
		if (sourceIcon == null) {
			project.logger.error("Could not find a suitable source icon in your androidMain/res/mipmap-* directories (e.g., ic_launcher.webp or ic_launcher_foreground.webp).")
			return
		}

		project.logger.lifecycle("Using ${sourceIcon.absolutePath} as source for generating iOS icons.")

		// Clean the destination directory of any previous PNG icons before generating new ones.
		destDir.listFiles { _, name -> name.endsWith(".png") }?.forEach { it.delete() }

		iosIcons.forEach { (name, size) ->
			val outputFile = File(destDir, name)
			// Execute the 'dwebp' command to resize the source WebP and output as a PNG.
			val result = project.exec {
				commandLine(
					"dwebp",
					"-resize",
					size.toString(),
					size.toString(),
					sourceIcon.absolutePath,
					"-o",
					outputFile.absolutePath
				)
				isIgnoreExitValue = true // Don't fail the build; just log the error.
			}
			if (result.exitValue != 0) {
				project.logger.error("Failed to generate ${outputFile.name}. Is 'dwebp' installed and in your PATH?")
			} else {
				project.logger.lifecycle("Generated ${outputFile.name}")
			}
		}

		// Handle the 1024x1024 App Store icon separately.
		val playstoreIcon = File(sourceDir.parentFile, "ic_launcher-playstore.png")
		if (playstoreIcon.exists()) {
			val outputFile = File(destDir, "Icon-App-1024x1024@1x.png")
			playstoreIcon.copyTo(outputFile, overwrite = true)
			project.logger.lifecycle("Copied Play Store icon to ${outputFile.name}")
		} else {
			project.logger.warn("Play Store icon (ic_launcher-playstore.png) not found in 'androidMain'. You may need to add the App Store icon manually.")
		}

		generateContentsJson(destDir)

		project.logger.lifecycle("Successfully synced iOS icons.")
	}

	/**
	 * Finds the best available WebP source icon from the Android mipmap directories,
	 * preferring the highest density.
	 */
	private fun findSourceIcon(sourceDir: File): File? {
		val preferredOrder = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")
		for (density in preferredOrder) {
			// Prefer the standard launcher icon, but fall back to the foreground icon.
			val iconFile = File(sourceDir, "mipmap-$density/ic_launcher.webp")
			if (iconFile.exists()) return iconFile
			val foregroundIconFile = File(sourceDir, "mipmap-$density/ic_launcher_foreground.webp")
			if (foregroundIconFile.exists()) return foregroundIconFile
		}
		return null
	}

	/**
	 * Generates the Contents.json file required by Xcode to identify the app icons.
	 */
	private fun generateContentsJson(destDir: File) {
		val images = mutableListOf<Map<String, Any>>()

		images.add(
			mapOf(
				"size" to "20x20",
				"idiom" to "iphone",
				"filename" to "Icon-App-20x20@2x.png",
				"scale" to "2x"
			)
		)
		images.add(
			mapOf(
				"size" to "20x20",
				"idiom" to "iphone",
				"filename" to "Icon-App-20x20@3x.png",
				"scale" to "3x"
			)
		)
		images.add(
			mapOf(
				"size" to "29x29",
				"idiom" to "iphone",
				"filename" to "Icon-App-29x29@2x.png",
				"scale" to "2x"
			)
		)
		images.add(
			mapOf(
				"size" to "29x29",
				"idiom" to "iphone",
				"filename" to "Icon-App-29x29@3x.png",
				"scale" to "3x"
			)
		)
		images.add(
			mapOf(
				"size" to "40x40",
				"idiom" to "iphone",
				"filename" to "Icon-App-40x40@2x.png",
				"scale" to "2x"
			)
		)
		images.add(
			mapOf(
				"size" to "40x40",
				"idiom" to "iphone",
				"filename" to "Icon-App-40x40@3x.png",
				"scale" to "3x"
			)
		)
		images.add(
			mapOf(
				"size" to "60x60",
				"idiom" to "iphone",
				"filename" to "Icon-App-60x60@2x.png",
				"scale" to "2x"
			)
		)
		images.add(
			mapOf(
				"size" to "60x60",
				"idiom" to "iphone",
				"filename" to "Icon-App-60x60@3x.png",
				"scale" to "3x"
			)
		)

		// Only include the App Store icon in Contents.json if it exists.
		if (File(destDir, "Icon-App-1024x1024@1x.png").exists()) {
			images.add(
				mapOf(
					"size" to "1024x1024",
					"idiom" to "ios-marketing",
					"filename" to "Icon-App-1024x1024@1x.png",
					"scale" to "1x"
				)
			)
		}

		val contents = mapOf(
			"images" to images,
			"info" to mapOf("version" to 1, "author" to "xcode")
		)

		val contentsJsonFile = File(destDir, "Contents.json")
		contentsJsonFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(contents)))
		project.logger.lifecycle("Generated Contents.json for iOS icons.")
	}
}
