package service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Fast2SMS Gateway Integration Service.
 * Automatically dispatches official real-time transactional SMS to patient phones in India.
 * 
 * Free Registration: https://www.fast2sms.com
 * Set your API key in FAST2SMS_API_KEY environment variable or below in DEFAULT_API_KEY.
 */
public class SmsService {

    // Configured Fast2SMS API Key
    private static final String DEFAULT_API_KEY = "WCJ1HGsMrpSwcTFGB4Nx1VZgB0HIa8UYooC4sEosxwOY1ITrJQ21Fh3NjXDgx5oa6UJkkSVYDLl2Pp2D"; 

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public static String getApiKey() {
        String envKey = System.getenv("FAST2SMS_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        return DEFAULT_API_KEY.trim();
    }

    /**
     * Sends SMS asynchronously in the background so it never slows down the HTTP booking response.
     */
    public static void sendSmsAsync(String rawPhoneNumber, String messageText) {
        if (rawPhoneNumber == null || rawPhoneNumber.trim().isEmpty()) {
            return;
        }

        // Clean phone number (extract 10-digit Indian mobile)
        String phone = rawPhoneNumber.replaceAll("[^0-9]", "");
        if (phone.length() > 10) {
            phone = phone.substring(phone.length() - 10);
        }

        if (phone.length() != 10) {
            System.out.println("[Fast2SMS] Skipped: Invalid mobile number format (" + rawPhoneNumber + ")");
            return;
        }

        final String targetPhone = phone;
        CompletableFuture.runAsync(() -> {
            executeFast2SmsDispatch(targetPhone, messageText);
        });
    }

    private static void executeFast2SmsDispatch(String phone, String messageText) {
        String apiKey = getApiKey();
        if (apiKey.isEmpty() || "YOUR_FAST2SMS_API_KEY_HERE".equalsIgnoreCase(apiKey)) {
            System.out.println("------------------------------------------------------------------");
            System.out.println(" 📲 [Fast2SMS Auto-Trigger] SMS Dispatched for: +91 " + phone);
            System.out.println(" Content: " + messageText);
            System.out.println(" >> To receive on physical phones, get your free key at fast2sms.com");
            System.out.println("    and set FAST2SMS_API_KEY or paste in src/service/SmsService.java");
            System.out.println("------------------------------------------------------------------");
            return;
        }

        try {
            // Fast2SMS Quick Route (route=q) with Authorization Header
            String url = String.format(
                "https://www.fast2sms.com/dev/bulkV2?route=q&message=%s&language=english&flash=0&numbers=%s",
                URLEncoder.encode(messageText, StandardCharsets.UTF_8),
                phone
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("authorization", apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() == 200) {
                if (responseBody != null && responseBody.contains("\"return\":true")) {
                    System.out.println("[Fast2SMS] ✅ Real SMS successfully dispatched to +91 " + phone);
                    System.out.println("[Fast2SMS Carrier Response] " + responseBody);
                } else if (responseBody != null && responseBody.contains("100 INR")) {
                    System.out.println("------------------------------------------------------------------");
                    System.out.println("⚠️ [Fast2SMS Wallet Notice] +91 " + phone);
                    System.out.println(" Fast2SMS Response: You need a one-time min ₹100 wallet recharge on fast2sms.com to unlock the API route.");
                    System.out.println(" Message attempted: " + messageText);
                    System.out.println(" ✨ Meanwhile, patient can click 'Receive via WhatsApp' or 'Open in SMS App' on screen for instant zero-cost delivery!");
                    System.out.println("------------------------------------------------------------------");
                } else {
                    System.out.println("[Fast2SMS Response] " + responseBody);
                }
            } else {
                System.err.println("[Fast2SMS] ⚠️ API returned HTTP " + response.statusCode() + ": " + responseBody);
            }

        } catch (Exception e) {
            System.err.println("[Fast2SMS Error] Failed to dispatch SMS to " + phone + ": " + e.getMessage());
        }
    }

    public static void sendAppointmentConfirmation(String phone, String patientName, String tokenId,
                                                   String centerName, String date, String slotTime,
                                                   String vaccine, int doseNumber) {
        String msg = String.format(
            "VaxSlot Pass Confirmed: Dear %s, your Dose %d (%s) slot at %s is CONFIRMED for %s (%s). Token ID: %s. Please bring original photo ID.",
            patientName, doseNumber, vaccine, centerName, date, slotTime, tokenId
        );
        sendSmsAsync(phone, msg);
    }

    public static void sendWaitlistNotification(String phone, String patientName, String centerName, int position) {
        String msg = String.format(
            "VaxSlot Waitlist: Dear %s, you are placed on the Priority Waitlist (Position #%d) for %s. You will receive an SMS alert when a slot opens.",
            patientName, position, centerName
        );
        sendSmsAsync(phone, msg);
    }

    public static void sendPromotionNotification(String phone, String patientName, String tokenId,
                                                 String centerName, String date, String slotTime) {
        String msg = String.format(
            "VaxSlot Promotion: Great news %s! A slot opened at %s. You have been auto-scheduled for %s (%s). Token ID: %s.",
            patientName, centerName, date, slotTime, tokenId
        );
        sendSmsAsync(phone, msg);
    }

    public static void sendRescheduleNotification(String phone, String patientName, String tokenId,
                                                  String centerName, String date, String slotTime) {
        String msg = String.format(
            "VaxSlot Rescheduled: Dear %s, your appointment %s was successfully rescheduled to %s (%s) at %s.",
            patientName, tokenId, date, slotTime, centerName
        );
        sendSmsAsync(phone, msg);
    }
}
