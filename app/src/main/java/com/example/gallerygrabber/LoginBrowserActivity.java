package com.example.gallerygrabber;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/** Full-screen WebView login browser sharing the same cookie jar as the scraper. */
public class LoginBrowserActivity extends Activity {
    public static final String EXTRA_SITE = "site_url";
    private String siteHome;
    private WebView web;
    private EditText address;
    private TextView cookieIndicator;
    private Button back, forward;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        siteHome = getIntent().getStringExtra(EXTRA_SITE);
        if (!"https://www.v2ph.com/".equals(siteHome) &&
                !"https://www.xasiat.com/".equals(siteHome)) { finish(); return; }
        ScreenChrome.edgeToEdge(this);
        setContentView(R.layout.activity_login_browser);
        ScreenChrome.padControlsForSystemBars(findViewById(R.id.browser_content));
        WallpaperStore.apply(this, findViewById(R.id.wallpaper_image));
        ((TextView) findViewById(R.id.browser_title)).setText(
                siteHome.contains("xasiat") ? "XASIAT LOGIN" : "V2PH LOGIN");
        address = findViewById(R.id.browser_address);
        cookieIndicator = findViewById(R.id.browser_session);
        progress = findViewById(R.id.browser_progress);
        back = findViewById(R.id.browser_back);
        forward = findViewById(R.id.browser_forward);
        web = findViewById(R.id.browser_webview);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadsImagesAutomatically(true);
        web.getSettings().setSupportZoom(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                address.setText(url);
                cookieIndicator.setText("Loading… (login not yet verified)");
                refreshNavigation();
            }
            @Override public void onPageFinished(WebView view, String url) {
                address.setText(url);
                refreshNavigation();
                updateCookieStatus();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                // Prevent custom schemes, javascript:, file:, and cleartext navigations.
                if (!"https".equalsIgnoreCase(uri.getScheme())) {
                    Toast.makeText(LoginBrowserActivity.this,
                            "Only HTTPS pages open inside this browser", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                    WebResourceError error) {
                if (request.isForMainFrame()) cookieIndicator.setText("Page failed: " + error.getDescription());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int amount) {
                progress.setProgress(amount);
                progress.setVisibility(amount >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        findViewById(R.id.browser_done).setOnClickListener(v -> done());
        findViewById(R.id.browser_go).setOnClickListener(v -> navigateAddress());
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateAddress(); return true;
            }
            return false;
        });
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        forward.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
        findViewById(R.id.browser_reload).setOnClickListener(v -> web.reload());
        findViewById(R.id.browser_home).setOnClickListener(v -> web.loadUrl(siteHome));
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(siteHome);
        }
        updateCookieStatus();
    }

    private void navigateAddress() {
        String entered = address.getText().toString().trim();
        if (!entered.startsWith("https://")) {
            Toast.makeText(this, "Use a full https:// URL", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = Uri.parse(entered);
            if (uri.getHost() == null || uri.getHost().isEmpty()) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, "Invalid address", Toast.LENGTH_SHORT).show();
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus();
        web.requestFocus();
        web.loadUrl(entered);
    }

    private void updateCookieStatus() {
        // Presence of cookies is not proof that the site accepted a login.
        String cookies = CookieManager.getInstance().getCookie(siteHome);
        cookieIndicator.setText(cookies == null || cookies.isEmpty() ?
                "No site cookies detected • sign-in not verified" :
                "Site cookies present • sign-in not verified");
    }

    private void refreshNavigation() {
        back.setEnabled(web.canGoBack());
        forward.setEnabled(web.canGoForward());
    }

    private void done() {
        CookieManager.getInstance().flush();
        finish();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else done();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (web != null) web.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        CookieManager.getInstance().flush();
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
