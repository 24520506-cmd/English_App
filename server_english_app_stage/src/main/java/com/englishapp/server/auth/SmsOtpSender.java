package com.englishapp.server.auth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
final class SmsOtpSender {
    private final RestClient client = RestClient.create("https://rest.esms.vn");
    private final String apiKey;
    private final String secretKey;
    private final String brandname;
    private final String contentTemplate;

    SmsOtpSender(@Value("${app.esms.api-key:}") String apiKey,
                 @Value("${app.esms.secret-key:}") String secretKey,
                 @Value("${app.esms.brandname:}") String brandname,
                 @Value("${app.esms.content-template}") String contentTemplate) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.brandname = brandname;
        this.contentTemplate = contentTemplate;
    }

    void send(String phone, String code, String requestId) {
        if (apiKey.isBlank() || secretKey.isBlank() || brandname.isBlank())
            throw new IllegalStateException("eSMS delivery is not configured");
        String localPhone = phone.startsWith("+84") ? "0" + phone.substring(3) : phone;
        @SuppressWarnings("unchecked")
        Map<String, Object> response = client.post()
                .uri("/MainService.svc/json/SendMultipleMessage_V4_post_json/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ApiKey", apiKey, "SecretKey", secretKey,
                        "Phone", localPhone, "Content", contentTemplate.replace("{code}", code),
                        "Brandname", brandname, "SmsType", "2", "IsUnicode", "0",
                        "RequestId", requestId))
                .retrieve().body(Map.class);
        Object result = response == null ? null : response.get("CodeResult");
        if (!"100".equals(String.valueOf(result)))
            throw new IllegalStateException("eSMS rejected OTP delivery");
    }
}
