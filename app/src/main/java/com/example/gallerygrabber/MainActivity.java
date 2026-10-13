package com.example.gallerygrabber;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A native, single-activity album downloader. No Python engine is bundled.
 * WebView loads sites and uses the user's own session cookies for image requests.
 * Restricted albums stay restricted; the app does not bypass access controls.
 */
public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> queue = new ArrayList<>();
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private String browserUserAgent;
    private volatile boolean running = false;
    private EditText input;
    private TextView status, log;
    private ProgressBar progress;
    private Button startButton;
    private ImageView wallpaperImage;
    private TextView sessionStatus;
    private WebView scraper;
    private static final int MAX_V2PH_PAGES = 300;
    private static final Pattern EXT = Pattern.compile("\\.(jpe?g|png|webp|gif|bmp|avif)(?:/|$)", Pattern.CASE_INSENSITIVE);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenChrome.edgeToEdge(this);
        setContentView(R.layout.activity_main);
        ScreenChrome.padControlsForSystemBars(findViewById(R.id.main_content));
        wallpaperImage = findViewById(R.id.wallpaper_image);
        WallpaperStore.apply(this, wallpaperImage);
        sessionStatus = findViewById(R.id.session_status);
        findViewById(R.id.wallpaper_btn).setOnClickListener(v -> wallpaperOptions());
        input = findViewById(R.id.url_input);
        status = findViewById(R.id.status);
        log = findViewById(R.id.log);
        progress = findViewById(R.id.progress);
        startButton = findViewById(R.id.start_btn);
        findViewById(R.id.queue_btn).setOnClickListener(v -> addToQueue());
        findViewById(R.id.login_btn).setOnClickListener(v -> showSiteLoginChoice());
        startButton.setOnClickListener(v -> begin());
        findViewById(R.id.pause_btn).setOnClickListener(v -> {
            if (running) {
                boolean nowPaused = !paused.get();
                paused.set(nowPaused);
                ((Button) findViewById(R.id.pause_btn)).setText(nowPaused ? "Resume" : "Pause");
                append(nowPaused ? "Paused (current transfer may finish)." : "Resumed.");
            }
        });
        findViewById(R.id.stop_btn).setOnClickListener(v -> {
            stop.set(true);
            append("Stop requested; current image may finish first.");
        });
        // The offscreen browser is attached, allowing JavaScript and DOM execution.
        scraper = new WebView(this);
        scraper.getSettings().setJavaScriptEnabled(true);
        scraper.getSettings().setDomStorageEnabled(true);
        scraper.getSettings().setLoadsImagesAutomatically(true);
        browserUserAgent = scraper.getSettings().getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(scraper, true);
        refreshSiteCookies();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(360, 640);
        addContentView(scraper, params);
        scraper.setVisibility(View.INVISIBLE);
    }

    private void addToQueue() {
        int added = 0;
        for (String raw : input.getText().toString().split("[\\r\\n]+")) {
            String url = raw.trim();
            if (url.isEmpty()) continue;
            if (SiteRules.site(url) == null) {
                append("Unsupported album URL (HTTPS V2PH or Xasiat album only): " + url);
                continue;
            }
            if (!queue.contains(url)) { queue.add(url); added++; }
        }
        input.setText("");
        append("Added " + added + " album(s); queued: " + queue.size());
    }

    private void showSiteLoginChoice() {
        if (running) { append("Finish or stop downloading before logging in."); return; }
        new AlertDialog.Builder(this).setTitle("Open site to sign in")
            .setItems(new String[]{"V2PH", "Xasiat"}, (dialog, which) ->
                    showLoginWebView(which == 0 ? "https://www.v2ph.com/" : "https://www.xasiat.com/"))
            .show();
    }

    private void showLoginWebView(String url) {
        Intent intent = new Intent(this, LoginBrowserActivity.class);
        intent.putExtra(LoginBrowserActivity.EXTRA_SITE, url);
        startActivity(intent);
    }

    private void wallpaperOptions() {
        new AlertDialog.Builder(this).setTitle("App wallpaper")
                .setItems(new String[]{"Choose image", "Remove wallpaper"}, (dialog, which) -> {
                    if (which == 0) startActivityForResult(WallpaperStore.picker(), WallpaperStore.PICK_IMAGE);
                    else {
                        WallpaperStore.clear(this);
                        WallpaperStore.apply(this, wallpaperImage);
                        append("Wallpaper removed; default gradient restored.");
                    }
                }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WallpaperStore.PICK_IMAGE && resultCode == RESULT_OK) {
            if (WallpaperStore.accept(this, data)) {
                WallpaperStore.apply(this, wallpaperImage);
                append("Wallpaper updated. It will remain after restarting the app.");
            }
        }
    }

    private void refreshSiteCookies() {
        CookieManager jar = CookieManager.getInstance();
        String v2 = jar.getCookie("https://www.v2ph.com/");
        String xa = jar.getCookie("https://www.xasiat.com/");
        sessionStatus.setText("Cookies — V2PH: " + (v2 == null || v2.isEmpty() ? "none" : "present") +
                " • Xasiat: " + (xa == null || xa.isEmpty() ? "none" : "present") +
                " (login not verified)");
    }

    @Override protected void onResume() {
        super.onResume();
        if (sessionStatus != null) refreshSiteCookies();
        if (wallpaperImage != null) WallpaperStore.apply(this, wallpaperImage);
    }

    private void begin() {
        if (running) return;
        if (queue.isEmpty()) { addToQueue(); }
        if (queue.isEmpty()) { append("Paste at least one album URL first."); return; }
        final List<String> batch = new ArrayList<>(queue);
        queue.clear();
        stop.set(false);
        paused.set(false);
        ((Button) findViewById(R.id.pause_btn)).setText("Pause");
        running = true;
        startButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            int done = 0;
            try {
                for (String url : batch) {
                    try {
                        waitWhilePaused();
                        if (stop.get()) break;
                        String site = SiteRules.site(url);
                        showStatus("Reading " + site + " album " + (done + 1) + "/" + batch.size());
                        downloadAlbum(url, site);
                        done++;
                    } catch (InterruptedException interrupted) {
                        // Paused downloads may be interrupted during activity shutdown.
                        // Restore interruption and exit the worker cleanly.
                        stop.set(true);
                        Thread.currentThread().interrupt();
                        append("Download interrupted; stopping queue.");
                        break;
                    } catch (Exception ex) {
                        append("FAILED: " + concise(ex));
                        done++;
                    }
                }
            } finally {
                int finished = done;
                ui.post(() -> {
                    running = false;
                    startButton.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    status.setText((stop.get() ? "Stopped" : "Queue completed") + " — " + finished + " processed");
                });
            }
        });
    }

    private void downloadAlbum(String albumUrl, String site) throws Exception {
        LinkedHashSet<String> imageUrls = new LinkedHashSet<>();
        String title = "Album";
        int pageCount = site.equals("V2PH") ? MAX_V2PH_PAGES : 1;
        int scanned = 0;
        for (int page = 1; page <= pageCount && !stop.get(); page++) {
            waitWhilePaused();
            String pageUrl = page == 1 ? albumUrl : Uri.parse(albumUrl).buildUpon()
                    .clearQuery().appendQueryParameter("page", String.valueOf(page)).build().toString();
            ScrapedPage result = scrapePage(pageUrl, site).get(65, TimeUnit.SECONDS);
            if (page == 1 && !result.title.isEmpty()) title = result.title;
            int before = imageUrls.size();
            imageUrls.addAll(result.images);
            int unique = imageUrls.size() - before;
            scanned++;
            append(site + " | page " + page + ": " + unique + " new images");
            // Empty or repeated page signals the end, but could also mean an
            // authentication/anti-bot page. Avoid claiming a complete album.
            if (unique == 0) break;
            if (site.equals("V2PH") && page == MAX_V2PH_PAGES) {
                append("Page limit reached; check completeness (max " + MAX_V2PH_PAGES + ").");
            }
        }
        if (stop.get()) return;
        if (imageUrls.isEmpty()) {
            append("No images found. Try Site login, or open the album in your browser first.");
            return;
        }
        String albumFolder = sanitize(SiteRules.albumKey(albumUrl) + " - " + title);
        String folder = "Pictures/Gallery Grabber/" + site + "/" + albumFolder + "/";
        int success = 0, skipped = 0, failed = 0, idx = 0;
        append("Album: " + title + " | found: " + imageUrls.size() + " | saving in: " + folder);
        for (String imageUrl : imageUrls) {
            waitWhilePaused();
            if (stop.get()) break;
            idx++;
            String filename = String.format(Locale.ROOT, "%04d.%s", idx, extension(imageUrl));
            showStatus(site + " " + idx + "/" + imageUrls.size() + " • " + title);
            try {
                if (alreadySaved(folder, filename)) { skipped++; continue; }
                downloadImage(imageUrl, albumUrl, folder, filename);
                success++;
            } catch (Exception ex) {
                failed++;
                append("Image " + idx + " failed: " + concise(ex));
            }
        }
        append(site + " album: " + success + " new, " + skipped + " skipped, " + failed + " failed. " +
                "Discovered " + imageUrls.size() + " on " + scanned + " page(s)." +
                (failed > 0 ? " Incomplete — retry this album." : ""));
    }

    private CompletableFuture<ScrapedPage> scrapePage(String url, String site) {
        CompletableFuture<ScrapedPage> future = new CompletableFuture<>();
        ui.post(() -> {
            scraper.stopLoading();
            scraper.setWebViewClient(new WebViewClient() {
                private boolean finished;
                @Override public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError err) {
                    if (request.isForMainFrame() && !future.isDone())
                        future.completeExceptionally(new IOException("Could not load page: " + err.getDescription()));
                }
                @Override public void onPageFinished(WebView view, String loadedUrl) {
                    if (finished || future.isDone()) return;
                    finished = true;
                    // Lazy loaded V2PH photos appear after scrolling. WebView JS
                    // executes on the UI thread; scrape after several scrolls.
                    scrollAndExtract(0, site, future);
                }
            });
            scraper.loadUrl(url);
        });
        return future;
    }

    private void scrollAndExtract(int step, String site, CompletableFuture<ScrapedPage> future) {
        if (future.isDone()) return;
        if (step < (site.equals("V2PH") ? 8 : 1)) {
            scraper.evaluateJavascript("window.scrollTo(0, Math.round(document.body.scrollHeight * " +
                    ((step + 1) / 8.0) + "));", unused ->
                    ui.postDelayed(() -> scrollAndExtract(step + 1, site, future), 550));
            return;
        }
        String js = "(function(){" +
            "var urls=[];" +
            (site.equals("Xasiat") ?
                "document.querySelectorAll('.images a[href]').forEach(function(a){if(a.href.includes('/get_image/'))urls.push(a.href);});" :
                "document.querySelectorAll('img').forEach(function(i){var c=[i.getAttribute('data-original'),i.getAttribute('data-src'),i.currentSrc,i.getAttribute('src')];for(var a of c){if(!a)continue;try{var u=new URL(a,location.href);if(u.protocol==='https:' && (u.hostname==='v2ph.com'||u.hostname.endsWith('.v2ph.com')) && u.pathname.startsWith('/photos/')){urls.push(u.href);break;}}catch(e){}}});") +
            "var h=document.querySelector('h1');return JSON.stringify({title:h?h.textContent.trim():'Album',images:[...new Set(urls)]});})()";
        scraper.evaluateJavascript(js, result -> {
            try {
                Object parsed = new JSONTokener(result).nextValue();
                JSONObject data = new JSONObject(String.valueOf(parsed));
                JSONArray values = data.getJSONArray("images");
                List<String> urls = new ArrayList<>();
                for (int i=0; i<values.length(); i++) {
                    String imageUrl = values.getString(i);
                    if (imageUrl.startsWith("https://")) urls.add(imageUrl);
                }
                future.complete(new ScrapedPage(data.optString("title", "Album"), urls));
            } catch (Exception e) { future.completeExceptionally(e); }
        });
    }

    private void downloadImage(String src, String referer, String directory, String name) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(src).openConnection();
        connection.setConnectTimeout(18000);
        connection.setReadTimeout(24000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", browserUserAgent);
        connection.setRequestProperty("Referer", referer);
        String cookies = CookieManager.getInstance().getCookie(src);
        if (cookies == null || cookies.isEmpty()) cookies = CookieManager.getInstance().getCookie(referer);
        if (cookies != null) connection.setRequestProperty("Cookie", cookies);
        Uri output = null;
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            String type = connection.getContentType();
            if (type != null && (type.toLowerCase(Locale.ROOT).contains("text/html") ||
                    type.toLowerCase(Locale.ROOT).contains("application/json")))
                throw new IOException("Server returned HTML/JSON instead of an image (login may be required)");
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, mime(name));
            values.put(MediaStore.Images.Media.RELATIVE_PATH, directory);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            ContentResolver cr = getContentResolver();
            output = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (output == null) throw new IOException("Could not create image in Pictures");
            try (InputStream from = connection.getInputStream(); OutputStream to = cr.openOutputStream(output)) {
                if (to == null) throw new IOException("Could not open MediaStore output");
                byte[] buffer = new byte[65536];
                int bytes, total = 0;
                while ((bytes = from.read(buffer)) >= 0) {
                    waitWhilePaused();
                    if (stop.get()) throw new IOException("Stopped");
                    to.write(buffer, 0, bytes);
                    total += bytes;
                }
                if (total < 512) throw new IOException("Response too small to be an image");
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(output, ready, null, null);
            output = null;
        } finally {
            if (output != null) getContentResolver().delete(output, null, null);
            connection.disconnect();
        }
    }

    private void waitWhilePaused() throws InterruptedException {
        while (paused.get() && !stop.get()) Thread.sleep(150);
    }

    private boolean alreadySaved(String relative, String name) {
        String[] columns = { MediaStore.Images.Media._ID };
        String filter = MediaStore.Images.Media.RELATIVE_PATH + "=? AND " +
                MediaStore.Images.Media.DISPLAY_NAME + "=?";
        try (Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                columns, filter, new String[]{relative, name}, null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    private static String sanitize(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return clean.substring(0, Math.min(clean.length(), 95));
    }
    private static String extension(String url) {
        Matcher matcher = EXT.matcher(Uri.parse(url).getPath() == null ? "" : Uri.parse(url).getPath());
        if (matcher.find()) {
            String ext = matcher.group(1).toLowerCase(Locale.ROOT);
            return ext.equals("jpeg") ? "jpg" : ext;
        }
        return "jpg";
    }
    private static String mime(String filename) {
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".webp")) return "image/webp";
        if (filename.endsWith(".gif")) return "image/gif";
        if (filename.endsWith(".bmp")) return "image/bmp";
        if (filename.endsWith(".avif")) return "image/avif";
        return "image/jpeg";
    }
    private static String concise(Exception error) {
        String reason = error.getMessage();
        return reason != null ? reason : error.getClass().getSimpleName();
    }
    private void showStatus(String message) { ui.post(() -> status.setText(message)); }
    private void append(String line) {
        ui.post(() -> {
            String current = log.getText().toString();
            if (current.length() > 23000) current = current.substring(current.length() - 16000);
            log.setText(current + "\n" + line);
        });
    }

    @Override protected void onDestroy() {
        stop.set(true);
        worker.shutdownNow();
        if (scraper != null) { scraper.stopLoading(); scraper.destroy(); }
        super.onDestroy();
    }
    private static final class ScrapedPage {
        final String title;
        final List<String> images;
        ScrapedPage(String title, List<String> images) { this.title = title; this.images = images; }
    }
}
