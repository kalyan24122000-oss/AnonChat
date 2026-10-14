package com.anonchat.app.data.api;

import com.anonchat.app.utils.PreferenceHelper;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    private static ApiService apiService;
    private static Retrofit retrofit;

    public static synchronized ApiService getService(PreferenceHelper prefHelper) {
        if (apiService == null) {
            String baseUrl = prefHelper.getBaseUrl();
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }

            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            // In release, use Level.NONE or Level.BASIC to avoid exposing payloads
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request original = chain.request();
                        Request.Builder builder = original.newBuilder();

                        String token = prefHelper.getToken();
                        if (token != null && !token.isEmpty()) {
                            builder.header("Authorization", "Bearer " + token);
                        }
                        builder.header("Content-Type", "application/json");

                        return chain.proceed(builder.build());
                    }
                })
                .addInterceptor(loggingInterceptor)
                .build();

            retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

            apiService = retrofit.create(ApiService.class);
        }
        return apiService;
    }

    public static synchronized void reset() {
        apiService = null;
        retrofit = null;
    }
}
