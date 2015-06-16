package io.scrollback.app;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.app.Activity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static android.webkit.WebSettings.LOAD_DEFAULT;


public class MainActivity extends Activity {

    public static final String ORIGIN = Constants.domain;
    public static final String INDEX = Constants.protocol + "//" + ORIGIN;

    public static final String HOME = INDEX + "/me";

    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";

    private static final int REQ_SIGN_IN_REQUIRED = 55664;

    private static final int SOME_REQUEST_CODE = 12323;

    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private final static int FB_REQUEST_CODE_OPEN = 14141;

    private final static int FB_REQUEST_CODE_PERM = 14142;

    private static final String TAG = "android_wrapper";

    private String accountName;
    private String accessToken;

    GoogleCloudMessaging gcm;
    String regid;

    private WebView mWebView;
    private ProgressBar mProgressBar;
    private TextView mLoadError;

    private boolean inProgress = false;

    ProgressDialog dialog;

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArr;

    private final static int REQUEST_SELECT_FILE_LEGACY = 19264;
    private final static int REQUEST_SELECT_FILE = 19275;

    CallbackManager callbackManager;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mWebView = (WebView) findViewById(R.id.main_webview);

        mProgressBar = (ProgressBar) findViewById(R.id.main_pgbar);
        mLoadError = (TextView) findViewById(R.id.main_loaderror);

        // Enable debugging in webview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }

        // Check device for Play Services APK. If check succeeds, proceed with
        // GCM registration.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(getApplicationContext());
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }

        mWebView.setWebViewClient(mWebViewClient);

        mWebView.setWebChromeClient(new WebChromeClient() {
            // For Android < 3.0
            @SuppressWarnings("unused")
            public void openFileChooser(ValueCallback<Uri> uploadMsg) {
                mUploadMessage = uploadMsg;

                Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");

                MainActivity.this.startActivityForResult(Intent.createChooser(i, getString(R.string.select_file)), REQUEST_SELECT_FILE_LEGACY);

            }

            // For Android 3.0+
            @SuppressWarnings("unused")
            public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
                mUploadMessage = uploadMsg;

                Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(acceptType);

                MainActivity.this.startActivityForResult(Intent.createChooser(i, getString(R.string.select_file)), REQUEST_SELECT_FILE_LEGACY);
            }

            // For Android 4.1+
            @SuppressWarnings("unused")
            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                mUploadMessage = uploadMsg;

                Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(acceptType);

                MainActivity.this.startActivityForResult(Intent.createChooser(i, getString(R.string.select_file)), REQUEST_SELECT_FILE_LEGACY);
            }

            // For Android 5.0+
            @SuppressLint("NewApi")
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadMessageArr != null) {
                    mUploadMessageArr.onReceiveValue(null);
                    mUploadMessageArr = null;
                }

                mUploadMessageArr = filePathCallback;

                Intent intent = fileChooserParams.createIntent();

                try {
                    MainActivity.this.startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (ActivityNotFoundException e) {
                    mUploadMessageArr = null;

                    Toast.makeText(MainActivity.this, getString(R.string.file_chooser_error), Toast.LENGTH_LONG).show();

                    return false;
                }

                return true;
            }
        });

        WebSettings mWebSettings = mWebView.getSettings();

        String appCachePath = getApplicationContext().getCacheDir().getAbsolutePath();

        mWebSettings.setJavaScriptEnabled(true);
        mWebSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        mWebSettings.setSupportZoom(false);
        mWebSettings.setSaveFormData(true);
        mWebSettings.setDomStorageEnabled(true);
        mWebSettings.setAppCacheEnabled(true);
        mWebSettings.setAppCachePath(appCachePath);
        mWebSettings.setAllowFileAccess(true);
        mWebSettings.setCacheMode(LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            String databasePath = getApplicationContext().getDir("databases", Context.MODE_PRIVATE).getPath();

            mWebSettings.setDatabaseEnabled(true);
            mWebSettings.setDatabasePath(databasePath);
        }

        mWebView.addJavascriptInterface(new ScrollbackInterface(getApplicationContext()) {

            @SuppressWarnings("unused")
            @JavascriptInterface
            public boolean isFileUploadAvailable(final boolean needsCorrectMimeType) {
                if (Build.VERSION.SDK_INT == 19) {
                    final String platformVersion = (Build.VERSION.RELEASE == null) ? "" : Build.VERSION.RELEASE;

                    return !needsCorrectMimeType && (platformVersion.startsWith("4.4.3") || platformVersion.startsWith("4.4.4"));
                } else {
                    return true;
                }
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public boolean isFileUploadAvailable() {
                return isFileUploadAvailable(false);
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void setStatusBarColor() {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            try {
                                getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark));
                            } catch (Exception e) {
                                Log.d(TAG, "Failed to set statusbar color " + e);
                            }
                        }
                    }
                });
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void setStatusBarColor(final String color) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            try {
                                getWindow().setStatusBarColor(Color.parseColor(color));
                            } catch (Exception e) {
                                Log.d(TAG, "Failed to set statusbar color to " + color + " " + e);
                            }
                        }
                    }
                });
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void shareItem(String title, String content) {
                Intent sharingIntent = new Intent(Intent.ACTION_SEND);

                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, content);

                startActivity(Intent.createChooser(sharingIntent, title));
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void copyToClipboard(String label, String text) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(label, text);

                clipboard.setPrimaryClip(clip);

                Toast toast = Toast.makeText(MainActivity.this, getString(R.string.clipboard_success), Toast.LENGTH_SHORT);
                toast.show();
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void onFinishedLoading() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                    }
                });
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void googleLogin() {
                Intent intent = AccountPicker.newChooseAccountIntent(null, null, new String[]{"com.google"},
                        false, null, null, null, null);
                startActivityForResult(intent, SOME_REQUEST_CODE);
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void facebookLogin() {
                // Get a handler that can be used to post to the main thread
                Handler mainHandler = new Handler(MainActivity.this.getMainLooper());

                Runnable myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        doFacebookLogin();
                    }
                };
                mainHandler.post(myRunnable);
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void registerGCM() {
                registerBackground();
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void unregisterGCM() {
                unRegisterBackground();

            }
        }, "Android");

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri uri = intent.getData();

        if (intent.hasExtra("scrollback_path")) {
            mWebView.loadUrl(INDEX + getIntent().getStringExtra("scrollback_path"));
        } else if (Intent.ACTION_VIEW.equals(action) && uri != null) {
            final String URL = uri.toString();

            mWebView.loadUrl(URL);
        } else {
            mWebView.loadUrl(HOME);
        }

        mLoadError.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mWebView.loadUrl(mWebView.getUrl());

                mLoadError.setVisibility(View.GONE);

                showLoading();
            }
        });

        showLoading();

        FacebookSdk.sdkInitialize(getApplicationContext());
        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager, loginCallback);
    }

    void doFacebookLogin() {

        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("public_profile", "email"));

    }

    void hideLoading() {
        mProgressBar.setVisibility(View.GONE);
        mWebView.setVisibility(View.VISIBLE);
    }

    void showLoading() {
        mProgressBar.setVisibility(View.VISIBLE);
        mWebView.setVisibility(View.GONE);
    }

    void emitGoogleLoginEvent(String token) {
        Log.d("emitGoogleLoginEvent", "email:"+accountName+" token:"+token);

        mWebView.loadUrl("javascript:window.dispatchEvent(new CustomEvent('login', { detail :{'provider': 'google', 'email': '" + accountName + "', 'token': '" + token + "'} }))");
    }

    void emitFacebookLoginEvent(String email, String token) {
        Log.d("emitFacebookLoginEvent", "email:"+email+" token:"+token);

        mWebView.loadUrl("javascript:window.dispatchEvent(new CustomEvent('login', { detail :{'provider': 'facebook', 'email': '" + email + "', 'token': '" + token + "'} }))");

    }

    void emitGCMRegisterEvent(String regid, String uuid, String model) {
        Log.d("emitGCMRegisterEvent", "uuid:"+uuid+" regid:"+regid);

        mWebView.loadUrl("javascript:window.dispatchEvent(new CustomEvent('gcm_register', { detail :{'regId': '" + regid + "', 'uuid': '" + uuid + "', 'model': '" + model + "'} }))");
    }

    void emitGCMUnregisterEvent(String uuid) {
        mWebView.loadUrl("javascript:window.dispatchEvent(new CustomEvent('gcm_unregister', { detail :{'uuid': '" + uuid + "'} }))");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Check if the key event was the Back button and if there's history
            if (mWebView.getUrl().equals(HOME) || !mWebView.canGoBack()) {
                finish();
            } else if (mWebView.canGoBack()) {
                mWebView.goBack();
            }

            return true;
        }

        // Bubble up to the default system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event);
    }

    private WebViewClient mWebViewClient = new WebViewClient() {

        @SuppressWarnings("unused")
        public boolean onConsoleMessage(ConsoleMessage cm) {
            Log.d(getString(R.string.app_name), cm.message() + " -- From line "
                    + cm.lineNumber() + " of "
                    + cm.sourceId() );

            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);

            if (uri.getAuthority().equals(ORIGIN)) {
                // This is my web site, so do not override; let my WebView load the page
                return false;


            } else {
                Log.d(TAG, uri.getAuthority() + " is not " + ORIGIN);

                // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);

                return true;
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            mLoadError.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mWebView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mWebView.destroy();
    }

    @SuppressLint("NewApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SELECT_FILE_LEGACY) {
            if (mUploadMessage == null) return;

            Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();

            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }

        else if (requestCode == REQUEST_SELECT_FILE) {
            if (mUploadMessageArr == null) return;

            mUploadMessageArr.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            mUploadMessageArr = null;
        }

        else if (requestCode == SOME_REQUEST_CODE && resultCode == RESULT_OK) {
            accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

            new RetrieveGoogleTokenTask().execute(accountName);
        }

        else if (requestCode == REQ_SIGN_IN_REQUIRED && resultCode == RESULT_OK) {
            // We had to sign in - now we can finish off the token request.
            new RetrieveGoogleTokenTask().execute(accountName);
        }

        else {
            callbackManager.onActivityResult(requestCode, resultCode, data);

        }
    }

    FacebookCallback<LoginResult> loginCallback = new FacebookCallback<LoginResult>() {
        @Override
        public void onSuccess(final LoginResult loginResult) {

            if(loginResult.getRecentlyGrantedPermissions().contains("email")) {
                GraphRequest.newMeRequest(
                        loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                            @Override
                            public void onCompleted(JSONObject me, GraphResponse response) {
                                if (response.getError() != null) {
                                    // handle error
                                } else {
                                    String email = me.optString("email");
                                    emitFacebookLoginEvent(email, loginResult.getAccessToken().getToken());
                                    // send email and id to your web server
                                }
                            }
                        }).executeAsync();
            }
            else {
                Toast.makeText(MainActivity.this, "Something is wrong", Toast.LENGTH_SHORT).show();
            }



        }

        @Override
        public void onCancel() {

        }

        @Override
        public void onError(FacebookException e) {

        }
    };

//    Session.StatusCallback statusCallback = new Session.StatusCallback() {
//        @Override
//        public void call(Session session, SessionState state, Exception exception) {
//
//            Log.d("SessionState", state.toString());
//            processSessionStatus(session, state, exception);
//        }
//    };
//
//    public void processSessionStatus(final Session session, SessionState state, Exception exception) {
//        if (exception!=null) {
//            Log.d("FBException", exception.getMessage());
//        }
//
//        Log.d("processSessionState", "State: " + state.toString());
//
//        if (session != null && session.isOpened()) {
//            if (session.getPermissions().contains("email")) {
//                session.getAccessToken();
//
//                //Show Progress Dialog
//                dialog = new ProgressDialog(MainActivity.this);
//
//                dialog.setMessage(getString(R.string.fb_signing_in));
//                dialog.show();
//
//                Request.newMeRequest(session, new Request.GraphUserCallback() {
//                    @Override
//                    public void onCompleted(GraphUser user, Response response) {
//
//                        if (dialog != null && dialog.isShowing()) {
//                            dialog.dismiss();
//                        }
//
//                        if (user != null) {
//                            GraphObject graphObject = response.getGraphObject();
//                            Map<String, Object> responseMap = graphObject.asMap();
//
//                            Log.i("FbLogin", "Response Map KeySet - " + responseMap.keySet());
//
//                            // String fb_id = user.getId();
//                            // String name = (String) responseMap.get("name");
//
//                            String email;
//
//                            if (responseMap.get("email") != null) {
//                                email = responseMap.get("email").toString();
//                                emitFacebookLoginEvent(email, session.getAccessToken());
//                            } else {
//                                // Clear all session info & ask user to login again
//                                Session session = Session.getActiveSession();
//
//                                if (session != null) {
//                                    session.closeAndClearTokenInformation();
//                                }
//                            }
//                        }
//                    }
//                }).executeAsync();
//
//            } else {
//                LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("public_profile", "user_friends"));
//                session.requestNewReadPermissions(new Session.NewPermissionsRequest(this, permissions).setRequestCode(FB_REQUEST_CODE_PERM));
//            }
//        }
//    }


    private class RetrieveGoogleTokenTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(MainActivity.this);
            dialog.setMessage(getString(R.string.google_signing_in));
            dialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            String accountName = params[0];
            String scopes = "oauth2:profile email";
            String token = null;

            try {
                token = GoogleAuthUtil.getToken(getApplicationContext(), accountName, scopes);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            } catch (UserRecoverableAuthException e) {
                startActivityForResult(e.getIntent(), REQ_SIGN_IN_REQUIRED);
            } catch (GoogleAuthException e) {
                Log.e(TAG, e.getMessage());
            }

            return token;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }

            if (s == null) {
                Toast.makeText(MainActivity.this, getString(R.string.requesting_permission), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, getString(R.string.signed_in), Toast.LENGTH_SHORT).show();

                emitGoogleLoginEvent(s);

                accessToken = s;
            }
        }
    }

    private class DeleteTokenTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            inProgress = true;
        }

        @Override
        protected String doInBackground(String... params) {
            accessToken = params[0];

            String result = null;

            try {
                GoogleAuthUtil.clearToken(getApplicationContext(), accessToken);

                result = "true";
            } catch (GoogleAuthException e) {
                Log.e(TAG, e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }

            return result;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            inProgress = false;

        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p/>
     * Stores the registration id, app versionCode, and expiration time in the application's
     * shared preferences.
     */
    private void registerBackground() {
        new AsyncTask<Void, Void, String>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected String doInBackground(Void... params) {
                String msg;

                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
                    }

                    regid = gcm.register(getString(R.string.gcm_sender_id));

                    msg = "Device registered, registration id=" + regid;

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Save the regid - no need to register again.
                    setRegistrationId(getApplicationContext(), regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                }

                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }

                String uuid = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                        Settings.Secure.ANDROID_ID);
                emitGCMRegisterEvent(regid, uuid, Build.MODEL);
            }
        }.execute(null, null, null);
    }


    private void unRegisterBackground() {
        new AsyncTask<Void, Void, String>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected String doInBackground(Void... params) {
                String msg = "";

                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
                    }

                    gcm.unregister();

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Save the regid - no need to register again.
                    setRegistrationId(getApplicationContext(), regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                }

                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }

                emitGCMUnregisterEvent(Build.MODEL);
            }
        }.execute(null, null, null);
    }



    /**
     * Stores the registration id, app versionCode, and expiration time in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId   registration id
     */
    private void setRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences();
        int appVersion = getAppVersion(context);

        Log.v(TAG, "Saving regId on app version " + appVersion);

        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.apply();
    }

    /**
     * Gets the current registration id for application on GCM service.
     * <p/>
     * If result is empty, the registration has failed.
     *
     * @return registration id, or empty string if the registration is not
     * complete.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences();
        final String registrationId = prefs.getString(PROPERTY_REG_ID, "");

        if (registrationId.length() == 0) {
            Log.v(TAG, "Registration not found.");

            return "";
        }

        // check if app was updated; if so, it must clear registration id to
        // avoid a race condition if GCM sends a message
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);

        if (registeredVersion != currentVersion) {
            Log.v(TAG, "App version changed or registration expired.");

            return "";
        }

        return registrationId;
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);

            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // Should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences() {
        return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");

                finish();
            }

            return false;
        }

        return true;
    }
}
