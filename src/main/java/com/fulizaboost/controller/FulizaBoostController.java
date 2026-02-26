package com.fulizaboost.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fulizaboost.EnvConfig;
import com.fulizaboost.entity.FulizaBoost;
import com.fulizaboost.service.FulizaBoostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/boosts")
@CrossOrigin(origins = "*")
public class FulizaBoostController {

    @Autowired
    private FulizaBoostService boostService;

    // PayHero settings from .env
    private final String PAYHERO_API = "https://backend.payhero.co.ke/api/v2/payments";
    private final String payHeroUsername = EnvConfig.dotenv.get("PAYHERO_API_USERNAME");
    private final String payHeroPassword = EnvConfig.dotenv.get("PAYHERO_API_PASSWORD");
    private final String payHeroChannelId = EnvConfig.dotenv.get("PAYHERO_CHANNEL_ID");
    private final String callbackUrl = EnvConfig.dotenv.get("PAYHERO_CALLBACK_URL");

    private RestTemplate restTemplate = new RestTemplate();

    // Build Basic Auth header from username:password
    private String getPayHeroBasicAuth() {
        String credentials = payHeroUsername + ":" + payHeroPassword;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    // ------------------ BOOST ENDPOINTS ------------------

    @PostMapping
    public ResponseEntity<FulizaBoost> createBoost(@RequestBody FulizaBoost boost) {
        FulizaBoost savedBoost = boostService.saveBoost(boost);
        return ResponseEntity.ok(savedBoost);
    }

    @GetMapping
    public ResponseEntity<List<FulizaBoost>> getAllBoosts() {
        return ResponseEntity.ok(boostService.getAllBoosts());
    }

    @GetMapping("/by-id/{identificationNumber}")
    public ResponseEntity<List<FulizaBoost>> getBoostsByIdNumber(@PathVariable String identificationNumber) {
        return ResponseEntity.ok(boostService.getBoostsByIdentificationNumber(identificationNumber));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FulizaBoost> getBoostById(@PathVariable Long id) {
        return ResponseEntity.ok(boostService.getBoostById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteBoost(@PathVariable Long id) {
        boostService.deleteBoost(id);
        return ResponseEntity.ok("Boost deleted successfully");
    }
    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> payBoostFee(@RequestBody Map<String, Object> payload) {
        try {
            // 1️⃣ Format phone
            String rawPhone = ((String) payload.get("phone")).replaceAll("\\D", "");
            String phone;

            if (rawPhone.startsWith("2540") && rawPhone.length() == 13) {
                rawPhone = "254" + rawPhone.substring(4);
            }

            if (rawPhone.startsWith("254") && rawPhone.length() == 12) {
                phone = rawPhone;
            } else if ((rawPhone.startsWith("07") || rawPhone.startsWith("01")) && rawPhone.length() == 10) {
                phone = "254" + rawPhone.substring(1);
            } else if ((rawPhone.startsWith("7") || rawPhone.startsWith("1")) && rawPhone.length() == 9) {
                phone = "254" + rawPhone;
            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid phone number"));
            }

            if (!phone.matches("^254(7|1)\\d{8}$")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid Safaricom number"));
            }

            // 2️⃣ Extract data
            Double amount = ((Number) payload.get("amount")).doubleValue();
            Double fee = ((Number) payload.get("fee")).doubleValue();
            String identificationNumber = (String) payload.get("identificationNumber");
            String customerName = (String) payload.getOrDefault("customer_name", "Customer");

            // 3️⃣ Generate external reference
            String externalRef = "BOOST-" + UUID.randomUUID();

            // 4️⃣ Save pending record in DB
            FulizaBoost boost = new FulizaBoost();
            boost.setIdentificationNumber(identificationNumber);
            boost.setAmount(amount);
            boost.setFee(fee);
            boost.setExternalReference(externalRef);
            boost.setPaid(false);
            boost.setPaymentStatus("PENDING");
            boostService.saveBoost(boost);

            // 5️⃣ Build PayHero payload (match callback keys)
            Map<String, Object> payHeroPayload = new HashMap<>();
            payHeroPayload.put("amount", fee.intValue());
            payHeroPayload.put("phone_number", phone);
            payHeroPayload.put("channel_id", Integer.parseInt(payHeroChannelId));
            payHeroPayload.put("provider", "m-pesa");
            payHeroPayload.put("external_reference", externalRef); // MUST match callback
            payHeroPayload.put("customer_name", customerName);
            payHeroPayload.put("callback_url", callbackUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", getPayHeroBasicAuth());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payHeroPayload, headers);

            // 6️⃣ Send request
            System.out.println("Sending PayHero Request: " + payHeroPayload);
            ResponseEntity<String> response = restTemplate.postForEntity(PAYHERO_API, request, String.class);
            System.out.println("PayHero Response: " + response.getBody());

            // 7️⃣ Return result to client
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment initiated. Client saved with PENDING status.",
                    "data", response.getBody(),
                    "reference", externalRef,
                    "boostId", boost.getId()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    // ------------------ PAYHERO CALLBACK ------------------
    @PostMapping("/pay/callback")
    public ResponseEntity<String> handlePayHeroCallback(@RequestBody Map<String, Object> callbackData) {
        System.out.println("PayHero CALLBACK RAW: " + callbackData);

        Map<String, Object> response = (Map<String, Object>) callbackData.get("response");
        if (response == null) {
            System.err.println("Missing response object in callback");
            return ResponseEntity.ok("Missing response object");
        }

        // Determine reference
        String reference = (String) response.getOrDefault(
                "ExternalReference",
                response.get("User_Reference")
        );

        if (reference == null) {
            System.err.println("Missing reference in callback response");
            return ResponseEntity.ok("Missing reference");
        }

        FulizaBoost boost = boostService.getBoostByReference(reference);
        if (boost == null) {
            System.err.println("Boost not found for reference: " + reference);
            return ResponseEntity.ok("Boost not found");
        }

        // Determine payment status
        String paymentStatus = "FAILED"; // default
        Object statusObj = response.get("Status");
        Object successObj = callbackData.get("status");

        if (statusObj instanceof String) {
            String statusStr = ((String) statusObj).toUpperCase();
            if (statusStr.equals("COMPLETED") || statusStr.equals("SUCCESS")) {
                paymentStatus = "COMPLETED";
            } else if (statusStr.equals("CANCELLED") || statusStr.equals("FAILED")) {
                paymentStatus = "FAILED";
            }
        } else if (successObj instanceof Boolean && (Boolean) successObj) {
            paymentStatus = "COMPLETED";
        }

        // Update boost record
        if (!boost.getPaymentStatus().equals(paymentStatus)) {
            boost.setPaymentStatus(paymentStatus);
            boost.setPaid(paymentStatus.equals("COMPLETED"));
            if (paymentStatus.equals("COMPLETED")) {
                boost.setPaymentDate(LocalDateTime.now());
                boost.setMpesaReceipt((String) response.get("MpesaReceiptNumber"));
            }
            boostService.saveBoost(boost);
        }

        System.out.println("Payment status updated to " + paymentStatus + " for " + reference);
        return ResponseEntity.ok("Callback processed");
    }
    // ------------------ REPORTING ENDPOINTS ------------------

    @GetMapping("/paid")
    public ResponseEntity<List<FulizaBoost>> getPaidBoosts(
            @RequestParam(required = false) String date
    ) {
        List<FulizaBoost> boosts;
        if (date != null) {
            boosts = boostService.getPaidBoostsByDate(date);
        } else {
            boosts = boostService.getAllPaidBoosts();
        }
        return ResponseEntity.ok(boosts);
    }

    @GetMapping("/paid/total")
    public ResponseEntity<Map<String, Object>> getTotalFees(
            @RequestParam(required = false) String date
    ) {
        double total = (date != null) ? boostService.getTotalFeesByDate(date) : boostService.getTotalFees();
        return ResponseEntity.ok(Map.of("total", total));
    }

    @GetMapping("/paid/count")
    public ResponseEntity<Map<String, Object>> getTotalCustomers(
            @RequestParam(required = false) String date
    ) {
        int count = (date != null) ? boostService.getPaidBoostCountByDate(date) : boostService.getPaidBoostCount();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/paid/filter")
    public ResponseEntity<List<FulizaBoost>> filterPaidBoosts(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        List<FulizaBoost> boosts = boostService.getPaidBoostsBetweenDates(
                LocalDate.parse(startDate).atStartOfDay(),
                LocalDate.parse(endDate).atTime(23, 59, 59)
        );
        return ResponseEntity.ok(boosts);
    }

    @DeleteMapping("/all")
    public ResponseEntity<String> deleteAllBoosts(@RequestParam String confirm) {
        if (!"DELETE".equals(confirm)) {
            return ResponseEntity.badRequest().body("You must confirm deletion by passing ?confirm=DELETE");
        }
        boostService.deleteAllBoosts();
        return ResponseEntity.ok("All boosts have been deleted successfully");
    }
}
