package com.github.ansgrb.iconsync

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject
import groovy.json.JsonOutput
import java.io.IOException

abstract class IconSyncPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.tasks.register("iconsync", IconSyncTask::class.java) {
			group = "iOS Icon Sync"
			description = "Converts and syncs Android mipmap icons to the iOS asset catalog."
			androidResDir.set(project.file("composeApp/src/androidMain/res"))
			iosAssetsDir.set(project.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"))
		}
	}
}

abstract class IconSyncTask @Inject constructor(
	private val execOperations: ExecOperations
) : DefaultTask() {

	private val colorReset = "\u001B[0m"
	private val colorGreen = "\u001B[32m"
	private val colorRed = "\u001B[31m"
	private val colorYellow = "\u001B[33m"
	private val colorBlue = "\u001B[34m"

	@get:InputDirectory
	abstract val androidResDir: Property<File>

	@get:OutputDirectory
	abstract val iosAssetsDir: Property<File>

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
		printHeader()

		if (!isDwebpInstalled()) {
			logger.error("$colorRed ❌ ERROR: 'dwebp' command not found. Please install webp tools.$colorReset")
			logger.error("$colorYellow You can install it on macOS via Homebrew: 'brew install webp'$colorReset")
			return
		}

		val sourceDir = androidResDir.get()
		val destDir = iosAssetsDir.get()

		if (!destDir.exists()) {
			logger.lifecycle("   - Destination directory not found. Creating it at: ${destDir.path}")
			destDir.mkdirs()
		}

		val sourceIcon = findSourceIcon(sourceDir)
		if (sourceIcon == null) {
			logger.error("$colorRed ❌ ERROR: Could not find a suitable source icon in your androidMain/res/mipmap-* directories.$colorReset")
			logger.error("$colorYellow   - Please ensure 'ic_launcher.webp' or 'ic_launcher_foreground.webp' exists.$colorReset")
			return
		}

		logger.lifecycle("▶️  Found source icon: ${sourceIcon.path}")
		logger.lifecycle("--------------------------------------------------")

		var generatedCount = 0
		var failedCount = 0

		iosIcons.forEach { (name, size) ->
			val outputFile = File(destDir, name)
			try {
				val result = execOperations.exec {
					commandLine(
						"dwebp",
						"-resize",
						size.toString(),
						size.toString(),
						sourceIcon.absolutePath,
						"-o",
						outputFile.absolutePath
					)
					isIgnoreExitValue = true
				}
				if (result.exitValue != 0) {
					logger.error("$colorRed ❗ Failed to generate ${outputFile.name}.$colorReset")
					failedCount++
				} else {
					logger.lifecycle("$colorGreen   ✓ Generated ${outputFile.name} ($size x $size px)$colorReset")
					generatedCount++
				}
			} catch (e: IOException) {
				logger.error("$colorRed ❗ IOException while generating ${outputFile.name}: ${e.message}$colorReset")
				failedCount++
			}
		}

		copyPlayStoreIcon(sourceDir.parentFile, destDir)
		generateContentsJson(destDir)

		printFooter(generatedCount, failedCount)
	}

	private fun isDwebpInstalled(): Boolean {
		return try {
			val result = execOperations.exec {
				commandLine("which", "dwebp")
				isIgnoreExitValue = true
			}
			result.exitValue == 0
		} catch (e: IOException) {
			false
		}
	}

	private fun copyPlayStoreIcon(androidMainDir: File, destDir: File) {
		val playstoreIcon = File(androidMainDir, "ic_launcher-playstore.png")
		if (playstoreIcon.exists()) {
			val outputFile = File(destDir, "Icon-App-1024x1024@1x.png")
			playstoreIcon.copyTo(outputFile, overwrite = true)
			logger.lifecycle("$colorGreen   ✓ Copied App Store icon (1024x1024px).$colorReset")
		} else {
			logger.warn("$colorYellow   ⚠️ Play Store icon not found at 'src/androidMain/ic_launcher-playstore.png'. App Store icon will be missing.$colorReset")
		}
	}

	private fun findSourceIcon(sourceDir: File): File? {
		val preferredOrder = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")
		for (density in preferredOrder) {
			val iconFile = File(sourceDir, "mipmap-$density/ic_launcher.webp")
			if (iconFile.exists()) return iconFile
			val foregroundIconFile = File(sourceDir, "mipmap-$density/ic_launcher_foreground.webp")
			if (foregroundIconFile.exists()) return foregroundIconFile
		}
		return null
	}

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
		logger.lifecycle("$colorGreen   ✓ Generated Contents.json file.$colorReset")
	}

	private fun printHeader() {
		logger.lifecycle(
			"""
$colorBlue
  ___           ___           ___     
 (o o)         (o o)         (o o)    
(  V  ) I C O N S Y N C (  V  )
--m-m-----------m-m-----------m-m--
$colorReset
        """.trimIndent()
		)
	}

	private fun printFooter(generated: Int, failed: Int) {
		logger.lifecycle("--------------------------------------------------")
		if (failed > 0) {
			logger.lifecycle("$colorRed ❌ Sync finished with $failed errors.$colorReset")
		} else {
			logger.lifecycle("$colorGreen ✅ Sync complete! Successfully generated $generated icons.$colorReset")
		}
		logger.lifecycle("--------------------------------------------------")
	}
}


