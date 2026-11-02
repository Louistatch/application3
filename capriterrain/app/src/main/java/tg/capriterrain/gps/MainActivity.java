package tg.capriterrain.gps;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.OutputStream;

public class MainActivity extends Activity implements LocationListener {
    private WebView web;
    private LocationManager manager;
    private boolean listening = false;
    private byte[] fileToSave;
    private static final int REQUEST_LOCATION=501, REQUEST_SAVE=502;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff0b392f);
        getWindow().setNavigationBarColor(0xff0b392f);
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        web = new WebView(this);
        web.setBackgroundColor(0xfff3f6f1);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest req) {
                return !"file".equals(req.getUrl().getScheme());
            }
        });
        web.addJavascriptInterface(new Bridge(),"CapriAndroid");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    public class Bridge {
        @JavascriptInterface public void startGPS(){
            runOnUiThread(()->requestGPS());
        }
        @JavascriptInterface public void saveFile(String name,String mime,String base64){
            runOnUiThread(()->{
                try{
                    fileToSave=Base64.decode(base64,Base64.DEFAULT);
                    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(mime==null||mime.isEmpty()?"application/octet-stream":mime);
                    intent.putExtra(Intent.EXTRA_TITLE,name.replace('/','_').replace('\\','_'));
                    startActivityForResult(intent,REQUEST_SAVE);
                }catch(Exception e){error("Export impossible : "+e.getMessage());}
            });
        }
    }

    private void requestGPS(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
           checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        } else beginListening();
    }

    @SuppressWarnings("MissingPermission") private void beginListening(){
        if(listening)return;
        try {
            boolean started=false;
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED &&
               manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,this);
                started=true;
                Location last=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(last!=null&&System.currentTimeMillis()-last.getTime()<10000)onLocationChanged(last);
            }
            if((checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) &&
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1500L,0f,this);
                started=true;
            }
            listening=started;
            if(!started)error("Activez la localisation GPS dans les paramètres du téléphone.");
        } catch(Exception ex){error("Erreur GPS : "+ex.getMessage());}
    }

    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] grants){
        super.onRequestPermissionsResult(code,perms,grants);
        if(code==REQUEST_LOCATION){
            for(int g:grants)if(g==PackageManager.PERMISSION_GRANTED){beginListening();return;}
            error("Permission localisation refusée : autorisez la position précise.");
        }
    }

    @Override public void onLocationChanged(Location loc){
        try{
            JSONObject data=new JSONObject();
            data.put("lat",loc.getLatitude());
            data.put("lon",loc.getLongitude());
            data.put("accuracy",loc.hasAccuracy()?loc.getAccuracy():999);
            data.put("provider",loc.getProvider());
            data.put("time",loc.getTime());
            String payload=data.toString();
            runOnUiThread(()->{
                if(web!=null)web.evaluateJavascript("window.CapriNativeLocation && window.CapriNativeLocation("+payload+");",null);
            });
        }catch(Exception ignore){}
    }

    private void error(String msg){
        runOnUiThread(()->{
            if(web!=null)web.evaluateJavascript("window.CapriNativeError && window.CapriNativeError("+JSONObject.quote(msg)+");",null);
        });
    }

    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code==REQUEST_SAVE && result==RESULT_OK && data!=null && data.getData()!=null && fileToSave!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new java.io.IOException("Fichier inaccessible");
                out.write(fileToSave);
            }catch(Exception e){error("Échec de sauvegarde : "+e.getMessage());}
        }
        if(code==REQUEST_SAVE)fileToSave=null;
    }

    @Override public void onBackPressed() {
        if(web!=null) web.evaluateJavascript("window.CapriHandleBack && window.CapriHandleBack();",null);
        else super.onBackPressed();
    }

    @Override protected void onDestroy(){
        try{if(manager!=null)manager.removeUpdates(this);}catch(Exception ignore){}
        if(web!=null){web.removeJavascriptInterface("CapriAndroid");web.destroy();web=null;}
        super.onDestroy();
    }
}
