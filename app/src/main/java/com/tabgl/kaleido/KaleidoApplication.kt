package com.tabgl.kaleido

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.video.VideoFrameDecoder
import com.tabgl.kaleido.data.CrashLog
import coil.memory.MemoryCache

/**
 * Process-wide image loader. A large memory cache keeps scrolled-past thumbnails instantly available,
 * RGB_565 halves thumbnail memory, and a disk cache avoids re-decoding photos on every launch.
 */
class KaleidoApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            CrashLog.write(this, thread.name, error)
            previous?.uncaughtException(thread, error)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.3).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .components {
            add(SvgDecoder.Factory())
            add(VideoFrameDecoder.Factory())
        }
        .allowRgb565(true)
        .crossfade(false)
        .build()
}
