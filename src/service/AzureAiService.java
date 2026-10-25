package service;

import util.JsonUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Azure OpenAI Clinical Healthcare Assistant Service ("Aarogya AI").
 * Connects to Azure OpenAI Service (GPT-4o / GPT-4o-mini) to answer
 * patient medical queries, dosage intervals, and aftercare instructions.
 */
public class AzureAiService {

    private static final String DEFAULT_DEPLOYMENT = "gpt-4o-mini";
    private static final String API_VERSION = "2024-02-15-preview";

    private final HttpClient httpClient;

    public AzureAiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static String getEndpoint() {
        String ep = System.getenv("AZURE_OPENAI_ENDPOINT");
        return (ep != null && !ep.trim().isEmpty()) ? ep.trim().replaceAll("/+$", "") : "";
    }

    public static String getApiKey() {
        String key = System.getenv("AZURE_OPENAI_KEY");
        if (key == null || key.trim().isEmpty()) {
            key = System.getenv("AZURE_API_KEY");
        }
        return (key != null) ? key.trim() : "";
    }

    public static String getDeployment() {
        String dep = System.getenv("AZURE_OPENAI_DEPLOYMENT");
        return (dep != null && !dep.trim().isEmpty()) ? dep.trim() : DEFAULT_DEPLOYMENT;
    }

    public boolean isConfigured() {
        String ep = getEndpoint();
        String key = getApiKey();
        return !ep.isEmpty() && !key.isEmpty();
    }

    /**
     * Sends a clinical prompt to Azure OpenAI and returns the AI's response.
     */
    public String getClinicalAdvice(String userPrompt) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return "Hello! I am Aarogya AI, your clinical vaccine assistant. How may I help you today?";
        }

        if (!isConfigured()) {
            return generateLocalClinicalFallback(userPrompt.trim());
        }

        try {
            String endpoint = getEndpoint();
            String key = getApiKey();
            String deployment = getDeployment();

            String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                    endpoint, deployment, API_VERSION);

            String systemPrompt = "You are 'Aarogya AI', an expert Clinical Vaccine & Healthcare Assistant for India's National Vaccination Portal. " +
                    "Provide empathetic, highly accurate, evidence-based advice according to Indian Ministry of Health (MoHFW) guidelines. " +
                    "Explain dosage intervals (Covishield: 84 days, Covaxin: 28 days, Pfizer: 21 days), comorbidity safety (diabetes, hypertension), " +
                    "and standard post-vaccination fever/soreness aftercare (paracetamol, hydration). Keep responses concise and formatted in bullet points when helpful.";

            String escapedPrompt = escapeJson(userPrompt.trim());
            String escapedSystem = escapeJson(systemPrompt);

            String requestBody = String.format(
                    "{\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0.5,\"max_tokens\":400}",
                    escapedSystem, escapedPrompt
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("api-key", key)
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                // Extract message content from Azure OpenAI JSON
                int contentIdx = body.indexOf("\"content\":");
                if (contentIdx != -1) {
                    int start = body.indexOf("\"", contentIdx + 10);
                    if (start != -1) {
                        int end = findJsonStringEnd(body, start + 1);
                        if (end != -1) {
                            String rawContent = body.substring(start + 1, end);
                            return unescapeJson(rawContent);
                        }
                    }
                }
                return "Advice received from Azure AI: " + body;
            } else {
                System.err.println("[Azure OpenAI] HTTP " + response.statusCode() + ": " + response.body());
                return generateLocalClinicalFallback(userPrompt.trim());
            }

        } catch (Exception e) {
            System.err.println("[Azure OpenAI Exception] " + e.getMessage());
            return generateLocalClinicalFallback(userPrompt.trim());
        }
    }

    /**
     * Intelligent local medical rules engine that answers immediate clinical questions
     * even before Azure credentials are set, ensuring 100% zero downtime.
     */
    private String generateLocalClinicalFallback(String query) {
        String q = query.toLowerCase();

        if (q.contains("fever") || q.contains("side effect") || q.contains("headache") || q.contains("pain") || q.contains("arm")) {
            return "🌡️ **Post-Vaccination Aftercare Advice:**\n\n" +
                   "• **Mild Fever & Chills**: Normal signs that your immune system is responding. Take Paracetamol (500mg/650mg) after consulting a pharmacist.\n" +
                   "• **Arm Soreness**: Apply a clean, cool, damp washcloth over the injection area. Gently move your arm to reduce stiffness.\n" +
                   "• **Hydration**: Drink plenty of fluids (water, electrolytes) and get adequate rest.\n" +
                   "• **When to consult a doctor**: If high fever persists beyond 48 hours or you experience difficulty breathing.";
        }

        if (q.contains("dose 2") || q.contains("interval") || q.contains("gap") || q.contains("second dose")) {
            return "📅 **Clinical Vaccine Intervals (MoHFW Guidelines):**\n\n" +
                   "• **Covishield (Oxford-AstraZeneca)**: Minimum **84 days (12 weeks)** between Dose 1 and Dose 2 for optimal antibody titers.\n" +
                   "• **Covaxin (Bharat Biotech)**: Minimum **28 days (4 weeks)** between Dose 1 and Dose 2.\n" +
                   "• **Pfizer-BioNTech**: Minimum **21 days (3 weeks)** between doses.\n\n" +
                   "Our portal automatically blocks early Dose 2 bookings to protect your clinical safety.";
        }

        if (q.contains("diabetes") || q.contains("bp") || q.contains("blood pressure") || q.contains("comorbidity") || q.contains("heart") || q.contains("sugar")) {
            return "🩺 **Comorbidities & High-Risk Priority:**\n\n" +
                   "• **Safety**: Clinical trials confirm vaccines are safe and strongly recommended for individuals with diabetes, hypertension, and asthma.\n" +
                   "• **Routine Medicines**: Do NOT stop your regular blood pressure or diabetes medication on vaccination day.\n" +
                   "• **Priority Queue**: On our portal, checking 'Pre-existing Medical Condition' gives your profile a higher priority weight for waitlists.";
        }

        if (q.contains("age") || q.contains("child") || q.contains("senior") || q.contains("18") || q.contains("60")) {
            return "👥 **Age Categories & Eligibility:**\n\n" +
                   "• **Seniors (60+)**: Highest allocation priority and dedicated morning slots across all centers.\n" +
                   "• **Adults (18–59)**: Fully eligible for Covishield, Covaxin, and Pfizer.\n" +
                   "• **Youth (12–17)**: Approved for Corbevax and Covaxin under pediatric guidelines.";
        }

        return "🩺 **Clinical Guidance (Aarogya AI):**\n\n" +
               "All approved vaccines (Covishield, Covaxin, Pfizer, Corbevax) undergo strict clinical evaluation. " +
               "Please ensure you carry your original Photo ID to the hospital and stay hydrated. " +
               "If you are feeling acute illness or fever today, it is advised to wait until recovery before receiving your dose.";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\t", "\t");
    }

    private static int findJsonStringEnd(String json, int start) {
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '\"') {
                return i;
            }
        }
        return -1;
    }
}