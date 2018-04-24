package de.yoxcu.ytdl;


import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by yoxcu on 20.03.18.
 */

public class HttpHandler {
    public static String TAG = "HttpHandler";
    public static String SERVER_ADRESS = "https://ytdl.yoxcu.de";
    public static int TIMEOUT = 10000;


    private static Response post(String path, String auth, String json) throws IOException {
        String url = SERVER_ADRESS + "/" + path;
        HttpURLConnection httpcon;
        Response result = null;
        try {

            //Connect
            httpcon = (HttpURLConnection) ((new URL(url).openConnection()));
            httpcon.setDoOutput(true);
            httpcon.setRequestProperty("Content-Type", "application/json");
            httpcon.setRequestProperty("Accept", "application/json");
            httpcon.setRequestMethod("POST");
            httpcon.setReadTimeout(TIMEOUT);
            httpcon.setConnectTimeout(TIMEOUT);
            String header = "Basic " + new String(android.util.Base64.encode(auth.getBytes(), android.util.Base64.NO_WRAP));
            httpcon.addRequestProperty("Authorization", header);
            httpcon.connect();

            //Write
            OutputStream os = httpcon.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(json);
            writer.close();
            os.close();

            int responseCode = httpcon.getResponseCode();
            if (responseCode == 200) {

                //Read
                BufferedReader br = new BufferedReader(new InputStreamReader(httpcon.getInputStream(), "UTF-8"));

                String line = null;
                StringBuilder sb = new StringBuilder();

                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }

                br.close();
                result = new Response(responseCode, sb.toString());
            } else {
                result = new Response(responseCode, null);
            }
            httpcon.disconnect();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }


    public static Response get(String path, String auth) throws IOException {
        String url = SERVER_ADRESS + "/" + path;
        HttpURLConnection httpcon;
        String result = null;
        int responseCode = -1;
        try {
            //Connect
            httpcon = (HttpURLConnection) ((new URL(url).openConnection()));
            httpcon.setRequestProperty("Content-Type", "application/json");
            httpcon.setRequestProperty("Accept", "application/json");
            httpcon.setRequestMethod("GET");
            httpcon.setReadTimeout(TIMEOUT);
            httpcon.setConnectTimeout(TIMEOUT);
            String header = "Basic " + new String(android.util.Base64.encode(auth.getBytes(), android.util.Base64.NO_WRAP));
            httpcon.addRequestProperty("Authorization", header);
            httpcon.connect();


            responseCode = httpcon.getResponseCode();
            if (responseCode == 200) {
                //Read
                BufferedReader br = new BufferedReader(new InputStreamReader(httpcon.getInputStream(), "UTF-8"));

                String line = null;
                StringBuilder sb = new StringBuilder();

                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }

                br.close();
                result = sb.toString();
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return new Response(responseCode, result);
    }

    public static Token getToken(Context context) throws IOException {
        SharedPreferences prefs = context.getSharedPreferences("de.yoxcu.ytdl", Context.MODE_PRIVATE);
        String tokenjson = prefs.getString("token", "");
        Token token = Token.fromJson(tokenjson);
        if (token != null && token.isAlive()) {
            return token;
        } else {
            Log.d(TAG, "Getting Token");
            String android_id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String username = prefs.getString("api_username", android_id);
            String pwd = prefs.getString("api_password", String.valueOf(android_id.hashCode()));
            Response response = get("token", username + ":" + pwd);
            if (response.getCode() == 200) {
                String json = response.getJson();
                if (json != null) {
                    Token newToken = Token.fromJson(json);
                    prefs.edit().putString("token", newToken.toJson()).apply();
                    return newToken;
                } else {
                    throw new IOException("Response Json is null");
                }
            } else if (response.getCode() == 401) {
                Log.d(TAG, "Creating User");
                String json = String.format("{\"username\":\"%s\",\"password\":\"%s\",\"api_key\":\"%s\"}", android_id, pwd, context.getString(R.string.api_secret_key));
                response = HttpHandler.post("users", "", json);
                if (response.getCode() == 201) {
                    return getToken(context);
                } else {
                    throw new HttpResponseException("Unhandled Response Code" + response.getCode(), response.getCode());
                }
            } else {
                throw new HttpResponseException("Unhandled Response Code" + response.getCode(), response.getCode());
            }
        }
    }

    public static String getFromServer(String path, Context context) throws IOException {
        Response response = get(path, getToken(context).getAuth());
        if (response.getCode() == 200) {
            String json = response.getJson();
            if (json != null) {
                return json;
            } else {
                throw new IOException("Response Json is null");
            }
        } else {
            throw new HttpResponseException(String.format("Unhandled response code: %s from call: %s" ,response.getCode(),path), response.getCode());
        }
    }

    public static String postToServer(String path, String json, Context context) throws IOException {
        Log.d(TAG, "Posting to " + path);
        Response response = post(path, getToken(context).getAuth(), json);
        if (response.getCode() == 200) {
            String responseJson = response.getJson();
            if (responseJson != null) {
                return responseJson;
            } else {
                throw new IOException("Response Json is null");
            }
        } else {
            throw new HttpResponseException("Unhandled Response Code" + response.getCode(), response.getCode());
        }
    }

    public static boolean haveNetworkConnection(Context context) {
        boolean haveConnectedWifi = false;
        boolean haveConnectedMobile = false;

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().equalsIgnoreCase("WIFI"))
                if (ni.isConnected())
                    haveConnectedWifi = true;
            if (ni.getTypeName().equalsIgnoreCase("MOBILE"))
                if (ni.isConnected())
                    haveConnectedMobile = true;
        }
        return haveConnectedWifi || haveConnectedMobile;
    }

    public static boolean urlIsValid(String url) {
        try {
            URL u = new URL(url);
            u.toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static class Response {
        private int code;
        private String json;

        public Response(int code, String json) {
            this.code = code;
            this.json = json;
        }

        public Response() {
        }

        public String getJson() {
            return this.json;
        }

        public void setJson(String value) {
            this.json = value;
        }

        public Integer getCode() {
            return this.code;
        }

        public void setCode(Integer value) {
            this.code = value;
        }

    }

    public static class HttpResponseException extends IOException {
        private int code = -1;

        public HttpResponseException() {
            super();
        }

        public HttpResponseException(String message, int code) {
            super(message);
            this.code = code;
        }

        public HttpResponseException(String message, Throwable cause) {
            super(message, cause);
        }

        public HttpResponseException(Throwable cause) {
            super(cause);
        }

        public int getCode() {
            return code;
        }

    }

}
