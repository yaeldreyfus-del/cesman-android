package com.cesmaneg.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private View offline;
    private String homeUrl;

    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;

    private final ActivityResultLauncher<Intent> filePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (fileCallback == null) return;
                fileCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData()));
                fileCallback = null;
            });

    private final ActivityResultLauncher<String[]> mediaPermissions =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (pendingPermission == null) return;
                List<String> ok = new ArrayList<>();
                for (String res : pendingPermission.getResources()) {
                    if (res.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && has(Manifest.permission.CAMERA)) ok.add(res);
                    if (res.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && has(Manifest.permission.RECORD_AUDIO)) ok.add(res);
                }
                if (ok.isEmpty()) pendingPermission.deny();
                else pendingPermission.grant(ok.toArray(new String[0]));
                pendingPermission = null;
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        homeUrl = getString(R.string.site_url);
        webView = findViewById(R.id.webview);
        swipe = findViewById(R.id.swipe);
        progress = findViewById(R.id.progress);
        offline = findViewById(R.id.offline);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                swipe.setRefreshing(false);
                CookieManager.getInstance().flush();
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    swipe.setRefreshing(false);
                    offline.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            }
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    filePicker.launch(params.createIntent());
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    pendingPermission = request;
                    mediaPermissions.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
                });
            }
        });

        // Désactive le "tirer pour actualiser" quand la page n'est pas tout en haut
        swipe.setColorSchemeResources(R.color.brand);
        swipe.setOnChildScrollUpCallback((parent, child) -> webView.getScrollY() > 0);
        swipe.setOnRefreshListener(() -> webView.reload());

        findViewById(R.id.retry).setOnClickListener(v -> {
            offline.setVisibility(View.GONE);
            webView.reload();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            Uri deepLink = getIntent() != null ? getIntent().getData() : null;
            webView.loadUrl(deepLink != null ? deepLink.toString() : homeUrl);
        }
    }

    /** Le site reste dans l'app ; téléphone, WhatsApp, e-mail, cartes, Zoom… s'ouvrent dans l'app adaptée. */
    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean web = scheme.equals("http") || scheme.equals("https");
        boolean internal = web && (host.endsWith("cesmaneg.com") || host.endsWith("wix.com")
                || host.endsWith("wixsite.com") || host.endsWith("wixstatic.com")
                || host.endsWith("parastorage.com") || host.endsWith("wixapps.net"));
        if (internal) return false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException ignored) { }
        return true;
    }

    private boolean has(String perm) {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getData() != null) webView.loadUrl(intent.getData().toString());
    }
}
