package com.fulizaboost.service;

import com.fulizaboost.EnvConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class MpesaService {

    @Autowired
    private RestTemplate restTemplate;

    private final String consumerKey    = EnvConfig.dotenv.get("MPESA_CONSUMER_KEY");
    private final String consumerSecret = EnvConfig.dotenv.get("MPESA_CONSUMER_SECRET");
    private final String passkey        = EnvConfig.dotenv.get("MPESA_PASSKEY");
    private final String shortcode      = EnvConfig.dotenv.get("MPESA_SHORTCODE");
    private final String callbackUrl    = EnvConfig.dotenv.get("MPESA_CALLBACK_URL");
    private final boolean isProd        = "production".equalsIgnoreCase(EnvConfig.dotenv.get("MPESA_ENV"));

    private String baseUrl() {
        return isProd
                ? "https://api.safaricom.co.ke"
                : "https://sandbox.safaricom.co.ke";
    }

    public String getAccessToken() {
        String credentials = consumerKey + ":" + consumerSecret;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/oauth/v1/generate?grant_type=client_credentials",
                HttpMethod.GET,
                request,
                Map.class
        );

        return (String) response.getBody().get("access_token");
    }

    public Map<String, Object> initiateStkPush(String phone, int amount, String accountReference, String transactionDesc) {
        String token = getAccessToken();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = Base64.getEncoder().encodeToString(
                (shortcode + passkey + timestamp).getBytes()
        );

        // Daraja AccountReference has a practical limit (~12 chars for some paybills) - trim to be safe
        String safeRef = accountReference.length() > 12
                ? accountReference.substring(0, 12)
                : accountReference;

        Map<String, Object> payload = new HashMap<>();
        payload.put("BusinessShortCode", shortcode);
        payload.put("Password", password);
        payload.put("Timestamp", timestamp);
        payload.put("TransactionType", "CustomerPayBillOnline");
        payload.put("Amount", amount);
        payload.put("PartyA", phone);
        payload.put("PartyB", shortcode);
        payload.put("PhoneNumber", phone);
        payload.put("CallBackURL", callbackUrl);
        payload.put("AccountReference", safeRef);
        payload.put("TransactionDesc", transactionDesc);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/mpesa/stkpush/v1/processrequest",
                request,
                Map.class
        );

        return response.getBody();
    }
}