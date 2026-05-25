package com.example.core.ocr.preprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object OcrPreprocessor {

    /**
     * Converts a source Bitmap to a grayscale Bitmap.
     */
    fun toGrayscale(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Normalizes the exposure/brightness of an image to a target mean.
     */
    fun normalizeBrightness(src: Bitmap, targetMean: Float = 128f): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        var sum = 0L
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            sum += r
        }
        val mean = if (pixels.isNotEmpty()) sum.toFloat() / pixels.size else 128f
        val factor = if (mean > 0f) targetMean / mean else 1f

        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            
            val nr = (r * factor).toInt().coerceIn(0, 255)
            val ng = (g * factor).toInt().coerceIn(0, 255)
            val nb = (b * factor).toInt().coerceIn(0, 255)
            
            outPixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Adjusts the contrast of a source Bitmap.
     */
    fun adjustContrast(src: Bitmap, contrast: Float = 1.4f): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val array = floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(array))
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Denoises a source Bitmap using a 3x3 mean box filter.
     */
    fun applyDenoise(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val outPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                
                for (ky in -1..1) {
                    val ny = y + ky
                    if (ny in 0 until height) {
                        for (kx in -1..1) {
                            val nx = x + kx
                            if (nx in 0 until width) {
                                val pixel = pixels[ny * width + nx]
                                rSum += (pixel shr 16) and 0xFF
                                gSum += (pixel shr 8) and 0xFF
                                bSum += pixel and 0xFF
                                count++
                            }
                        }
                    }
                }
                
                val nr = rSum / count
                val ng = gSum / count
                val nb = bSum / count
                outPixels[y * width + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Sharpens a source Bitmap using a 3x3 Laplacian convolution kernel.
     */
    fun applySharpen(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val outPixels = IntArray(width * height)

        // Sharpen Kernel:
        //  0  -1   0
        // -1   5  -1
        //  0  -1   0
        val kernel = intArrayOf(
             0, -1,  0,
            -1,  5, -1,
             0, -1,  0
        )
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                    outPixels[y * width + x] = pixels[y * width + x]
                    continue
                }
                
                var rSum = 0
                var gSum = 0
                var bSum = 0
                
                for (ky in -1..1) {
                    val ny = y + ky
                    for (kx in -1..1) {
                        val nx = x + kx
                        val pixel = pixels[ny * width + nx]
                        val weight = kernel[(ky + 1) * 3 + (kx + 1)]
                        rSum += ((pixel shr 16) and 0xFF) * weight
                        gSum += ((pixel shr 8) and 0xFF) * weight
                        bSum += (pixel and 0xFF) * weight
                    }
                }
                
                val nr = rSum.coerceIn(0, 255)
                val ng = gSum.coerceIn(0, 255)
                val nb = bSum.coerceIn(0, 255)
                outPixels[y * width + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Applies Contrast Limited Adaptive Histogram Equalization (CLAHE).
     */
    fun applyClahe(src: Bitmap, numTilesX: Int = 8, numTilesY: Int = 8, clipLimit: Float = 4.0f): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val tileW = width / numTilesX
        val tileH = height / numTilesY
        if (tileW < 2 || tileH < 2) return src // Tiles are too small, skip

        val histograms = Array(numTilesY) { Array(numTilesX) { IntArray(256) } }
        val cdfs = Array(numTilesY) { Array(numTilesX) { FloatArray(256) } }

        // 1. Compute Histograms and clip them
        for (ty in 0 until numTilesY) {
            for (tx in 0 until numTilesX) {
                val startX = tx * tileW
                val startY = ty * tileH
                val hist = histograms[ty][tx]
                
                // Collect histogram
                var totalPixels = 0
                for (y in 0 until tileH) {
                    val py = startY + y
                    if (py >= height) continue
                    for (x in 0 until tileW) {
                        val px = startX + x
                        if (px >= width) continue
                        val pixel = pixels[py * width + px]
                        val gray = (pixel shr 16) and 0xFF
                        hist[gray]++
                        totalPixels++
                    }
                }
                
                // Clip histogram
                val limit = (clipLimit * totalPixels / 256f).coerceAtLeast(1f).toInt()
                var clipped = 0
                for (g in 0..255) {
                    if (hist[g] > limit) {
                        clipped += hist[g] - limit
                        hist[g] = limit
                    }
                }
                
                // Redistribute clipped pixels
                val redistAmount = clipped / 256
                val remainder = clipped % 256
                for (g in 0..255) {
                    hist[g] += redistAmount
                }
                // Distribute remainder
                for (i in 0 until remainder) {
                    val step = 256 / remainder
                    hist[(i * step) % 256]++
                }

                // Compute CDF
                var sum = 0
                val cdf = cdfs[ty][tx]
                for (g in 0..255) {
                    sum += hist[g]
                    cdf[g] = sum.toFloat() / totalPixels
                }
            }
        }

        // 2. Interpolate CDFs for each pixel
        val outPixels = IntArray(width * height)
        for (y in 0 until height) {
            val fy = y.toFloat() / tileH - 0.5f
            val ty1 = fy.toInt().coerceIn(0, numTilesY - 1)
            val ty2 = (ty1 + 1).coerceIn(0, numTilesY - 1)
            val dy = fy - ty1
            
            for (x in 0 until width) {
                val fx = x.toFloat() / tileW - 0.5f
                val tx1 = fx.toInt().coerceIn(0, numTilesX - 1)
                val tx2 = (tx1 + 1).coerceIn(0, numTilesX - 1)
                val dx = fx - tx1

                val pixel = pixels[y * width + x]
                val gray = (pixel shr 16) and 0xFF

                // Bilinear interpolation of CDF values
                val cdf11 = cdfs[ty1][tx1][gray]
                val cdf12 = cdfs[ty1][tx2][gray]
                val cdf21 = cdfs[ty2][tx1][gray]
                val cdf22 = cdfs[ty2][tx2][gray]

                val interpCdf = (1 - dy) * ((1 - dx) * cdf11 + dx * cdf12) +
                        dy * ((1 - dx) * cdf21 + dx * cdf22)

                val newGray = (interpCdf * 255).toInt().coerceIn(0, 255)
                outPixels[y * width + x] = (0xFF shl 24) or (newGray shl 16) or (newGray shl 8) or newGray
            }
        }

        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Applies adaptive thresholding using the Bradley-Roth integral image algorithm.
     */
    fun applyAdaptiveThreshold(src: Bitmap, windowSize: Int = 0, thresholdPercentage: Float = 0.15f): Bitmap {
        val width = src.width
        val height = src.height
        
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            gray[i] = (pixels[i] shr 16) and 0xFF
        }

        // 1. Compute integral image
        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var sum = 0L
            for (x in 0 until width) {
                sum += gray[y * width + x]
                if (y == 0) {
                    integral[y * width + x] = sum
                } else {
                    integral[y * width + x] = integral[(y - 1) * width + x] + sum
                }
            }
        }

        // 2. Perform binarization
        val outPixels = IntArray(width * height)
        val s = if (windowSize > 0) windowSize else width / 8
        val halfS = s / 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                val x1 = (x - halfS).coerceAtLeast(0)
                val x2 = (x + halfS).coerceAtMost(width - 1)
                val y1 = (y - halfS).coerceAtLeast(0)
                val y2 = (y + halfS).coerceAtMost(height - 1)
                
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                
                var sum = integral[y2 * width + x2]
                if (x1 > 0) sum -= integral[y2 * width + (x1 - 1)]
                if (y1 > 0) sum -= integral[(y1 - 1) * width + x2]
                if (x1 > 0 && y1 > 0) sum += integral[(y1 - 1) * width + (x1 - 1)]

                val avg = sum.toDouble() / count
                val thresholdVal = avg * (1.0 - thresholdPercentage)
                
                val resVal = if (gray[y * width + x] < thresholdVal) 0 else 255
                outPixels[y * width + x] = (0xFF shl 24) or (resVal shl 16) or (resVal shl 8) or resVal
            }
        }

        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Upscales a bitmap to target dimensions using bilinear interpolation.
     */
    fun upscaleBilinear(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
    }

    /**
     * Computes Sobel gradient magnitude for the image.
     */
    fun applySobel(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val luma = FloatArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        
        val outPixels = IntArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                val gx = -luma[idx - width - 1] + luma[idx - width + 1] -
                         2f * luma[idx - 1] + 2f * luma[idx + 1] -
                         luma[idx + width - 1] + luma[idx + width + 1]
                         
                val gy = -luma[idx - width - 1] - 2f * luma[idx - width] - luma[idx - width + 1] +
                         luma[idx + width - 1] + 2f * luma[idx + width] + luma[idx + width + 1]
                         
                val gVal = kotlin.math.sqrt(gx * gx + gy * gy).toInt().coerceIn(0, 255)
                outPixels[idx] = (0xFF shl 24) or (gVal shl 16) or (gVal shl 8) or gVal
            }
        }
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Enhances edges by blending the original image with its Sobel gradients.
     */
    fun applyEdgeEnhancement(src: Bitmap, amount: Float = 0.5f): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val luma = FloatArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        
        val outPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                    outPixels[idx] = pixels[idx]
                    continue
                }
                
                val gx = -luma[idx - width - 1] + luma[idx - width + 1] -
                         2f * luma[idx - 1] + 2f * luma[idx + 1] -
                         luma[idx + width - 1] + luma[idx + width + 1]
                         
                val gy = -luma[idx - width - 1] - 2f * luma[idx - width] - luma[idx - width + 1] +
                         luma[idx + width - 1] + 2f * luma[idx + width] + luma[idx + width + 1]
                         
                val gVal = kotlin.math.sqrt(gx * gx + gy * gy) * amount
                
                val p = pixels[idx]
                val r = (((p shr 16) and 0xFF) + gVal).toInt().coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) + gVal).toInt().coerceIn(0, 255)
                val b = ((p and 0xFF) + gVal).toInt().coerceIn(0, 255)
                
                outPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }
}
