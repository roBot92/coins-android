package com.nothingmagical.coins;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;


public class MainActivity extends Activity {

    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String PREFERENCES_NAME = "Preferences";
    public static final String CONVERSION_PREFERENCES_NAME = "ConversionPreferences";
    public static final String KEY_BTC = "BTC";
    public static final String KEY_CURRENCY = "Currency";
    public static final String KEY_UPDATED_AT = "UpdatedAt";

    protected TextView mValueLabel;
    protected TextView mBtcLabel;
    protected TextView mUpdatedAtLabel;
    protected boolean mUpdating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mValueLabel = (TextView) findViewById(R.id.valueLabel);
        mBtcLabel = (TextView) findViewById(R.id.btcLabel);
        mUpdatedAtLabel = (TextView) findViewById(R.id.updatedAtLabel);

        if (isNetworkAvailable()) {
            mUpdating = true;
            GetConversionTask task = new GetConversionTask();
            task.execute();
        }
        else {
            Toast.makeText(this, "Network is unavailable.", Toast.LENGTH_LONG).show();
        }

        updateInterface();
    }

    protected void updateInterface() {
        double btc = getBtc();
        double rate = getRate();
        double value = btc * rate;

        mValueLabel.setText(String.format("$%.2f", value));
        mBtcLabel.setText("" + btc + " BTC");

        if (mUpdating) {
            mUpdatedAtLabel.setText("Updating…");
        } else {
            long timestamp = getUpdatedAtTimestamp();
            if (timestamp == 0) {
                mUpdatedAtLabel.setText("Never updated");
            } else {
                mUpdatedAtLabel.setText("Updated " + DateUtils.getRelativeTimeSpanString(this, timestamp));
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();

        return networkInfo != null && networkInfo.isConnected();
    }

    protected String getCurrencyCode() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return preferences.getString(KEY_CURRENCY, "USD");
    }

    protected void setCurrencyCode(String code) {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_CURRENCY, code);
        editor.commit();
    }

    protected double getBtc() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(preferences.getLong(KEY_BTC, Double.doubleToLongBits(2.0)));
    }

    protected void setBtc(double number) {
        SharedPreferences preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(KEY_BTC, Double.doubleToRawLongBits(number));
        editor.commit();
    }

    protected double getRate() {
        SharedPreferences preferences = getSharedPreferences(CONVERSION_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return Double.longBitsToDouble(preferences.getLong(getCurrencyCode(), Double.doubleToLongBits(0.0)));
    }

    protected long getUpdatedAtTimestamp() {
        SharedPreferences preferences = getSharedPreferences(CONVERSION_PREFERENCES_NAME, Context.MODE_PRIVATE);
        return preferences.getLong(KEY_UPDATED_AT, 0);
    }

    public class GetConversionTask extends AsyncTask<Object, Void, Object> {
        @Override
        protected Void doInBackground(Object... arg0) {
            try {
                // Connect
                String json = getJSON("https://coinbase.com/api/v1/currencies/exchange_rates", 1500);
                if (json == null) {
                    return null;
                }
                JSONObject data = new JSONObject(json);

                // Put values into map
                Iterator<String> keys = data.keys();

                SharedPreferences preferences = getSharedPreferences(CONVERSION_PREFERENCES_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();

                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.startsWith("btc_to_")) {
                        String value = data.getString(key);
                        key = key.replace("btc_to_", "").toUpperCase();
                        editor.putLong(key, Double.doubleToLongBits(Double.valueOf(value)));
                    }
                }

                editor.putLong(KEY_UPDATED_AT, System.currentTimeMillis());
                editor.commit();
            }
            catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.getClass().getSimpleName() + " - " + e.getMessage());
            }

            return null;
        }

        @Override
        protected void onPostExecute(Object _) {
            mUpdating = false;
            updateInterface();
        }

        public String getJSON(String url, int timeout) {
            try {
                URL u = new URL(url);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("Content-length", "0");
                c.setUseCaches(false);
                c.setAllowUserInteraction(false);
                c.setConnectTimeout(timeout);
                c.setReadTimeout(timeout);
                c.connect();
                int status = c.getResponseCode();

                switch (status) {
                    case 200:
                    case 201:
                        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line+"\n");
                        }
                        br.close();
                        return sb.toString();
                }

            } catch (MalformedURLException e) {
                Log.e(TAG, e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            return null;
        }
    }
}
