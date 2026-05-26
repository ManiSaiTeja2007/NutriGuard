package com.example.core.export

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ExportFileWriter(private val context: Context) {

    fun getExportDir(executionId: String): File? {
        val root = context.getExternalFilesDir("exports") ?: return null
        val dir = File(root, executionId)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun writeTextFile(dir: File, subDirName: String, fileName: String, content: String): File? {
        return try {
            val subDir = File(dir, subDirName)
            if (!subDir.exists()) subDir.mkdirs()
            val file = File(subDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray())
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun copyFile(dir: File, subDirName: String, sourcePath: String, destFileName: String): File? {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) return null
            val subDir = File(dir, subDirName)
            if (!subDir.exists()) subDir.mkdirs()
            val destFile = File(subDir, destFileName)
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun writeBitmapFile(dir: File, subDirName: String, fileName: String, bitmap: android.graphics.Bitmap): File? {
        return try {
            val subDir = File(dir, subDirName)
            if (!subDir.exists()) subDir.mkdirs()
            val file = File(subDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun calculateSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
