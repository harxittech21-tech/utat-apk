package in.utaekwondopro.app;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        if (webView == null) return;

        // 1. Setup Direct JavaScript Bridge for Printing
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void performPrint() {
                runOnUiThread(() -> {
                    Log.d(TAG, "Native Print Interface called");
                    try {
                        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter("Document");
                        String jobName = "U.T.A.I Document";
                        printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
                    } catch (Exception e) {
                        Log.e(TAG, "Print Error: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Print failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "AndroidPrintBridge");

        // 2. Inject JS Override on every page load
        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageLoaded(WebView webView) {
                injectPrintOverride(webView);
            }

            @Override
            public void onPageStarted(WebView webView) {
                injectPrintOverride(webView);
            }

            private void injectPrintOverride(WebView webView) {
                String js = "window.print = function() { " +
                            "  if (typeof AndroidPrintBridge !== 'undefined') { " +
                            "    AndroidPrintBridge.performPrint(); " +
                            "  } else { " +
                            "    console.error('AndroidPrintBridge not found'); " +
                            "  } " +
                            "};";
                webView.evaluateJavascript(js, null);
                Log.d(TAG, "JS Print Bridge Injected");
            }
        });

        // 3. Robust Download Handling (HTTP + Data URIs)
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                Log.d(TAG, "Download Triggered: " + url);
                
                if (url.startsWith("data:")) {
                    handleDataUri(url, mimetype);
                } else {
                    handleHttpUri(url, userAgent, contentDisposition, mimetype);
                }
            }
        });
    }

    private void handleHttpUri(String url, String userAgent, String contentDisposition, String mimetype) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimetype);
            String cookies = CookieManager.getInstance().getCookie(url);
            request.addRequestHeader("cookie", cookies);
            request.addRequestHeader("User-Agent", userAgent);
            
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            request.setTitle(fileName);
            request.setDescription("Downloading file...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "HTTP Download Error: " + e.getMessage());
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleDataUri(String url, String mimetype) {
        try {
            String base64Data = url.substring(url.indexOf(",") + 1);
            byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
            
            String extension = ".png";
            if (mimetype.contains("pdf")) extension = ".pdf";
            else if (mimetype.contains("jpg") || mimetype.contains("jpeg")) extension = ".jpg";
            
            String fileName = "U.T.A.I_" + System.currentTimeMillis() + extension;
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(path, fileName);
            
            OutputStream os = new FileOutputStream(file);
            os.write(decodedBytes);
            os.close();
            
            // Refresh Gallery/Files App
            android.media.MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            
            Toast.makeText(this, "File saved to Downloads folder", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Data URI Error: " + e.getMessage());
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show();
        }
    }
}
