package com.cesmaneg.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private View offline;
    private String homeUrl;
    private String tidyScript = "";

    private final TextView[] navItems = new TextView[4];
    private final String[] navPaths = {"/", "/book-online", "/news", "/blank-2"};

    private boolean firstPageShown = false;
    private long splashStart;
    private long lastBackPress = 0;

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
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Garde l'écran CESMAN jusqu'à ce que la page soit prête (8 s maximum) : plus d'écran blanc au lancement
        splashStart = SystemClock.uptimeMillis();
        splash.setKeepOnScreenCondition(() ->
                !firstPageShown && SystemClock.uptimeMillis() - splashStart < 8000);

        homeUrl = getString(R.string.site_url);
        tidyScript = readAsset("tidy.js");
        webView = findViewById(R.id.webview);
        swipe = findViewById(R.id.swipe);
        progress = findViewById(R.id.progress);
        offline = findViewById(R.id.offline);

        setupWebView();
        setupBottomNav();

        // Tirer pour actualiser : seulement tout en haut de la page, et il faut tirer franchement
        swipe.setColorSchemeResources(R.color.accent);
        swipe.setDistanceToTriggerSync(280);
        swipe.setOnChildScrollUpCallback((parent, child) -> webView.getScrollY() > 0);
        swipe.setOnRefreshListener(() -> webView.reload());

        findViewById(R.id.retry).setOnClickListener(v -> {
            offline.setVisibility(View.GONE);
            applyCacheMode();
            webView.reload();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (offline.getVisibility() == View.VISIBLE && webView.canGoBack()) {
                    offline.setVisibility(View.GONE);
                    webView.goBack();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else if (SystemClock.uptimeMillis() - lastBackPress < 2000) {
                    finish();
                } else {
                    lastBackPress = SystemClock.uptimeMillis();
                    Toast.makeText(MainActivity.this, R.string.press_again, Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            firstPageShown = true;
        } else {
            Uri deepLink = getIntent() != null ? getIntent().getData() : null;
            webView.loadUrl(deepLink != null ? deepLink.toString() : homeUrl);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        // Comportement d'application : pas de zoom involontaire, texte stable quelle que soit la police du téléphone
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        // Défilement plus fluide : le contenu hors écran est pré-dessiné
        s.setOffscreenPreRaster(true);
        applyCacheMode();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        }
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                injectTidy();
                firstPageShown = true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectTidy();
                firstPageShown = true;
                swipe.setRefreshing(false);
                CookieManager.getInstance().flush();
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                highlightNav(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    swipe.setRefreshing(false);
                    firstPageShown = true;
                    offline.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
                if (p >= 70) firstPageShown = true;
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

        // Fichiers à télécharger (PDF, etc.) : ouverts dans l'application adaptée
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) ->
                openExternal(Uri.parse(url)));
    }

    /** Barre de navigation en bas de l'écran, toujours accessible au pouce. */
    private void setupBottomNav() {
        int[] ids = {R.id.nav_home, R.id.nav_services, R.id.nav_news, R.id.nav_contact};
        for (int i = 0; i < ids.length; i++) {
            final String target = homeUrl.replaceAll("/$", "") + navPaths[i];
            navItems[i] = findViewById(ids[i]);
            navItems[i].setOnClickListener(v -> {
                offline.setVisibility(View.GONE);
                String current = webView.getUrl();
                if (current != null && samePage(current, target)) {
                    webView.evaluateJavascript("window.scrollTo({top:0,behavior:'smooth'})", null);
                } else {
                    webView.loadUrl(target);
                }
            });
        }
        findViewById(R.id.nav_whatsapp).setOnClickListener(v ->
                openExternal(Uri.parse(getString(R.string.whatsapp_url))));
        highlightNav(homeUrl);
    }

    private void highlightNav(String url) {
        if (url == null) return;
        String path = Uri.parse(url).getPath();
        if (path == null || path.isEmpty()) path = "/";
        for (int i = 0; i < navItems.length; i++) {
            if (navItems[i] == null) continue;
            boolean active = i == 0 ? path.equals("/") : path.startsWith(navPaths[i]);
            int color = ContextCompat.getColor(this, active ? R.color.accent : R.color.nav_inactive);
            navItems[i].setTextColor(color);
            navItems[i].setCompoundDrawableTintList(ColorStateList.valueOf(color));
        }
    }

    private boolean samePage(String a, String b) {
        String pa = Uri.parse(a).getPath(), pb = Uri.parse(b).getPath();
        if (pa == null || pa.isEmpty()) pa = "/";
        if (pb == null || pb.isEmpty()) pb = "/";
        return pa.equals(pb);
    }

    private void injectTidy() {
        if (!tidyScript.isEmpty()) webView.evaluateJavascript(tidyScript, null);
    }

    /** Sans réseau, on affiche la dernière version gardée en mémoire plutôt qu'une page d'erreur. */
    private void applyCacheMode() {
        webView.getSettings().setCacheMode(isOnline()
                ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
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
        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException ignored) { }
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private boolean has(String perm) {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
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
