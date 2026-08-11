package com.fulizaboost.controller;

import com.fulizaboost.entity.FulizaBoost;
import com.fulizaboost.service.FulizaBoostService;
import com.fulizaboost.service.MpesaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/boosts")
@CrossOrigin(origins = "*")
public class FulizaBoostController {

    @Autowired
    private FulizaBoostService boostService;

    @Autowired
    private MpesaService mpesaService;

    // ------------------ BOOST ENDPOINTS ------------------

    @PostMapping
    public ResponseEntity<FulizaBoost> createBoost(@RequestBody FulizaBoost boost) {
        return ResponseEntity.ok(boostService.saveBoost(boost));
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

    @GetMapping("/status/{status}")
    public ResponseEntity<List<FulizaBoost>> getBoostsByStatus(@PathVariable String status) {
        return ResponseEntity.ok(boostService.getBoostsByStatus(status.toUpperCase()));
    }

    // ------------------ PAYMENT (M-Pesa STK Push) ------------------

    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> payBoostFee(@RequestBody Map<String, Object> payload) {
        try {
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

            Double amount = ((Number) payload.get("amount")).doubleValue();
            Double fee = ((Number) payload.get("fee")).doubleValue();
            String identificationNumber = (String) payload.get("identificationNumber");

            String externalRef = "BOOST-" + UUID.randomUUID().toString().substring(0, 8);

            FulizaBoost boost = new FulizaBoost();
            boost.setIdentificationNumber(identificationNumber);
            boost.setAmount(amount);
            boost.setFee(fee);
            boost.setPhoneNumber(phone);
            boost.setExternalReference(externalRef);
            boost.setPaid(false);
            boost.setPaymentStatus("PENDING");
            boostService.saveBoost(boost);

            Map<String, Object> mpesaResponse = mpesaService.initiateStkPush(
                    phone, fee.intValue(), externalRef, "FulizaBoost Fee"
            );

            // Capture CheckoutRequestID so we can match the async callback later
            Object checkoutRequestId = mpesaResponse.get("CheckoutRequestID");
            if (checkoutRequestId != null) {
                boost.setCheckoutRequestId((String) checkoutRequestId);
                boostService.saveBoost(boost);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "STK push sent. Client saved with PENDING status.",
                    "data", mpesaResponse,
                    "reference", externalRef,
                    "boostId", boost.getId()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------ CALLBACK (M-Pesa Daraja) ------------------

    @PostMapping("/pay/callback")
    public ResponseEntity<Map<String, Object>> handleMpesaCallback(@RequestBody Map<String, Object> callbackData) {
        try {
            Map<String, Object> body = (Map<String, Object>) callbackData.get("Body");
            Map<String, Object> stkCallback = (Map<String, Object>) body.get("stkCallback");

            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            int resultCode = (Integer) stkCallback.get("ResultCode");

            if (checkoutRequestId == null) {
                return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Missing CheckoutRequestID"));
            }

            FulizaBoost boost = boostService.getBoostByCheckoutRequestId(checkoutRequestId);
            if (boost == null) {
                return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Boost not found"));
            }

            if (resultCode == 0) {
                // Payment succeeded - extract receipt from CallbackMetadata
                Map<String, Object> metadata = (Map<String, Object>) stkCallback.get("CallbackMetadata");
                List<Map<String, Object>> items = (List<Map<String, Object>>) metadata.get("Item");

                String mpesaReceipt = null;
                for (Map<String, Object> item : items) {
                    if ("MpesaReceiptNumber".equals(item.get("Name"))) {
                        mpesaReceipt = String.valueOf(item.get("Value"));
                    }
                }

                boost.setPaymentStatus("COMPLETED");
                boost.setPaid(true);
                boost.setPaymentDate(LocalDateTime.now());
                boost.setMpesaReceipt(mpesaReceipt);
            } else if (resultCode == 1032) {
                // User cancelled the STK prompt
                boost.setPaymentStatus("CANCELLED");
                boost.setPaid(false);
            } else {
                boost.setPaymentStatus("FAILED");
                boost.setPaid(false);
            }

            boostService.saveBoost(boost);

            return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ResultCode", 1, "ResultDesc", "Error: " + e.getMessage()));
        }
    }

    // ------------------ REPORTING ------------------

    @GetMapping("/paid")
    public ResponseEntity<List<FulizaBoost>> getPaidBoosts(@RequestParam(required = false) String date) {
        return ResponseEntity.ok(
                date != null ?
                        boostService.getPaidBoostsByDate(date) :
                        boostService.getAllPaidBoosts()
        );
    }

    @GetMapping("/paid/total")
    public ResponseEntity<Map<String, Object>> getTotalFees(@RequestParam(required = false) String date) {
        double total = date != null ?
                boostService.getTotalFeesByDate(date) :
                boostService.getTotalFees();
        return ResponseEntity.ok(Map.of("total", total));
    }

    @GetMapping("/paid/count")
    public ResponseEntity<Map<String, Object>> getTotalCustomers(@RequestParam(required = false) String date) {
        int count = date != null ?
                boostService.getPaidBoostCountByDate(date) :
                boostService.getPaidBoostCount();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/paid/filter")
    public ResponseEntity<List<FulizaBoost>> filterPaidBoosts(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        return ResponseEntity.ok(
                boostService.getPaidBoostsBetweenDates(
                        LocalDate.parse(startDate).atStartOfDay(),
                        LocalDate.parse(endDate).atTime(23, 59, 59)
                )
        );
    }

    @DeleteMapping("/all")
    public ResponseEntity<String> deleteAllBoosts(@RequestParam String confirm) {
        if (!"DELETE".equals(confirm)) {
            return ResponseEntity.badRequest()
                    .body("You must confirm deletion by passing ?confirm=DELETE");
        }
        boostService.deleteAllBoosts();
        return ResponseEntity.ok("All boosts have been deleted successfully");
    }

    @GetMapping("/phones")
    public ResponseEntity<List<String>> getAllPhoneNumbers() {
        return ResponseEntity.ok(boostService.getAllPhoneNumbers());
    }
}