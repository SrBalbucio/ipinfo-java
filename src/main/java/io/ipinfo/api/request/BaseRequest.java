package io.ipinfo.api.request;

import com.google.gson.Gson;
import io.ipinfo.api.errors.ErrorResponseException;
import io.ipinfo.api.errors.RateLimitedException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class BaseRequest<T> {

    protected static final Gson gson = new Gson();
    private final OkHttpClient client;
    private final String token;

    protected BaseRequest(OkHttpClient client, String token) {
        this.client = client;
        this.token = token;
    }

    public abstract T handle() throws RateLimitedException;

    /**
     * Builds a HTTP Basic auth header without going through
     * {@code okhttp3.Credentials}, which depends on
     * {@code okio.ByteString.base64} (Kotlin default-arg bridge).
     * Mixing okhttp-jvm 5.x with an old okio (1.x/2.x) on the consumer's
     * classpath throws {@code NoSuchMethodError} on that call, so we encode
     * with the JDK instead. Equivalent to {@code Credentials.basic(token, "")}.
     */
    public static String basicAuth(String token) {
        String value = (token == null ? "" : token) + ":";
        return "Basic " +
            Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    public Response handleRequest(Request.Builder request)
        throws RateLimitedException {
        request
            .addHeader("Authorization", basicAuth(token))
            .addHeader("user-agent", "IPinfoClient/Java/3.5.1")
            .addHeader("Content-Type", "application/json");

        Response response;

        try {
            response = client.newCall(request.build()).execute();
        } catch (Exception e) {
            throw new ErrorResponseException(e);
        }

        // Sanity check
        if (response == null) {
            return null;
        }

        if (response.code() == 429) {
            throw new RateLimitedException();
        }

        if (!response.isSuccessful()) {
            String body = "";
            try {
                if (response.body() != null) {
                    body = response.body().string();
                }
            } catch (Exception ignored) {}
            throw new ErrorResponseException(response.code(), body);
        }

        return response;
    }
}
