package com.example.gallerygrabber;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;

/** Stores read permission to a user-picked local wallpaper, never copies or uploads it. */
public final class WallpaperStore {
    private static final String PREFS = "appearance";
    private static final String KEY = "wallpaper_uri";
    public static final int PICK_IMAGE = 601;
    private WallpaperStore() { }

    public static Intent picker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    public static boolean accept(Activity activity, Intent result) {
        if (result == null || result.getData() == null) return false;
        Uri uri = result.getData();
        try {
            activity.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // Validate that decoding works before replacing an existing wallpaper.
            decode(activity, uri);
            String previous = activity.getSharedPreferences(PREFS, 0).getString(KEY, null);
            activity.getSharedPreferences(PREFS, 0).edit().putString(KEY, uri.toString()).apply();
            if (previous != null && !previous.equals(uri.toString())) {
                try {
                    activity.getContentResolver().releasePersistableUriPermission(Uri.parse(previous),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) { }
            }
            return true;
        } catch (Exception | OutOfMemoryError ex) {
            try {
                activity.getContentResolver().releasePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            Toast.makeText(activity, "Could not open that wallpaper: " + ex.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public static void clear(Activity activity) {
        String value = activity.getSharedPreferences(PREFS, 0).getString(KEY, null);
        if (value != null) {
            try {
                activity.getContentResolver().releasePersistableUriPermission(Uri.parse(value),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
        }
        activity.getSharedPreferences(PREFS, 0).edit().remove(KEY).apply();
    }

    public static void apply(Activity activity, ImageView image) {
        String stored = activity.getSharedPreferences(PREFS, 0).getString(KEY, null);
        image.setImageDrawable(null);
        if (stored != null) {
            try {
                image.setImageBitmap(decode(activity, Uri.parse(stored)));
                image.setBackgroundColor(Color.TRANSPARENT);
                return;
            } catch (Exception | OutOfMemoryError ex) {
                // Revoked permission, moved image, or unsupported encoding. Keep a safe default.
                activity.getSharedPreferences(PREFS, 0).edit().remove(KEY).apply();
            }
        }
        GradientDrawable fallback = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(10, 27, 52), Color.rgb(23, 77, 81), Color.rgb(11, 18, 33)});
        image.setBackground(fallback);
    }

    private static Bitmap decode(Activity activity, Uri uri) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(activity.getContentResolver(), uri);
        // Downsample huge photos before allocating; keeps 8K wallpapers from exhausting RAM.
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            if (width < 1 || height < 1) throw new IllegalArgumentException("Empty image");
            double ratio = Math.min(1.0, Math.min(1440.0 / width, 2560.0 / height));
            decoder.setTargetSize(Math.max(1, (int) (width * ratio)),
                    Math.max(1, (int) (height * ratio)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }
}
