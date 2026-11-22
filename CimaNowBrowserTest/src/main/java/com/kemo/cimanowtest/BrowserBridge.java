package com.kemo.cimanowtest;

// Original experimental bridge for Kareem. No third-party provider implementation copied.
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BrowserBridge {
    public interface Listener {
        void onMedia(Media media);
        void onClosed(String reason);
    }
    public static final class Media {
        public final String url;
        public final Map<String, String> headers;
        public final boolean hls;
        Media(String url, Map<String, String> headers, boolean hls) {
            this.url = url;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            this.hls = hls;
        }
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final IdentityHashMap<WebView, Dialog> windows = new IdentityHashMap<>();
    private final Map<String, Media> candidates = new LinkedHashMap<>();
    private final List<Button> buttons = new ArrayList<>();
    private final Activity activity;
    private Listener listener;
    private TextView status;
    private LinearLayout choices;
    private boolean active;
    private volatile HttpsURLConnection checking;
    private final Runnable timeout = () -> finish(null, "انتهت مهلة الاختبار. افتح الاختبار من جديد لو حبيت تحاول لاحقاً.");
    private final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityDestroyed(Activity a) { if (a == activity) cancel(); }
        @Override public void onActivityCreated(Activity a, Bundle b) { }
        @Override public void onActivityStarted(Activity a) { }
        @Override public void onActivityResumed(Activity a) { }
        @Override public void onActivityPaused(Activity a) { }
        @Override public void onActivityStopped(Activity a) { }
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    };

    public BrowserBridge(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }
    public void open(String initialUrl) {
        main.post(() -> {
            if (listener == null) return;
            if (activity.isFinishing() || activity.isDestroyed()) { finish(null, "شاشة التطبيق مش متاحة."); return; }
            if (!isHttps(initialUrl) || !"cimanow.cc".equalsIgnoreCase(Uri.parse(initialUrl).getHost())) {
                finish(null, "الاختبار بيبدأ من موقع cimanow.cc فقط."); return;
            }
            active = true;
            activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);
            main.postDelayed(timeout, 270000L);
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            status = new TextView(activity);
            status.setPadding(dp(10), dp(8), dp(10), dp(8));
            status.setText("تجربة سيما ناو، مش تشغيل مضمون. افتح العمل واكمل التحقق بنفسك. اضغط تشغيل في صفحة الموقع. أي روابط تظهر تحت ممكن تكون إعلانات؛ اختار رابط العمل فقط.");
            layout.addView(status);
            LinearLayout toolbar = new LinearLayout(activity);
            Button close = new Button(activity);
            close.setText("إلغاء");
            close.setOnClickListener(v -> cancel());
            toolbar.addView(close);
            Button home = new Button(activity);
            home.setText("رئيسية سيما ناو");
            toolbar.addView(home);
            layout.addView(toolbar);
            WebView browser = browser();
            home.setOnClickListener(v -> browser.loadUrl("https://cimanow.cc/"));
            layout.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
            choices = new LinearLayout(activity);
            choices.setOrientation(LinearLayout.VERTICAL);
            ScrollView list = new ScrollView(activity);
            list.addView(choices);
            layout.addView(list, new LinearLayout.LayoutParams(-1, dp(130)));
            show(browser, layout, true);
            browser.loadUrl(initialUrl);
        });
    }
    private WebView browser() {
        WebView browser = new WebView(activity);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        // Native UA, cookie handling and TLS checks stay unchanged. No injected code or JS bridge.
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isHttps(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !isHttps(url); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String path = request.getUrl().getPath();
                path = path == null ? "" : path.toLowerCase(Locale.ROOT);
                if (isHttps(url) && (path.endsWith(".m3u8") || path.endsWith(".mp4"))) {
                    Map<String, String> headers = new LinkedHashMap<>(request.getRequestHeaders());
                    main.post(() -> observe(url, headers, view));
                }
                return null; // Native browser handles the original request and response.
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean userGesture, Message result) {
                if (!userGesture || !active || windows.size() >= 3) return false;
                WebView popup = browser();
                LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                Button closePopup = new Button(activity);
                closePopup.setText("إغلاق النافذة والرجوع");
                layout.addView(closePopup);
                layout.addView(popup, new LinearLayout.LayoutParams(-1, 0, 1));
                Dialog dialog = show(popup, layout, false);
                closePopup.setOnClickListener(v -> dialog.dismiss());
                WebView.WebViewTransport transport = (WebView.WebViewTransport) result.obj;
                transport.setWebView(popup);
                result.sendToTarget();
                return true;
            }
            @Override public void onCloseWindow(WebView window) {
                Dialog dialog = windows.get(window);
                if (dialog != null) dialog.dismiss();
            }
        });
        return browser;
    }
    private Dialog show(WebView browser, View content, boolean primary) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        windows.put(browser, dialog);
        dialog.setOnDismissListener(ignored -> {
            if (primary) finish(null, "اتقفل اختبار المتصفح.");
            else destroy(browser);
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
        return dialog;
    }
    private void observe(String url, Map<String, String> original, WebView browser) {
        if (!active || candidates.containsKey(url) || candidates.size() >= 12) return;
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : original.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (key.equals("referer") || key.equals("origin") || key.equals("user-agent") || key.equals("authorization"))
                headers.put(key, entry.getValue());
        }
        if (!headers.containsKey("user-agent")) headers.put("user-agent", browser.getSettings().getUserAgentString());
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null) headers.put("cookie", cookie);
        boolean hls = Uri.parse(url).getPath().toLowerCase(Locale.ROOT).endsWith(".m3u8");
        Media media = new Media(url, headers, hls);
        candidates.put(url, media);
        Button button = new Button(activity);
        button.setText("فحص وتشغيل مرشح " + candidates.size() + " • " + Uri.parse(url).getHost() + (hls ? " • HLS" : " • MP4"));
        button.setOnClickListener(v -> validate(media));
        buttons.add(button);
        choices.addView(button);
        status.setText("ظهر " + candidates.size() + " مرشح فيديو. ممكن يكون إعلان. بعد ما يبدأ العمل في الموقع، اختار مرشحه للفحص ثم التشغيل في التطبيق.");
    }
    private void validate(Media media) {
        for (Button button : buttons) button.setEnabled(false);
        status.setText("بفحص نوع استجابة الرابط المختار، مش هنبعت رابط HTML للمشغل.");
        worker.execute(() -> {
            String failure = null;
            HttpsURLConnection connection = null;
            try {
                URL url = new URL(media.url);
                if (!"https".equalsIgnoreCase(url.getProtocol())) throw new Exception("HTTPS required");
                for (InetAddress address : InetAddress.getAllByName(url.getHost())) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress())
                        throw new Exception("Local address rejected");
                }
                connection = (HttpsURLConnection) url.openConnection();
                checking = connection;
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                for (Map.Entry<String, String> entry : media.headers.entrySet()) connection.setRequestProperty(entry.getKey(), entry.getValue());
                connection.setRequestProperty("Range", "bytes=0-4095");
                int code = connection.getResponseCode();
                if (code != 200 && code != 206) throw new Exception("HTTP " + code);
                byte[] bytes = new byte[4096];
                int size = 0;
                try (InputStream in = connection.getInputStream()) {
                    while (size < bytes.length) {
                        int read = in.read(bytes, size, bytes.length - size);
                        if (read < 0) break;
                        size += read;
                    }
                }
                boolean good = MediaSignatures.matches(bytes, size, media.hls);
                if (!good) throw new Exception("Response is not expected media");
            } catch (Exception error) {
                failure = "الفحص ما نجحش. ممكن الرابط منتهي أو بيحوّل أو محتاج جلسة مختلفة. اختار مرشح تاني؛ ده مش دليل إن الموقع كله مش شغال.";
            } finally {
                if (connection != null) connection.disconnect();
                checking = null;
            }
            final String result = failure;
            main.post(() -> {
                if (!active) return;
                if (result == null) finish(media, null);
                else {
                    status.setText(result);
                    for (Button button : buttons) button.setEnabled(true);
                }
            });
        });
    }
    public void cancel() {
        if (Looper.myLooper() == Looper.getMainLooper()) finish(null, "تم إلغاء اختبار سيما ناو.");
        else main.post(() -> finish(null, "تم إلغاء اختبار سيما ناو."));
    }
    private void finish(Media media, String reason) {
        if (listener == null) return;
        Listener callback = listener;
        listener = null;
        active = false;
        main.removeCallbacks(timeout);
        activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);
        HttpsURLConnection connection = checking;
        if (connection != null) connection.disconnect();
        worker.shutdownNow();
        for (WebView browser : new ArrayList<>(windows.keySet())) destroy(browser);
        candidates.clear();
        buttons.clear();
        if (media != null) callback.onMedia(media);
        else callback.onClosed(reason == null ? "اتقفل الاختبار." : reason);
    }
    private void destroy(WebView browser) {
        Dialog dialog = windows.remove(browser);
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
        browser.stopLoading();
        browser.destroy();
    }
    private int dp(int value) { return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f); }
    private static boolean isHttps(String url) {
        Uri uri = Uri.parse(url);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
    }
}
