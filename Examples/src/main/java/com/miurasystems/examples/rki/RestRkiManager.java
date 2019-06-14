/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.examples.rki;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

class TestHostException extends Exception {

    private static final long serialVersionUID = 3707910022001672874L;

    public TestHostException(String message) {
        super(message);
    }

    public TestHostException(String message, Throwable cause) {
        super(message, cause);
    }

    public TestHostException(Throwable cause) {
        super(cause);
    }
}


final class RestRkiManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestRkiManager.class);
    private static final Charset UTF_8 = Charset.forName("utf-8");

    // AWS live instance
    private static final String BASE_URL =
        "http://ec2-54-203-237-161.us-west-2.compute.amazonaws.com:8080/"
            + "rki-host-0.0.2-SNAPSHOT/"
            + "keyinject/";
    // Local PC address used when debugging the web service
    // private static final String BASE_URL = "http://192.168.3.68:8080/keyinject";
    // Debug value - This site can be used to inspect the message sent from the App*/
    // private static final String BASE_URL = "http://requestb.in/s9rfnts9";

    private RestRkiManager() {
    }

    public static HostRkiResponse postRkiInitRequest(HostRkiCommand command)
        throws TestHostException {
        LOGGER.trace("postRkiInitRequest {}", command);
        try {
            return makeHTTPPostRequest(BASE_URL, command.toJson());
        } catch (IOException e) {
            throw new TestHostException(e);
        } catch (JSONException e) {
            throw new TestHostException(e);
        }
    }

    private static HostRkiResponse makeHTTPPostRequest(
        String baseURL,
        JSONObject rkiPost
    ) throws IOException, JSONException, TestHostException {

        LOGGER.trace("makeHTTPPostRequest({}, {})", baseURL, rkiPost);

        URL url = new URL(baseURL);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn == null) {
            throw new TestHostException("makeHTTPPostRequest: No connection?");
        }
        conn.setConnectTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");

        LOGGER.trace("about to connect to URL");
        conn.connect();
        try {
            writeHttpPostRequest(conn, rkiPost);
            return readHttpPostRequest(conn);
        } finally {
            conn.disconnect();
        }
    }

    private static void writeHttpPostRequest(
        HttpURLConnection conn,
        JSONObject rkiPost
    ) throws IOException, TestHostException {
        OutputStream os = conn.getOutputStream();
        if (os == null) {
            throw new TestHostException("makeHTTPPostRequest: No output stream?");
        }
        String jsonString = rkiPost.toString();
        if (jsonString == null) {
            throw new TestHostException("makeHTTPPostRequest: Invalid JSON string");
        }
        os.write(jsonString.getBytes(UTF_8));
        os.close();

        LOGGER.trace("POST written ok");
    }

    private static HostRkiResponse readHttpPostRequest(HttpURLConnection conn)
        throws IOException, TestHostException, JSONException {

        InputStream inputStream = conn.getInputStream();
        if (inputStream == null) {
            throw new TestHostException("makeHTTPPostRequest: No input stream?");
        }
        InputStreamReader streamReader = new InputStreamReader(inputStream, UTF_8);
        BufferedReader bufferedReader = new BufferedReader(streamReader);
        try {
            StringBuilder responseStrBuilder = new StringBuilder();

            String inputStr;
            while ((inputStr = bufferedReader.readLine()) != null) {
                responseStrBuilder.append(inputStr);
            }
            String readStr = responseStrBuilder.toString();
            LOGGER.trace("Read POST response: {}", readStr);

            JSONObject serviceResponse = new JSONObject(readStr);
            LOGGER.debug("JSON response: {}", serviceResponse);
            return HostRkiResponse.valueOf(serviceResponse);
        } finally {
            bufferedReader.close();
            streamReader.close();
            inputStream.close();
        }
    }
}
