package au.scout.mobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.content.ClipData;
import android.content.ClipboardManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class ScoutActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String,String> urls = new LinkedHashMap<>();
    private FrameLayout frame;
    private WebView dashboard, retailer;
    private String extractor;
    private String activeStore;
    private long deadline;
    private final Map<String,JSONObject> cards = new LinkedHashMap<>();
    private int passes;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        urls.put("Kmart","https://www.kmart.com.au/category/clearance/shop-all-clearance/");
        urls.put("Officeworks","https://www.officeworks.com.au/shop/officeworks/c/clearance");
        urls.put("BIG W","https://www.bigw.com.au/collection/clearance");
        frame = new FrameLayout(this);
        setContentView(frame);
        frame.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        dashboard = makeView();
        // Only bundled UI gets the native interface. Retailer pages run in a separate WebView.
        dashboard.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req){
                return true;
            }
        });
        dashboard.addJavascriptInterface(new Bridge(),"ScoutDevice");
        frame.addView(dashboard);
        dashboard.loadDataWithBaseURL("https://scout.invalid/",asset("scout.html"),"text/html","UTF-8",null);
        extractor=asset("extract.js");
    }
    private String asset(String name){
        try(InputStream in=getAssets().open(name);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }catch(Exception e){throw new IllegalStateException(name,e);}
    }
    private WebView makeView(){
        WebView v=new WebView(this);
        v.getSettings().setJavaScriptEnabled(true);
        v.getSettings().setDomStorageEnabled(true);
        v.getSettings().setAllowFileAccess(false);
        v.getSettings().setAllowContentAccess(false);
        return v;
    }
    public class Bridge {
        @JavascriptInterface public void scan(String store){handler.post(()->start(store));}
        @JavascriptInterface public void copy(String text){handler.post(()->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Scout log",text)));}
        @JavascriptInterface public void open(String link){handler.post(()->{
            Uri u=Uri.parse(link);if("https".equals(u.getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(android.content.ActivityNotFoundException ignored){}
        });}
        @JavascriptInterface public void export(String text){handler.post(()->{
            pendingLog=text;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"Clearance-Scout-log.json");
            startActivityForResult(i,10);
        });}
    }
    private String pendingLog;
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==10&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingLog!=null){
            try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){
                out.write(pendingLog.getBytes(StandardCharsets.UTF_8));
            }catch(Exception e){android.widget.Toast.makeText(this,"Could not save log",android.widget.Toast.LENGTH_LONG).show();}
        }
        pendingLog=null;
    }
    private boolean sameShop(String url){
        if(url==null||activeStore==null)return false;
        Uri u=Uri.parse(url);
        return "https".equals(u.getScheme())&&Uri.parse(urls.get(activeStore)).getHost().equals(u.getHost());
    }
    private void start(String store){
        if(!urls.containsKey(store)||retailer!=null)return;
        activeStore=store;cards.clear();passes=0;
        retailer=makeView();WebView current=retailer;
        frame.addView(current,0,new FrameLayout.LayoutParams(-1,-1));
        current.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){
                return r.isForMainFrame()&&!sameShop(r.getUrl().toString());
            }
        });
        deadline=SystemClock.elapsedRealtime()+35000;
        current.loadUrl(urls.get(store));
        handler.postDelayed(()->poll(current),1500);
        handler.postDelayed(()->{if(retailer==current)finishScan("Scan time limit reached");},36000);
    }
    private void poll(WebView v){
        if(v!=retailer)return;
        if(SystemClock.elapsedRealtime()>=deadline){finishScan("No readable cards before timeout");return;}
        if(!sameShop(v.getUrl())){handler.postDelayed(()->poll(v),800);return;}
        v.evaluateJavascript(extractor,value->{
            if(v!=retailer)return;
            try{
                JSONObject data=new JSONObject(value);
                if(data.optBoolean("blocked")){finishScan("Retailer requested browser verification");return;}
                JSONArray rows=data.optJSONArray("rows");
                if(rows!=null)for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.getJSONObject(i);
                    String link=row.getString("url");
                    if(sameShop(link)&&(!cards.containsKey(link)||!row.isNull("now")))cards.put(link,row);
                }
            }catch(Exception ignored){}
            if(!cards.isEmpty()&&++passes>=4){finishScan("Rendered clearance cards");return;}
            handler.postDelayed(()->poll(v),1500);
        });
    }
    private void finishScan(String status){
        if(retailer==null)return;
        String store=activeStore;
        frame.removeView(retailer);retailer.stopLoading();retailer.destroy();retailer=null;activeStore=null;
        JSONArray rows=new JSONArray(cards.values());
        dashboard.evaluateJavascript("window.scoutResult("+JSONObject.quote(store)+","+rows+","+JSONObject.quote(status)+")",null);
    }
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(retailer!=null){frame.removeView(retailer);retailer.destroy();retailer=null;}
        frame.removeView(dashboard);dashboard.removeJavascriptInterface("ScoutDevice");dashboard.destroy();
        super.onDestroy();
    }
}
