package com.fpoly.duan.service;

import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.fpoly.duan.config.PayOSProperties;

import lombok.RequiredArgsConstructor;

/**
 * Xác thực chữ ký webhook thanh toán PayOS (HMAC_SHA256 trên chuỗi key=value đã sort).
 */
@Service
@RequiredArgsConstructor
public class PayOSWebhookService {

    private final PayOSProperties payOSProperties;

    public boolean verifyPaymentWebhookSignature(JSONObject data, String expectedSignature) {
        if (expectedSignature == null || expectedSignature.isBlank() || data == null) {
            return false;
        }
        String payload = buildSortedQueryString(data);
        String computed = hmacSha256Hex(payload, payOSProperties.getChecksumKey());
        return computed.equalsIgnoreCase(expectedSignature.trim());
    }

    private String buildSortedQueryString(JSONObject data) {
        TreeSet<String> sortedKeys = new TreeSet<>(data.keySet());
        StringBuilder sb = new StringBuilder();
        for (String key : sortedKeys) {
            Object val = data.get(key);
            if (JSONObject.NULL.equals(val) || val == null) {
                val = "";
            } else if (val instanceof JSONArray) {
                val = stringifyArrayWithSortedObjects((JSONArray) val);
            } else if (val instanceof JSONObject) {
                val = sortJsonObject((JSONObject) val).toString();
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(val);
        }
        return sb.toString();
    }

    private String stringifyArrayWithSortedObjects(JSONArray arr) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            Object o = arr.get(i);
            if (o instanceof JSONObject) {
                out.put(sortJsonObject((JSONObject) o));
            } else {
                out.put(o);
            }
        }
        return out.toString();
    }

    private JSONObject sortJsonObject(JSONObject obj) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (String k : obj.keySet()) {
            sorted.put(k, obj.get(k));
        }
        return new JSONObject(sorted);
    }

    private static String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC SHA256 webhook thất bại", e);
        }
    }
}
