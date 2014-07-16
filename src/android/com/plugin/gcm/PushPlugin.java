package com.plugin.gcm;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.Config;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

/**
 * @author awysocki
 * @author David García <david.garcia@inqbarna.com>
 */

public class PushPlugin extends CordovaPlugin {
	public static final String TAG = "PushPlugin";

	public static final String REGISTER = "register";
	public static final String UNREGISTER = "unregister";
	public static final String EXIT = "exit";

	private static final String PREFS_NAME = "push.prefs";

	private static final String PROVIDER_CLASS = "notification.provider.class";
	private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    public static final String PROPERTY_REG_ID = "registration_id";
    
    /**
     * Substitute you own sender ID here. This is the project number you got
     * from the API Console, as described in "Getting Started."
     */
    String SENDER_ID = "320440445719";


    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();

    String regid;

	
	public interface PushClientProvider {
		public void generateNotification(Context ctxt, Bundle extras);
	}
	
	public static void configureProvider(Context ctxt, Class<? extends PushClientProvider> providerClazz) {
		SharedPreferences prefs = ctxt.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROVIDER_CLASS, providerClazz.getName());
		editor.commit();
	}
	
	public static PushClientProvider getProvider(Context ctxt) {
		SharedPreferences prefs = ctxt.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		if (!prefs.contains(PROVIDER_CLASS))
			return null;
		
		String className = prefs.getString(PROVIDER_CLASS, "");
		try {
			Class<?> clazz = Class.forName(className);
			PushClientProvider provider = (PushClientProvider) clazz.newInstance();
			return provider;
		} catch (ClassNotFoundException e) {
			Log.e(TAG, "Provider class " + className + " not found");
			return null;
		} catch (InstantiationException e) {
			Log.e(TAG, "Invalid class instantiation. Make sure it has a public default constructor.", e);
			return null;
		} catch (IllegalAccessException e) {
			Log.e(TAG, "Invalid class instantiation. Make sure it has a public default constructor.", e);
			return null;
		}
		
	}
	
	@Override
	public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
		Log.v(TAG, "execute: action=" + action);
		if (REGISTER.equals(action)) {
			Log.v(TAG, "execute: data=" + args.toString());

			try {
				JSONObject jo = args.getJSONObject(0);

				Log.v(TAG, "execute: jo=" + jo.toString());

				SENDER_ID = (String) jo.get("senderID");

				Log.v(TAG, "execute: senderID=" + SENDER_ID);

				gcmOnRegister(callbackContext);
			} catch (JSONException e) {
				Log.e(TAG, "execute: Got JSON Exception " + e.getMessage(), e);
				callbackContext.error(e.getMessage());
				return false;
			}
			
			return true;
		} else if (UNREGISTER.equals(action)) {
			return false;
		}
		return super.execute(action, args, callbackContext);
	}
	
	/*
	@Override
	public boolean execute(String action, JSONArray data, CallbackContext callbackContext) {

		boolean result = false;

		Log.v(TAG, "execute: action=" + action);

		if (REGISTER.equals(action)) {

			Log.v(TAG, "execute: data=" + data.toString());

			try {
				JSONObject jo = data.getJSONObject(0);

				gWebView = this.webView;
				Log.v(TAG, "execute: jo=" + jo.toString());

				gECB = (String) jo.get("ecb");
				gSenderID = (String) jo.get("senderID");

				Log.v(TAG, "execute: ECB=" + gECB + " senderID=" + gSenderID);

				GCMRegistrar.register(getApplicationContext(), gSenderID);
				result = true;
				callbackContext.success();
			} catch (JSONException e) {
				Log.e(TAG, "execute: Got JSON Exception " + e.getMessage());
				result = false;
				callbackContext.error(e.getMessage());
			}

			if ( gCachedExtras != null) {
				Log.v(TAG, "sending cached extras");
				sendExtras(gCachedExtras);
				gCachedExtras = null;
			}

		} else if (UNREGISTER.equals(action)) {

			//GCMRegistrar.unregister(getApplicationContext());

			Log.v(TAG, "UNREGISTER");
			result = true;
			callbackContext.success();
		} else {
			result = false;
			Log.e(TAG, "Invalid action : " + action);
			callbackContext.error("Invalid action : " + action);
		}

		return result;
	}
	*/
    private void gcmOnRegister(CallbackContext callbacks) {
		// Check device for Play Services APK. If check succeeds, proceed with GCM registration.
    	
    	Activity act = cordova.getActivity();
    	
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(act);
            regid = getRegistrationId(act);

            if (regid.isEmpty()) {
                registerInBackground(callbacks);
            } else {
            	callbacks.success(makeCallbackSuccessArgs());
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
	}

    private JSONObject makeCallbackSuccessArgs() {
    	if (TextUtils.isEmpty(regid))
    		throw new IllegalStateException("You need to make sure to call this when regid is valid");
    	
		JSONObject obj = new JSONObject();
		try {
			obj.put(PROPERTY_REG_ID, regid);
			return obj;
		} catch (JSONException e) {
			Log.e(TAG, "Error creating JSON Arguments: " + e.getMessage(), e);
			return null;
		}
	}

	@Override
    public void onResume(boolean multitasking) {
    	super.onResume(multitasking);
    	checkPlayServices();
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
    	Activity act = cordova.getActivity();
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(act);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, act,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                act.finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    /**
     * Gets the current registration ID for application on GCM service, if there is one.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     * @param callbacks TODO
     */
    private void registerInBackground(final CallbackContext callbacks) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                Activity act = cordova.getActivity();
                if (act == null) {
                	Log.i(TAG, "Trying to register when activity is not available");
                	return "Activity is not ready";
                }
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(act);
                    }
                    regid = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.
                    sendRegistrationIdToBackend(callbacks);

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Persist the regID - no need to register again.
                    storeRegistrationId(act, regid);
                    return null;
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                Log.e(TAG, msg);
                return msg;
            }

            @Override
            protected void onPostExecute(String v) {
            	if (!TextUtils.isEmpty(v)) {
            		callbacks.error(v);
            	}
            }
        }.execute(null, null, null);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGcmPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return context.getSharedPreferences("myprefs.dat",
                Context.MODE_PRIVATE);
    }
    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP or CCS to send
     * messages to your app. Not needed for this demo since the device sends upstream messages
     * to a server that echoes back the message using the 'from' address in the message.
     * @param callbacks 
     */
    private void sendRegistrationIdToBackend(CallbackContext callbacks) {
    	Log.e(TAG, "Your registration ID is: " + regid);
    	callbacks.success(makeCallbackSuccessArgs());
    }
}
