package com.github.ansgrb.iconsync

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import groovy.json.JsonOutput

// Data class to define each required iOS icon specification
private data class IosIconSpec(
	val size: String,
	val idiom: String,
	val scale: String,
	val filename: String,
	val pixelSize: Int
) {
	fun toMap(): Map<String, Any> = mapOf(
		"size" to this.size,
		"idiom" to this.idiom,
		"filename" to this.filename,
		"scale" to this.scale
	)
}

// A single source of truth for all required iOS icons
private val IOS_ICON_SPECS = listOf(
	IosIconSpec("20x20", "iphone", "2x", "Icon-App-20x20@2x.png", 40),
	IosIconSpec("20x20", "iphone", "3x", "Icon-App-20x20@3x.png", 60),
	IosIconSpec("29x29", "iphone", "2x", "Icon-App-29x29@2x.png", 58),
	IosIconSpec("29x29", "iphone", "3x", "Icon-App-29x29@3x.png", 87),
	IosIconSpec("40x40", "iphone", "2x", "Icon-App-40x40@2x.png", 80),
	IosIconSpec("40x40", "iphone", "3x", "Icon-App-40x40@3x.png", 120),
	IosIconSpec("60x60", "iphone", "2x", "Icon-App-60x60@2x.png", 120),
	IosIconSpec("60x60", "iphone", "3x", "Icon-App-60x60@3x.png", 180),
	IosIconSpec("1024x1024", "ios-marketing", "1x", "Icon-App-1024x1024@1x.png", 1024)
)

/**
 * A Gradle extension to allow users to configure the plugin from their build script.
 */
interface IconSyncExtension {
	val androidResDirectory: DirectoryProperty
	val iosIconSetDirectory: DirectoryProperty
}

abstract class IconSyncPlugin : Plugin<Project> {
	override fun apply(project: Project) {
		// Create a configuration extension for users
		val extension = project.extensions.create("iconSyncConfig", IconSyncExtension::class.java)

		// Set sensible defaults for a typical KMP project
		extension.androidResDirectory.convention(project.layout.projectDirectory.dir("composeApp/src/androidMain/res"))
		extension.iosIconSetDirectory.convention(project.layout.projectDirectory.dir("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"))

		// Register the task using the modern API
		project.tasks.register("iconSync") {
			group = "build"
			description = "Converts and syncs Android mipmap icons to the iOS asset catalog"

			doLast {
				val implTask = project.tasks.register("_iconSyncImpl", IconSyncTask::class.java).get()
				implTask.androidResDir.set(extension.androidResDirectory)
				implTask.iosAssetsDir.set(extension.iosIconSetDirectory)
				implTask.execute()
			}
		}
	}
}

@CacheableTask
abstract class IconSyncTask : DefaultTask() {

	private val colorReset = "\u001B[0m"
	private val colorGreen = "\u001B[32m"
	private val colorRed = "\u001B[31m"
	private val colorYellow = "\u001B[33m"
	private val colorBlue = "\u001B[34m"

	@get:InputDirectory
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val androidResDir: DirectoryProperty

	@get:OutputDirectory
	abstract val iosAssetsDir: DirectoryProperty

	@TaskAction
	fun execute() {
		printHeader()
		val sourceDir = androidResDir.get().asFile
		val destDir = iosAssetsDir.get().asFile

		val sourceIcon = findBestSourceIcon(sourceDir)
		if (sourceIcon == null) {
			logger.error("$colorRed ❌ ERROR: Could not find 'ic_launcher.webp' or 'ic_launcher_foreground.webp' in your mipmap-* directories.$colorReset")
			return
		}

		logger.lifecycle("▶️  Using source icon: ${sourceIcon.path}")
		logger.lifecycle("--------------------------------------------------")

		val sourceImage = readWebPImage(sourceIcon)
		if (sourceImage == null) {
			logger.error("$colorRed ❌ ERROR: Failed to read source icon. It may be corrupt or an unsupported format.$colorReset")
			return
		}

		if (!destDir.exists()) {
			logger.lifecycle("   - Destination directory not found. Creating it at: ${destDir.path}")
			destDir.mkdirs()
		}

		var generatedCount = 0
		var failedCount = 0

		// Filter specs for which we can generate an icon
		val specsToGenerate = IOS_ICON_SPECS.filter { it.pixelSize <= sourceImage.width }
		val skippedSpecs = IOS_ICON_SPECS.filter { it.pixelSize > sourceImage.width }

		specsToGenerate.forEach { spec ->
			try {
				val resizedImage = resizeImage(sourceImage, spec.pixelSize, spec.pixelSize)
				val outputFile = File(destDir, spec.filename)
				ImageIO.write(resizedImage, "png", outputFile)
				logger.lifecycle("$colorGreen   ✓ Generated ${outputFile.name} (${spec.pixelSize} x ${spec.pixelSize} px)$colorReset")
				generatedCount++
			} catch (e: Exception) {
				logger.error("$colorRed ❗ Failed to generate ${spec.filename}: ${e.message}$colorReset")
				failedCount++
			}
		}

		skippedSpecs.forEach { spec ->
			logger.warn("$colorYellow   ⚠️ Skipped ${spec.filename}: Source icon (${sourceImage.width}px) is smaller than required size (${spec.pixelSize}px).$colorReset")
		}

		generateContentsJson(destDir, specsToGenerate)
		printFooter(generatedCount, failedCount)
	}

	private fun readWebPImage(file: File): BufferedImage? {
		return ImageIO.createImageInputStream(file).use { stream ->
			val readers = ImageIO.getImageReaders(stream)
			if (readers.hasNext()) {
				val reader = readers.next()
				try {
					reader.input = stream
					return reader.read(0)
				} finally {
					reader.dispose()
				}
			}
			null
		}
	}

	private fun findBestSourceIcon(resDir: File): File? {
		val preferredDensities = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")
		val iconNames = listOf("ic_launcher.webp", "ic_launcher_foreground.webp")

		return preferredDensities.asSequence()
			.flatMap { density -> iconNames.map { iconName -> File(resDir, "mipmap-$density/$iconName") } }
			.firstOrNull { it.exists() }
	}

	private fun resizeImage(source: BufferedImage, width: Int, height: Int): BufferedImage {
		val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		val g = resized.createGraphics()
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
			g.drawImage(source, 0, 0, width, height, null)
		} finally {
			g.dispose()
		}
		return resized
	}

	private fun generateContentsJson(destDir: File, generatedSpecs: List<IosIconSpec>) {
		val images = generatedSpecs.map { it.toMap() }
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
            ╔════════════════════════════════════════╗
            ║           iOS Icon Generator           ║
            ╚════════════════════════════════════════╝
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


