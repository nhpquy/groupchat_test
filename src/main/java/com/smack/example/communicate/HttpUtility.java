package com.smack.example.communicate;

import com.google.gson.JsonObject;
import com.smack.example.model.HttpRes;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpUtility {

    private static HttpClient client = HttpClientBuilder.create().build();

    /**
     * Send get request to URL and release HTTP connection after finish get data
     * response
     *
     * @param url : URL to send get request
     * @return String represents data response from get request
     */
    public static String sendGet(String url) {
        String result = "";
        HttpGet getRequest = new HttpGet(url);
        try {
            getRequest.addHeader("accept", "application/json;charset=utf-8");

            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
            getRequest.setConfig(requestConfig);
            HttpResponse response = client.execute(getRequest);
            int responseCode = response.getStatusLine().getStatusCode();

            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString()
                        + " and request url : " + url);
                return getStringFromResponseBody(response);
            }

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader((response.getEntity().getContent())));

            String output;
            StringBuilder stringBuilder = new StringBuilder();
            while ((output = bufferedReader.readLine()) != null) {
                stringBuilder.append(output);
            }
            result = stringBuilder.toString();
            bufferedReader.close();

        } catch (Exception e) {
            // e.printStackTrace();
        } finally {
            getRequest.abort();
            getRequest.releaseConnection();
        }
        return result;

    }

    /**
     * Send get request to authorize link and get data response
     *
     * @param url : URL to send get request
     * @return String represents data response from authorize request
     */
    public static String sendAuthorizeRequest(String url, String authorizeData) {
        String result = "";
        HttpGet getRequest = new HttpGet(url);
        try {
            getRequest.addHeader("accept", "application/json");
            getRequest.addHeader("Authorization", authorizeData);

            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
            getRequest.setConfig(requestConfig);
            HttpResponse response = client.execute(getRequest);

            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString()
                        + " and request url : " + url);
                return null;
            }

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader((response.getEntity().getContent())));

            String output;
            StringBuilder stringBuilder = new StringBuilder();
            while ((output = bufferedReader.readLine()) != null) {
                stringBuilder.append(output);
            }
            result = stringBuilder.toString();
            bufferedReader.close();

        } catch (Exception e) {
            // e.printStackTrace();
        } finally {
            getRequest.abort();
            getRequest.releaseConnection();
        }
        return result;

    }

    /**
     * Send post request with data to URL
     *
     * @param url  : URL to send post request
     * @param data : data to send with post request
     * @return String represents data response from post request
     */
    public static HttpRes sendPostWithAuthorizeData(String url, String data, String authorizeData) {
        String result = "";
        HttpPost post = new HttpPost(url);
        int responseCode = 200;
        try {
            StringEntity input = new StringEntity(data, "UTF-8");
            input.setContentType("application/json;charset=utf-8");
            post.setEntity(input);
            post.setHeader("Connection", "keep-alive");
            post.setHeader("Authorization", "Bearer " + authorizeData);
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
            post.setConfig(requestConfig);
            HttpResponse response = client.execute(post);
            responseCode = response.getStatusLine().getStatusCode();
            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString());
                result = getStringFromResponseBody(response);
                return new HttpRes(responseCode, result);
            }
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));
            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            result = stringBuffer.toString();
            bufferedReader.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            post.abort();
            post.releaseConnection();
        }
        return new HttpRes(responseCode, result);
    }

    /**
     * Send post request with data to URL
     *
     * @param url  : URL to send post request
     * @param data : data to send with post request
     * @return String represents data response from post request
     */
    public static String sendPost(String url, String data) throws Exception {
        String result = "";
        HttpPost post = new HttpPost(url);
        try {
            StringEntity input = new StringEntity(data, "UTF-8");
            input.setContentType("application/json;charset=utf-8");
            post.setEntity(input);
            post.setHeader("Connection", "keep-alive");
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
            post.setConfig(requestConfig);
            HttpResponse response = client.execute(post);
            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString()
                        + " and request url : " + url);
                return null;
            }
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));
            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            result = stringBuffer.toString();
            bufferedReader.close();

        } catch (Exception e) {
            // e.printStackTrace();
            JsonObject object = new JsonObject();
            object.addProperty("error", e.getMessage());
            return GsonSingleton.getInstance().toJson(object);
        } finally {
            post.abort();
            post.releaseConnection();
        }
        return result;
    }

    /**
     * Send post request with data to URL
     *
     * @param url  : URL to send post request
     * @param data : data to send with post request
     * @return String represents data response from post request
     */
    public static HttpRes sendPost(String url, List<Header> headers, String data) throws Exception {
        String result = "";
        int responseCode = 200;
        HttpPost post = new HttpPost(url);
        try {
            StringEntity input = new StringEntity(data, "UTF-8");
            input.setContentType("application/json;charset=utf-8");
            post.setEntity(input);
            if (headers != null && headers.size() > 0) {
                for (Header header : headers) {
                    post.setHeader(header);
                }
            }
            post.setHeader("Connection", "keep-alive");

            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
            post.setConfig(requestConfig);

            HttpResponse response = client.execute(post);
            responseCode = response.getStatusLine().getStatusCode();
            //
            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString()
                        + " and request url : " + url);
                return new HttpRes(responseCode, getStringFromResponseBody(response));
            }

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));
            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            result = stringBuffer.toString();
            bufferedReader.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            post.abort();
            post.releaseConnection();
        }
        return new HttpRes(responseCode, result);
    }

    /**
     * Send post request with data to URL
     *
     * @param url  : URL to send post request
     * @param data : data to send with post request
     * @return String represents data response from post request
     */
    public static HttpRes sendPut(String url, List<Header> headers, String data) throws Exception {
        String result = "";
        int responseCode = 200;
        HttpPut post = new HttpPut(url);
        try {
            StringEntity input = new StringEntity(data, "UTF-8");
            input.setContentType("application/json;charset=utf-8");
            post.setEntity(input);

            for (Header header : headers) {
                post.setHeader(header);
            }
            post.setHeader("Connection", "keep-alive");

            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
            post.setConfig(requestConfig);

            HttpResponse response = client.execute(post);
            responseCode = response.getStatusLine().getStatusCode();
            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString()
                        + " and request url : " + url);
                return new HttpRes(responseCode, getStringFromResponseBody(response));
            }
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));
            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            result = stringBuffer.toString();
            bufferedReader.close();

        } catch (Exception e) {
            // e.printStackTrace();
        } finally {
            post.abort();
            post.releaseConnection();
        }
        return new HttpRes(responseCode, result);
    }

    /**
     * Send post request with data to URL
     *
     * @param url  : URL to send post request
     * @param data : data to send with post request
     * @return String represents data response from post request
     */
    public static HttpRes sendPostRaw(String url, List<Header> headers, String data) throws Exception {
        String result = "";
        int responseCode = 200;
        HttpPost post = new HttpPost(url);
        try {
            StringEntity input = new StringEntity(data, "UTF-8");
            post.setEntity(input);

            for (Header header : headers) {
                post.setHeader(header);
            }
            post.setHeader("Connection", "keep-alive");

            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
            post.setConfig(requestConfig);

            HttpResponse response = client.execute(post);
            responseCode = response.getStatusLine().getStatusCode();
            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString()
                        + " and request url : " + url);
                return new HttpRes(responseCode, getStringFromResponseBody(response));
            }
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));
            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            result = stringBuffer.toString();
            bufferedReader.close();

        } catch (Exception e) {
            // e.printStackTrace();
        } finally {
            post.abort();
            post.releaseConnection();
        }
        return new HttpRes(responseCode, result);
    }

    /**
     * Send get request to URL and release HTTP connection after finish get data
     * response
     *
     * @param url : URL to send get request
     * @return String represents data response from get request
     */
    public static HttpRes sendGet(String url, List<Header> headers) {
        String result = "";
        int responseCode = 200;

        HttpGet getRequest = new HttpGet(url);
        try {
            getRequest.addHeader("accept", "application/json;charset=utf-8");

            for (Header header : headers) {
                getRequest.setHeader(header);
            }

            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
            getRequest.setConfig(requestConfig);
            HttpResponse response = client.execute(getRequest);
            responseCode = response.getStatusLine().getStatusCode();
            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString()
                        + " and request url : " + url);
                return new HttpRes(responseCode, getStringFromResponseBody(response));
            }

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader((response.getEntity().getContent())));

            String output;
            StringBuilder stringBuilder = new StringBuilder();
            while ((output = bufferedReader.readLine()) != null) {
                stringBuilder.append(output);
            }
            result = stringBuilder.toString();
            bufferedReader.close();

        } catch (Exception e) {
            // e.printStackTrace();
        } finally {
            getRequest.abort();
            getRequest.releaseConnection();
        }
        return new HttpRes(responseCode, result);
    }

    public static HttpRes sendPostForSendingSMS(String url, List<Header> headers, List<NameValuePair> data)
            throws Exception {
        String result = "";
        int responseCode = 200;
        HttpPost post = new HttpPost(url);
        try {
            UrlEncodedFormEntity input = new UrlEncodedFormEntity(data);
            input.setContentType("application/x-www-form-urlencoded;charset=utf-8");
            post.setEntity(input);

            for (Header header : headers) {
                post.setHeader(header);
            }
            post.setHeader("Connection", "keep-alive");

            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000)
                    .setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
            post.setConfig(requestConfig);

            HttpResponse response = client.execute(post);
            responseCode = response.getStatusLine().getStatusCode();
            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString()
                        + " and request url : " + url);
                return new HttpRes(responseCode, getStringFromResponseBody(response));
            }

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));
            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            result = stringBuffer.toString();
            bufferedReader.close();

        } catch (Exception e) {
            // e.printStackTrace();
        } finally {
            post.abort();
            post.releaseConnection();
        }
        return new HttpRes(responseCode, result);
    }

    public static String sendGetAndGetRedirectURL(String url) {
        String result = "";
        HttpClientContext context = HttpClientContext.create();
        HttpGet getRequest = new HttpGet(url);

        try {
            getRequest.addHeader("accept", "application/json;charset=utf-8");

            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(120000)
                    .setConnectionRequestTimeout(120000).setSocketTimeout(120000).build();
            getRequest.setConfig(requestConfig);
            HttpResponse response = client.execute(getRequest, context);
            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode >= 300) {
                System.out.println("Error occurred. Data response from server : " + response.toString()
                        + " and request url : " + url);
                return getStringFromResponseBody(response);
            }

            HttpHost target = context.getTargetHost();
            List<URI> redirectLocations = context.getRedirectLocations();
            URI location = URIUtils.resolve(getRequest.getURI(), target, redirectLocations);
            result = location.toASCIIString();
            System.out.println("Redirect URL : " + result);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            getRequest.abort();
            getRequest.releaseConnection();
        }
        return result;

    }

    public static String getStringFromResponseBody(HttpResponse response) {
        String result = null;
        try {

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));
            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            result = stringBuffer.toString();
            bufferedReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

}
