package com.kemo.cimanowtest;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowWebView;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28,33}, manifest = Config.NONE, qualifiers = "w800dp-h360dp-land-mdpi", shadows = BrowserLayoutTest.MeasuredWebView.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class BrowserLayoutTest {
    // No Chromium or external network runs here. Robolectric's WebView measurement
    // stub is zero-sized by default. Honour exact parent specs to test allocation.
    @Implements(WebView.class)
    public static class MeasuredWebView extends ShadowWebView {
        @RealObject private WebView webView;
        @Implementation protected boolean setFrame(int left,int top,int right,int bottom) {
            return org.robolectric.shadow.api.Shadow.directlyOn(webView,View.class,"setFrame",
                ClassParameter.from(int.class,left),ClassParameter.from(int.class,top),
                ClassParameter.from(int.class,right),ClassParameter.from(int.class,bottom));
        }
        @Implementation protected void onMeasure(int widthSpec, int heightSpec) {
            ReflectionHelpers.callInstanceMethod(webView, "setMeasuredDimension",
                ClassParameter.from(int.class, View.MeasureSpec.getSize(widthSpec)),
                ClassParameter.from(int.class, View.MeasureSpec.getSize(heightSpec)));
        }
    }
    private static class Session {
        Activity activity;
        BrowserBridge bridge;
        Dialog dialog;
        View root;
        WebView browser;
        int closed;
        Session() {this(800,360);}
        Session(int width,int height) {
            if(android.os.Build.VERSION.SDK_INT>=33)org.robolectric.shadows.ShadowView.setUseRealDrawTraversals(true);
            activity = Robolectric.buildActivity(Activity.class).setup().get();
            bridge = new BrowserBridge(activity, new BrowserBridge.Listener() {
                @Override public void onMedia(BrowserBridge.Media media) { fail("No media should be validated in layout tests"); }
                @Override public void onClosed(String reason) { closed++; }
            });
            bridge.open("https://cimanow.cc/");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            dialog = ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            ViewGroup content = dialog.findViewById(android.R.id.content);
            root = content.getChildAt(0);
            layout(width,height);
            browser = findBrowser(root);
            assertNotNull(browser);
        }
        void layout(int width, int height) {
            forceLayout(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
        }
        void close() { bridge.cancel(); assertFalse(dialog.isShowing()); assertEquals(1,closed); }
    }
    @Test public void landscapeReservesPageNotEmptyCandidates() throws Exception {
        Session s = new Session();
        assertEquals(-1,s.dialog.getWindow().getAttributes().width);
        assertEquals(-1,s.dialog.getWindow().getAttributes().height);
        assertEquals(313,s.browser.getHeight());
        assertEquals(800,s.browser.getWidth());
        assertEquals("https://cimanow.cc/",Shadows.shadowOf(s.browser).getLastLoadedUrl());
        screenshot(s.root,"browser-landscape-no-candidates.png",800,360);
        s.close();
        org.robolectric.RuntimeEnvironment.setQualifiers("w640dp-h270dp-land-mdpi");
        Session small=new Session(640,270);
        assertEquals(223,small.browser.getHeight());
        screenshot(small.root,"browser-small-landscape.png",640,270);
        small.close();
        org.robolectric.RuntimeEnvironment.setQualifiers("w800dp-h360dp-land-mdpi");
    }
    @Test public void candidateStripAppearsOnlyAfterObservation() throws Exception {
        Session s = new Session();
        Method observe = BrowserBridge.class.getDeclaredMethod("observe",String.class,Map.class,WebView.class);
        observe.setAccessible(true);
        observe.invoke(s.bridge,"https://media.example.test/fixture.m3u8",Collections.emptyMap(),s.browser);
        s.layout(800,360);
        assertEquals(257,s.browser.getHeight());
        screenshot(s.root,"browser-landscape-one-fixture-candidate.png",800,360);
        s.close();
    }
    @Test public void helpClosesWithBrowserSession() throws Exception {
        Session s=new Session();
        Method help=BrowserBridge.class.getDeclaredMethod("showHelp");help.setAccessible(true);help.invoke(s.bridge);
        Dialog helpDialog=ShadowDialog.getLatestDialog();assertNotSame(s.dialog,helpDialog);assertTrue(helpDialog.isShowing());
        s.close();assertFalse(helpDialog.isShowing());
    }
    @Test public void pageFinishedDoesNotHideHttpFailure() {
        Session s = new Session();
        String url="https://cimanow.cc/";
        s.browser.getWebViewClient().onPageStarted(s.browser,url,null);
        s.browser.getWebViewClient().onReceivedHttpError(s.browser,new WebResourceRequest() {
            public Uri getUrl(){return Uri.parse(url);}
            public boolean isForMainFrame(){return true;}
            public boolean isRedirect(){return false;}
            public boolean hasGesture(){return false;}
            public String getMethod(){return "GET";}
            public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}
        },new WebResourceResponse("text/html","UTF-8",403,"Forbidden",Collections.emptyMap(),new ByteArrayInputStream(new byte[0])));
        s.browser.getWebViewClient().onPageFinished(s.browser,url);
        assertTrue(text(s.root).contains("HTTP 403"));
        s.close();
    }
    private static void forceLayout(View v) {
        v.forceLayout();
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)forceLayout(g.getChildAt(i));}
    }
    private static WebView findBrowser(View view) {
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){WebView w=findBrowser(g.getChildAt(i));if(w!=null)return w;}}
        return null;
    }
    private static String text(View view) {
        if(view instanceof TextView)return ((TextView)view).getText().toString();
        StringBuilder value=new StringBuilder();
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)value.append(text(g.getChildAt(i)));}
        return value.toString();
    }
    private static void screenshot(View view,String name,int width,int height)throws Exception {
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);
        view.draw(canvas);
        // Annotate the measured slot. This is deliberately not web-rendering proof.
        WebView browser=findBrowser(view);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(android.graphics.Color.rgb(239,242,246));
        canvas.drawRect(browser.getLeft(),browser.getTop(),browser.getRight(),browser.getBottom(),paint);
        paint.setColor(android.graphics.Color.DKGRAY);paint.setTextSize(14);
        canvas.drawText("ANNOTATED LAYOUT PREVIEW - no website/Chromium",16,browser.getTop()+28,paint);
        canvas.drawText("Measured browser slot: "+browser.getWidth()+" x "+browser.getHeight(),16,browser.getTop()+50,paint);
        File folder=new File("build/layout-evidence");folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,"api"+android.os.Build.VERSION.SDK_INT+"-"+name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
}
