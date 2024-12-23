package com.pirhotech.hammingchat.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.crypto.tink.subtle.Base64;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.pirhotech.hammingchat.adapters.ConversationAdapter;
import com.pirhotech.hammingchat.databinding.ActivityConversationBinding;
import com.pirhotech.hammingchat.e2ee.CryptoManager;
import com.pirhotech.hammingchat.errdetn.HammingCode;
import com.pirhotech.hammingchat.errdetn.TextToBinaryConverter;
import com.pirhotech.hammingchat.models.ChatMessage;
import com.pirhotech.hammingchat.models.User;
import com.pirhotech.hammingchat.networks.ApiClient;
import com.pirhotech.hammingchat.networks.ApiService;
import com.pirhotech.hammingchat.utilities.BaseActivity;
import com.pirhotech.hammingchat.utilities.Constants;
import com.pirhotech.hammingchat.utilities.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConversationActivity extends BaseActivity {

    private ActivityConversationBinding binding;
    private ConversationAdapter conversationAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private List<ChatMessage> chatMessageList;
    private User receiverUser;
    private String conversationId;
    private boolean isReceiverAvailable = false;
    private String receiverPhoneNumber = null;
    private CryptoManager cryptoManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadReceiverDetails();
        setListeners();
        init();
        listMessages();

        try {
            cryptoManager = new CryptoManager(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String originalText = "Hello, World!sfgsfdgfg dugaud fguagu agfuaoidgfu au98e9r agfjajdfi8a9dsf ejr uppwe9ajdkaj 89uew jadji u9WE AJEI AU98WEJ ae 9aeu aje uFEJADFIFJADSFUAJDFUAE 898WEIADUF A8F8E JAF ASE8FAEF8 WEJfaduf SF EWRFEF AJFU9 8fWRPUWef EWF8UEWfHWNE FJWWEf8 9WERUWEF WEfOWF HAGGJAEFHSFUEWF JFUJPFUPAFUJA FU VALID";
        String binaryData = TextToBinaryConverter.textToBinary(originalText);
        Log.d("binData",binaryData);
        String encodedData = HammingCode.encodeLongString(binaryData);
        Log.d("encoded",encodedData);

        String decodedBinary = HammingCode.decodeLongString(encodedData);
        Log.d("decoded",binaryData);

        String decodedText = TextToBinaryConverter.binaryToText(decodedBinary);
        Log.d("decodedtext",decodedText);


    }

    private  String encode(String originalText)
    {
        String binaryData = TextToBinaryConverter.textToBinary(originalText);
        Log.d("binData",binaryData);
        String encodedData = HammingCode.encodeLongString(binaryData);
        Log.d("encoded",encodedData);
        return encodedData;

    }
    public String decode(String encodedData){
        String decodedBinary = HammingCode.decodeLongString(encodedData);
        Log.d("decoded",encodedData);

        String decodedText = TextToBinaryConverter.binaryToText(decodedBinary);
        Log.d("decodedtext",decodedText);

        return decodedText;
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(view -> onBackPressed());
        binding.layoutSend.setOnClickListener(view -> {
            try {
                sendMessage();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }


        });
        binding.imagePhoneCall.setOnClickListener(view -> startPhoneCall());
    }

    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessageList = new ArrayList<>();

        //Set adapter to  recyclerview
        conversationAdapter = new ConversationAdapter(
                chatMessageList,
                getBitmapFromEncodedUrl(receiverUser.getImage()),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        binding.conversationRecyclerView.setAdapter(conversationAdapter);

        database = FirebaseFirestore.getInstance();
    }


    private String encryptMessage(String message) {
        try {
            byte[] encryptedMessage = cryptoManager.encrypt(message, null);
            return Base64.encodeToString(encryptedMessage, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Decode from Base64 string and decrypt
    private String decryptMessage(String base64EncryptedMessage) {
        try {
            byte[] encryptedMessage = Base64.decode(base64EncryptedMessage, Base64.DEFAULT);
            String decryptedMessage = cryptoManager.decrypt(encryptedMessage, null);
            Log.d("decrypted data", decryptedMessage);
            return decryptedMessage;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    private void sendMessage()  {


        if (binding.inputMessage.getText().toString().trim().isEmpty()) {
            return;
        }
        HashMap<String, Object> chatMessage = new HashMap<>();
        chatMessage.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        chatMessage.put(Constants.KEY_RECEIVER_ID, receiverUser.getId());
        chatMessage.put(Constants.KEY_MESSAGE, encode(binding.inputMessage.getText().toString())) ;
        chatMessage.put(Constants.KEY_TIMESTAMP, new Date());
        preferenceManager.putString("input",binding.inputMessage.getText().toString());

        database.collection(Constants.KEY_COLLECTION_CHAT)
                .add(chatMessage);

        chatMessage.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString()) ;

        if (conversationId != null) {
            updateConversationConversion(binding.inputMessage.getText().toString());
        } else {
            HashMap<String, Object> conversation = new HashMap<>();
            conversation.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversation.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversation.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversation.put(Constants.KEY_RECEIVER_ID, receiverUser.getId());
            conversation.put(Constants.KEY_RECEIVER_NAME, receiverUser.getName());
            conversation.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.getImage());
            conversation.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

            conversation.put(Constants.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversation.put(Constants.KEY_TIMESTAMP, new Date());
            addConversion(conversation);
        }

        if (!isReceiverAvailable) {
            try {
                JSONArray tokens = new JSONArray();
                tokens.put(receiverUser.getToken());

                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
//                Log.d("chatMessage encode",encode(binding.inputMessage.getText().toString()));
//                Log.d("chatMessage encode",decode(encode(binding.inputMessage.getText().toString())));

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MESSAGE_DATA, data);
                body.put(Constants.REMOTE_MESSAGE_REGISTRATION_IDS, tokens);

                sendNotification(body.toString());

            } catch (Exception e) {
                showToast("sent failed" + e.getMessage());
            }
        }

        binding.inputMessage.setText(null);
    }

    private void listMessages() {
        // Iam sender You are receiver
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.getId())
                .addSnapshotListener(eventListener);

        // You are sender Iam receiver
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.getId())
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }


    @SuppressLint("NotifyDataSetChanged")
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {
            int count = chatMessageList.size();
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    ChatMessage chatMessage = new ChatMessage();
                    Log.d("document",documentChange.getDocument().getString(Constants.KEY_MESSAGE));
                    String s=decode(documentChange.getDocument().getString(Constants.KEY_MESSAGE));
                    if(HammingCode.ifErrorFixed){
                        showToast("1 bit Error is fixed");
                    } else if (!s.equals(preferenceManager.getString("input")))
                    {
                        showToast("there is some error happend");
                    }

                    chatMessage.setMessage(s);
                    chatMessage.setSenderId(documentChange.getDocument().getString(Constants.KEY_SENDER_ID));
                    chatMessage.setReceiverId(documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID));
                    chatMessage.setDateTime(getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP)));
                    chatMessage.setDateObject(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMessageList.add(chatMessage);
                }
            }

            Collections.sort(chatMessageList, (obj1, obj2) -> obj1.getDateObject().compareTo(obj2.getDateObject()));
            if (count == 0) {
                conversationAdapter.notifyDataSetChanged();
            } else {
                conversationAdapter.notifyItemRangeInserted(chatMessageList.size(), chatMessageList.size());
                binding.conversationRecyclerView.smoothScrollToPosition(chatMessageList.size() - 1);
            }
            binding.conversationRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);

        if (conversationId == null) {
            checkForConversationConversion();
        }
    };

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void sendNotification(String messageBody) {
        ApiClient.getApiClient().create(ApiService.class)
                .sendMessage(Constants.getRemoteMessageHeaders(),
                        messageBody).enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful()) {
                            try {
                                if (response.body() != null) {
                                    JSONObject responseJson = new JSONObject(response.body());
                                    JSONArray results = responseJson.getJSONArray("results");
                                    if (responseJson.getInt("failure") == 1) {
                                        JSONObject error = (JSONObject) results.get(0);
                                        showToast(error.getString("error"));
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            // showToast("Notification sent successfully");
                        } else {
                            showToast("Error: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        showToast(t.getMessage());
                    }
                });
    }

    private void listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(receiverUser.getId())
                .addSnapshotListener(ConversationActivity.this, (value, error) -> {
                    if (error != null) {
                        return;
                    }
                    if (value != null) {
                        if (value.getLong(Constants.KEY_USER_AVAILABILITY) != null) {
                            int availability = Objects.requireNonNull(value.getLong(Constants.KEY_USER_AVAILABILITY))
                                    .intValue();
                            isReceiverAvailable = availability == 1;
                        }
                        receiverUser.setToken(value.getString(Constants.KEY_FCM_TOKEN));
                        if (receiverUser.getImage() == null) {
                            receiverUser.setImage(value.getString(Constants.KEY_IMAGE));
                            conversationAdapter.setReceiverProfileImage(getBitmapFromEncodedUrl(receiverUser.getImage()));
                            conversationAdapter.notifyItemRangeChanged(0, chatMessageList.size());
                        }
                    }
                    if (isReceiverAvailable) {
                        // Receiver is available (online)
                        binding.textUserAvailability.setVisibility(View.VISIBLE);
                    } else {
                        //Receiver is not available (offline)
                        binding.textUserAvailability.setVisibility(View.GONE);
                    }

                });
    }

    private void startPhoneCall() {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS).document(receiverUser.getId()).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot snapshot = task.getResult();
                        if (snapshot.exists()) {
                            receiverPhoneNumber = snapshot.getString(Constants.KEY_PHONE);
                            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + receiverPhoneNumber));
                            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 1);
                            } else {
                                startActivity(intent);
                            }

                        }
                    }
                });
    }

    void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textUserName.setText(receiverUser.getName());
    }

    private Bitmap getBitmapFromEncodedUrl(String image) {
        if (image != null) {
            byte[] bytes = Base64.decode(image, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
        return null;
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void addConversion(HashMap<String, Object> conversion) {
        database.collection(Constants.KEY_COLLECTION_RECENT_CONVERSATION)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversationId = documentReference.getId());
    }

    private void checkForConversationConversion() {
        if (chatMessageList.size() != 0) {
            checkForConversionRemotely(preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.getId());
            checkForConversionRemotely(receiverUser.getId(),
                    preferenceManager.getString(Constants.KEY_USER_ID));
        }
    }

    private void updateConversationConversion(String message) {
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_RECENT_CONVERSATION).document(conversationId);
        HashMap<String, Object> update = new HashMap<>();
        update.put(Constants.KEY_LAST_MESSAGE, message);
        update.put(Constants.KEY_TIMESTAMP, new Date());
        documentReference.update(update);
    }

    private void checkForConversionRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_RECENT_CONVERSATION)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversationOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversationOnCompleteListener = new OnCompleteListener<QuerySnapshot>() {
        @Override
        public void onComplete(@NonNull Task<QuerySnapshot> task) {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
                DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
                conversationId = documentSnapshot.getId();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

}