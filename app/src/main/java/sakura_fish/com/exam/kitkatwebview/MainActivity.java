package sakura_fish.com.exam.kitkatwebview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {
    protected final int SELECT_PICTURE = 1;
    private Context mContext;
    private WebView mWebView;
    private boolean isImageSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        isImageSelected = false;
        mWebView = (WebView) findViewById(R.id.webview);
        settingWebView();
        mWebView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        if (mWebView != null) {
            mWebView.onResume();
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWebView != null) {
            mWebView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.setWebChromeClient(null);
            mWebView.setWebViewClient(null);
            mWebView.destroy();
            mWebView = null;
        }
        mContext = null;

        super.onDestroy();
    }

    @SuppressLint("SetJavaScriptEnabled")
    protected void settingWebView() {

        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setAllowFileAccess(true);
        mWebView.addJavascriptInterface(new WebAppInterface(), "Android");

        // openFileChooser not called on Android 4.4
//        mWebView.setWebChromeClient(new WebChromeClient() {
//            @SuppressWarnings("unused")
//            public void openFileChooser(ValueCallback<Uri> uploadMsg) {
//                selectImage();
//            }
//
//            @SuppressWarnings("unused")
//            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
//                selectImage();
//            }
//
//            // For Android 4.1
//            @SuppressWarnings("unused")
//            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
//                selectImage();
//            }
//
//            // Android 5.0 +
//            @Override
//            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
//                selectImage();
//                return true;
//            }
//        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(MainActivity.class.getSimpleName(), "requestCode:" + requestCode + " resultCode:" + resultCode);
        if (requestCode == SELECT_PICTURE && resultCode == Activity.RESULT_OK) {
            Uri selectedImage = data.getData();

            setFileUriToWebView(selectedImage.toString());

            InputStream input = null;
            try {
                input = mContext.getContentResolver().openInputStream(data.getData());
                if (input != null) {
                    // create image cache file
                    isImageSelected = true;
                    createPickCache(data.getData());
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void setFileUriToWebView(String uriString) {
        mWebView.loadUrl("javascript:setFileUri('" + "uri : " + uriString + "')");
    }

    void checkStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            chooseImage();
            return;
        }
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            MainActivityPermissionsDispatcher.chooseImageWithCheck(MainActivity.this);
        } else {
            chooseImage();
        }
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE})
    void chooseImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "File Chooser"), SELECT_PICTURE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(MainActivity.this, requestCode, grantResults);
    }

    private boolean createPickCache(@NonNull final Uri uri) {
        try {
            InputStream in = null;
            in = mContext.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            in.close();
            if (bitmap == null) {
                Log.e(MainActivity.class.getSimpleName(), "bitmap is null!");
                return false;
            }

            // サーバー送信用に画像を圧縮してテンポラリファイルに保存する
            float maxImageSize = 1500;
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            try {
                float ratio = Math.min(
                        maxImageSize / bitmap.getWidth(),
                        maxImageSize / bitmap.getHeight());
                int width = Math.round(ratio * bitmap.getWidth());
                int height = Math.round(ratio * bitmap.getHeight());

                Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
                newBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream);

                byte[] byteArray = stream.toByteArray();

                File file = new File(mContext.getCacheDir() + "cache.jpg");
                if (file.exists()) file.delete();
                FileOutputStream fo = null;
                fo = new FileOutputStream(file);
                fo.write(byteArray);
                fo.flush();
                fo.close();
                newBitmap.recycle();
                return true;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                bitmap.recycle();
            }
            return false;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void sendImageToServer() {
        File file = new File(mContext.getCacheDir() + "cache.jpg");
        if (!file.exists()) {
            throw new IllegalStateException("No cache file!");
        }

        // TODO ここにサーバーに送信する処理を書く
        Toast.makeText(mContext, "TODO: 画像送信しました!", Toast.LENGTH_LONG).show();
        isImageSelected = false;
        setFileUriToWebView("");
    }

    // JavascriptInterface
    class WebAppInterface {

        @JavascriptInterface
        public void openGallary() {
            checkStoragePermission();
        }

        @JavascriptInterface
        public void uploadImage() {
            if (isImageSelected) {
                sendImageToServer();
            } else {
                Toast.makeText(mContext, "You need to select image!", Toast.LENGTH_LONG).show();
            }
        }
    }
}
