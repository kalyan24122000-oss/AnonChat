package com.anonchat.app.data.api;

import com.anonchat.app.data.model.AuthResponse;
import com.anonchat.app.data.model.Conversation;
import com.anonchat.app.data.model.Message;
import com.anonchat.app.data.model.Plan;
import com.anonchat.app.data.model.RedeemResponse;
import com.anonchat.app.data.model.SubscriptionStatus;
import com.anonchat.app.data.model.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // Auth
    @POST("api/auth/register")
    Call<AuthResponse> register(@Body Map<String, Object> body);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body Map<String, Object> body);

    @POST("api/auth/recover")
    Call<Map<String, Object>> recoverAccount(@Body Map<String, Object> body);

    @POST("api/auth/delete-account")
    Call<Map<String, Object>> deleteAccount();

    // User Profile & Settings
    @GET("api/users/me")
    Call<Map<String, Object>> getProfile();

    @PUT("api/users/public-key")
    Call<Map<String, Object>> updatePublicKey(@Body Map<String, String> body);

    @GET("api/users/{id}/public-key")
    Call<Map<String, Object>> getUserPublicKey(@Path("id") String userId);

    @GET("api/users/search")
    Call<Map<String, List<User>>> searchUser(@Query("id") String userId);

    @POST("api/users/block")
    Call<Map<String, Object>> blockUser(@Body Map<String, String> body);

    @POST("api/users/unblock")
    Call<Map<String, Object>> unblockUser(@Body Map<String, String> body);

    @GET("api/users/blocked")
    Call<Map<String, List<Map<String, Object>>>> getBlockedUsers();

    @POST("api/users/report")
    Call<Map<String, Object>> reportUser(@Body Map<String, String> body);

    // Conversations & Messages
    @GET("api/chat/conversations")
    Call<Map<String, List<Conversation>>> getConversations();

    @POST("api/chat/conversations")
    Call<Map<String, Object>> getOrCreateConversation(@Body Map<String, String> body);

    @GET("api/chat/conversations/{id}/messages")
    Call<Map<String, List<Message>>> getMessages(@Path("id") String conversationId, @Query("limit") int limit);

    @POST("api/chat/conversations/{id}/messages")
    Call<Map<String, Message>> sendMessage(@Path("id") String conversationId, @Body Map<String, Object> body);

    @POST("api/chat/conversations/{id}/read")
    Call<Map<String, Object>> markMessagesRead(@Path("id") String conversationId);

    @DELETE("api/chat/conversations/{id}/messages")
    Call<Map<String, Object>> clearConversationMessages(@Path("id") String conversationId);

    @DELETE("api/chat/conversations/{id}")
    Call<Map<String, Object>> deleteConversation(@Path("id") String conversationId);

    @DELETE("api/chat/messages/{id}")
    Call<Map<String, Object>> deleteMessage(@Path("id") String messageId);

    // Licensing & Plans
    @GET("api/license/status")
    Call<SubscriptionStatus> getSubscriptionStatus();

    @GET("api/license/plans")
    Call<Map<String, List<Plan>>> getPlans();

    @POST("api/license/redeem")
    Call<RedeemResponse> redeemLicense(@Body Map<String, String> body);

    // Media Upload (Images & Videos with 7-Day Auto Deletion)
    @POST("api/chat/media/upload")
    Call<Map<String, Object>> uploadMedia(@Body Map<String, String> body);

    @PUT("api/users/profile")
    Call<Map<String, Object>> updateProfile(@Body Map<String, Object> body);
}
