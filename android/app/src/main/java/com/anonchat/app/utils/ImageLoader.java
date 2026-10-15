package com.anonchat.app.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * High-performance lightweight asynchronous Image Loader with LRU Memory Caching.
 * Provides instant on-the-spot previews for sent and received images inside chat bubbles.
 */
public class ImageLoader {
    private static ImageLoader instance;
    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final OkHttpClient httpClient;

    private ImageLoader() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8; // Use 1/8th of available memory for cache
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        executorService = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());
        httpClient = new OkHttpClient.Builder().build();
    }

    public static synchronized ImageLoader getInstance() {
        if (instance == null) {
            instance = new ImageLoader();
        }
        return instance;
    }

    /**
     * Store a pre-loaded or locally captured bitmap into memory cache
     */
    public void cacheBitmap(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) {
            memoryCache.put(key, bitmap);
        }
    }

    /**
     * Retrieve a cached bitmap directly if available
     */
    public Bitmap getCachedBitmap(String key) {
        if (key == null) return null;
        return memoryCache.get(key);
    }

    /**
     * Load an image thumbnail asynchronously into an ImageView with instant memory lookup
     */
    public void loadThumbnail(final ImageView imageView, final String rawUrl, final String baseUrl, final int placeholderResId) {
        if (imageView == null) return;
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            imageView.setImageResource(placeholderResId);
            return;
        }

        // Tag the ImageView with the URL to prevent recycling / race conditions
        imageView.setTag(rawUrl);

        // 1. Fast cache check
        Bitmap cached = memoryCache.get(rawUrl);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return;
        }

        // Set placeholder while loading
        imageView.setImageResource(placeholderResId);

        // 2. Fetch asynchronously
        executorService.submit(() -> {
            try {
                String fullUrl;
                if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    fullUrl = rawUrl;
                } else {
                    String base = baseUrl != null ? baseUrl : "";
                    if (base.endsWith("/") && rawUrl.startsWith("/")) {
                        fullUrl = base + rawUrl.substring(1);
                    } else if (!base.endsWith("/") && !rawUrl.startsWith("/")) {
                        fullUrl = base + "/" + rawUrl;
                    } else {
                        fullUrl = base + rawUrl;
                    }
                }

                Request request = new Request.Builder().url(fullUrl).build();
                Response response = httpClient.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    byte[] bytes = response.body().bytes();
                    Bitmap bitmap = decodeSampledBitmap(bytes, 400, 400);

                    if (bitmap != null) {
                        memoryCache.put(rawUrl, bitmap);

                        mainHandler.post(() -> {
                            Object tag = imageView.getTag();
                            if (rawUrl.equals(tag)) {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
                // Network error or cancelled request, placeholder remains visible
            }
        });
    }

    /**
     * Decodes a downsampled bitmap to prevent memory overhead and smooth rendering
     */
    public static Bitmap decodeSampledBitmap(byte[] data, int reqWidth, int reqHeight) {
        if (data == null || data.length == 0) return null;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565; // High performance, 50% memory saving

        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
