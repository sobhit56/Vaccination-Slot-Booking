import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dsa.PriorityWaitlist;
import dsa.Trie;
import model.Appointment;
import model.Center;
import service.AzureAiService;
import service.BookingService;
import service.DataStore;
import util.JsonUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class Main {
    private static DataStore dataStore;
    private static BookingService bookingService;
    private static AzureAiService azureAiService;
    private static final Set<String> activeAdminSessions = Collections.synchronizedSet(new HashSet<>());
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin123";

    private static int getPort() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 8080;
    }

    public static void main(String[] args) throws IOException {
        dataStore = new DataStore();
        bookingService = new BookingService(dataStore);
        azureAiService = new AzureAiService();

        int port = getPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API Endpoints
        server.createContext("/api/centers", new CentersHandler());
        server.createContext("/api/search", new SearchHandler());
        server.createContext("/api/nearest", new NearestHandler());
        server.createContext("/api/book", new BookHandler());
        server.createContext("/api/cancel", new CancelHandler());
        server.createContext("/api/admin/login", new AdminLoginHandler());
        server.createContext("/api/appointments", new AppointmentsHandler());
        server.createContext("/api/verify", new VerifyHandler());
        server.createContext("/api/waitlist", new WaitlistHandler());
        server.createContext("/api/stats", new StatsHandler());
        server.createContext("/api/analytics", new AnalyticsHandler());
        server.createContext("/api/export/csv", new ExportCsvHandler());
        server.createContext("/api/track", new TrackHandler());
        server.createContext("/api/reschedule", new RescheduleHandler());
        server.createContext("/api/admin/slot/capacity", new SlotCapacityHandler());
        server.createContext("/api/admin/walkin", new WalkinHandler());
        server.createContext("/api/ai/chat", new AiChatHandler());

        // Static Web Files (HTML, CSS, JS)
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // Default single-thread or pool
        server.start();

        System.out.println("==================================================================");
        System.out.println(" 🏥 VACCINATION SLOT BOOKING SYSTEM (DSA-POWERED JAVA SERVER)");
        System.out.println("==================================================================");
        System.out.println(" Server running at: http://localhost:" + port);
        System.out.println(" Patient Portal:   http://localhost:" + port + "/index.html");
        System.out.println(" Admin Portal:     http://localhost:" + port + "/admin.html");
        System.out.println(" Azure AI Active:  " + (azureAiService.isConfigured() ? "Yes (Cloud Connected)" : "Local Clinical Advisor Mode"));
        System.out.println(" Data directory:   data/ (Centers, Patients, Bookings)");
        System.out.println(" Press Ctrl+C to stop the server.");
        System.out.println("==================================================================");
    }

    // --- Static File Handler (Serves web/ folder) ---
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }

            byte[] bytes = null;
            File file = new File("web" + path);
            if (file.exists() && !file.isDirectory()) {
                bytes = Files.readAllBytes(file.toPath());
            } else {
                InputStream is = Main.class.getResourceAsStream("/web" + path);
                if (is != null) {
                    bytes = is.readAllBytes();
                    is.close();
                }
            }

            if (bytes == null) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            String mime = "text/plain";
            if (path.endsWith(".html")) mime = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) mime = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) mime = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".json")) mime = "application/json; charset=UTF-8";
            else if (path.endsWith(".png")) mime = "image/png";
            else if (path.endsWith(".svg")) mime = "image/svg+xml";

            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // --- API: Get All Centers ---
    static class CentersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            StringBuilder sb = new StringBuilder("[\n");
            Collection<Center> centers = dataStore.getCenters().values();
            int i = 0;
            for (Center c : centers) {
                sb.append("  ").append(c.toJson());
                if (i < centers.size() - 1) sb.append(",\n");
                i++;
            }
            sb.append("\n]");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // --- API: Trie Prefix Search ---
    static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String query = getQueryParam(exchange.getRequestURI().getQuery(), "q");

            if (query == null || query.trim().isEmpty()) {
                sendJsonResponse(exchange, 200, "{\"query\":\"\",\"nodesTraversed\":0,\"executionTimeMicros\":0,\"results\":[]}");
                return;
            }

            Trie.TrieResult result = dataStore.getCenterTrie().searchPrefix(query);
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"query\":\"").append(JsonUtils.escape(query)).append("\",");
            sb.append("\"nodesTraversed\":").append(result.nodesTraversed).append(",");
            sb.append("\"executionTimeMicros\":").append(result.executionTimeMicros).append(",");
            sb.append("\"results\":[");
            for (int i = 0; i < result.centers.size(); i++) {
                sb.append(result.centers.get(i).toJson());
                if (i < result.centers.size() - 1) sb.append(",");
            }
            sb.append("]}");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // --- API: Find Nearest Centers (Haversine GeoDistance Algorithm) ---
    static class NearestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String latStr = getQueryParam(exchange.getRequestURI().getQuery(), "lat");
            String lngStr = getQueryParam(exchange.getRequestURI().getQuery(), "lng");

            double lat = 28.6139; // Default New Delhi coordinates
            double lng = 77.2090;

            try {
                if (latStr != null) lat = Double.parseDouble(latStr);
                if (lngStr != null) lng = Double.parseDouble(lngStr);
            } catch (NumberFormatException ignored) {}

            List<dsa.GeoDistance.CenterDistance> nearest = dsa.GeoDistance.findNearestCenters(
                dataStore.getCenters().values(), lat, lng
            );

            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < nearest.size(); i++) {
                sb.append("  ").append(nearest.get(i).toJson());
                if (i < nearest.size() - 1) sb.append(",\n");
            }
            sb.append("\n]");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // --- API: Book Slot (with Mutex Lock & Waitlist Fallback) ---
    static class BookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, JsonUtils.errorResponse("Method Not Allowed"));
                return;
            }

            String body = readRequestBody(exchange);
            String name = JsonUtils.getString(body, "name");
            String id = JsonUtils.getString(body, "id");
            int age = JsonUtils.getInt(body, "age", 30);
            String phone = JsonUtils.getString(body, "phone");
            String email = JsonUtils.getString(body, "email");
            boolean hasComorbidity = JsonUtils.getBoolean(body, "hasComorbidity", false);
            String centerId = JsonUtils.getString(body, "centerId");
            String slotId = JsonUtils.getString(body, "slotId");
            String date = JsonUtils.getString(body, "date");
            int doseNumber = JsonUtils.getInt(body, "doseNumber", 1);
            String dose1Date = JsonUtils.getString(body, "dose1Date");

            BookingService.BookingResult result = bookingService.bookSlot(
                id, name, age, phone, email, hasComorbidity, centerId, slotId, date, doseNumber, dose1Date
            );

            sendJsonResponse(exchange, result.success ? 200 : 400, result.toJson());
        }
    }

    // --- API: Cancel Appointment & Trigger Max-Heap Auto-Promotion ---
    static class CancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, JsonUtils.errorResponse("Method Not Allowed"));
                return;
            }

            String body = readRequestBody(exchange);
            String tokenId = JsonUtils.getString(body, "tokenId");

            BookingService.CancellationResult res = bookingService.cancelAppointment(tokenId);
            sendJsonResponse(exchange, res.success ? 200 : 400, res.toJson());
        }
    }

    // --- API: Track Patient Booking Status ---
    static class TrackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, JsonUtils.errorResponse("Method Not Allowed"));
                return;
            }

            String query = getQueryParam(exchange.getRequestURI().getQuery(), "q");
            if (query == null) query = "";

            BookingService.TrackingResult result = bookingService.trackBooking(query);
            sendJsonResponse(exchange, 200, result.toJson());
        }
    }

    // --- API: Patient Slot Reschedule ---
    static class RescheduleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, JsonUtils.errorResponse("Method Not Allowed"));
                return;
            }

            String body = readRequestBody(exchange);
            String tokenId = JsonUtils.getString(body, "tokenId");
            String newSlotId = JsonUtils.getString(body, "newSlotId");
            String newDate = JsonUtils.getString(body, "newDate");

            BookingService.RescheduleResult result = bookingService.rescheduleAppointment(tokenId, newSlotId, newDate);
            sendJsonResponse(exchange, result.success ? 200 : 400, result.toJson());
        }
    }

    // --- API: Admin Increase Slot Capacity ---
    static class SlotCapacityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, JsonUtils.errorResponse("Method Not Allowed"));
                return;
            }

            if (!isAuthorizedStaff(exchange)) {
                sendJsonResponse(exchange, 401, JsonUtils.errorResponse("Unauthorized staff access."));
                return;
            }

            String body = readRequestBody(exchange);
            String centerId = JsonUtils.getString(body, "centerId");
            String slotId = JsonUtils.getString(body, "slotId");
            int delta = JsonUtils.getInt(body, "addedCapacity", 5);

            boolean success = bookingService.increaseSlotCapacity(centerId, slotId, delta);
            if (success) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Slot capacity increased by " + delta + " doses successfully.\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to update slot capacity.\"}");
            }
        }
    }

    // --- API: Admin Direct Emergency Walk-in Registration ---
    static class WalkinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, JsonUtils.errorResponse("Method Not Allowed"));
                return;
            }

            if (!isAuthorizedStaff(exchange)) {
                sendJsonResponse(exchange, 401, JsonUtils.errorResponse("Unauthorized staff access."));
                return;
            }

            String body = readRequestBody(exchange);
            String id = JsonUtils.getString(body, "id");
            String name = JsonUtils.getString(body, "name");
            int age = JsonUtils.getInt(body, "age", 30);
            String phone = JsonUtils.getString(body, "phone");
            boolean hasComorbidity = JsonUtils.getBoolean(body, "hasComorbidity", false);
            String centerId = JsonUtils.getString(body, "centerId");
            String slotId = JsonUtils.getString(body, "slotId");
            int doseNumber = JsonUtils.getInt(body, "doseNumber", 1);
            boolean autoVaccinate = JsonUtils.getBoolean(body, "autoVaccinate", true);

            BookingService.BookingResult result = bookingService.registerWalkin(
                id, name, age, phone, hasComorbidity, centerId, slotId, doseNumber, autoVaccinate
            );

            sendJsonResponse(exchange, result.success ? 200 : 400, result.toJson());
        }
    }

    // --- API: Admin Visual Analytics & Demographic Metrics ---
    static class AnalyticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!isAuthorizedStaff(exchange)) {
                sendJsonResponse(exchange, 401, JsonUtils.errorResponse("Unauthorized."));
                return;
            }

            Collection<Appointment> appts = dataStore.getFastLookup().getAllAppointments();

            int covishieldCount = 0, covaxinCount = 0, pfizerCount = 0;
            int seniorsCount = 0, adultsCount = 0, youthCount = 0;
            int confirmedCount = 0, vaccinatedCount = 0, cancelledCount = 0;

            for (Appointment a : appts) {
                // Vaccine distribution
                String vax = (a.getVaccineType() != null) ? a.getVaccineType().toLowerCase() : "";
                if (vax.contains("covishield")) covishieldCount++;
                else if (vax.contains("covaxin")) covaxinCount++;
                else if (vax.contains("pfizer")) pfizerCount++;

                // Age demographics
                int age = a.getPatientAge();
                if (age >= 60) seniorsCount++;
                else if (age >= 18) adultsCount++;
                else youthCount++;

                // Status
                if ("VACCINATED".equalsIgnoreCase(a.getStatus())) vaccinatedCount++;
                else if ("CANCELLED".equalsIgnoreCase(a.getStatus())) cancelledCount++;
                else confirmedCount++;
            }

            int waitlistCount = dataStore.getPriorityWaitlist().size();
            int totalAppts = appts.size();

            String analyticsJson = String.format(
                "{\"totalAppointments\":%d," +
                "\"vaccineDistribution\":{\"covishield\":%d,\"covaxin\":%d,\"pfizer\":%d}," +
                "\"ageDemographics\":{\"seniors\":%d,\"adults\":%d,\"youth\":%d}," +
                "\"statusMetrics\":{\"confirmed\":%d,\"vaccinated\":%d,\"cancelled\":%d,\"waitlisted\":%d}}",
                totalAppts, covishieldCount, covaxinCount, pfizerCount,
                seniorsCount, adultsCount, youthCount,
                confirmedCount, vaccinatedCount, cancelledCount, waitlistCount
            );

            sendJsonResponse(exchange, 200, analyticsJson);
        }
    }

    // --- API: 1-Click Export Appointments to CSV Report ---
    static class ExportCsvHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!isAuthorizedStaff(exchange)) {
                sendJsonResponse(exchange, 401, JsonUtils.errorResponse("Unauthorized."));
                return;
            }

            StringBuilder csv = new StringBuilder();
            csv.append("Token ID,Patient Name,Govt ID,Age,Hospital Center,Pincode,Vaccine,Dose,Slot Time,Date,Status,Booked Timestamp,Vaccinated Timestamp\n");

            for (Appointment a : dataStore.getFastLookup().getAllAppointments()) {
                csv.append(escapeCsv(a.getTokenId())).append(",")
                   .append(escapeCsv(a.getPatientName())).append(",")
                   .append(escapeCsv(a.getPatientId())).append(",")
                   .append(a.getPatientAge()).append(",")
                   .append(escapeCsv(a.getCenterName())).append(",")
                   .append(escapeCsv(a.getPincode())).append(",")
                   .append(escapeCsv(a.getVaccineType())).append(",")
                   .append(a.getDoseNumber()).append(",")
                   .append(escapeCsv(a.getSlotTime())).append(",")
                   .append(escapeCsv(a.getDate())).append(",")
                   .append(escapeCsv(a.getStatus())).append(",")
                   .append(escapeCsv(a.getBookedAt())).append(",")
                   .append(escapeCsv(a.getVaccinatedAt())).append("\n");
            }

            byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"vaccination_audit_report.csv\"");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

        private String escapeCsv(String s) {
            if (s == null) return "\"\"";
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
    }

    // --- API: Admin / Doctor Authentication ---
    static class AdminLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, JsonUtils.errorResponse("Method Not Allowed"));
                return;
            }

            String body = readRequestBody(exchange);
            String username = JsonUtils.getString(body, "username");
            String password = JsonUtils.getString(body, "password");

            if (ADMIN_USER.equals(username) && ADMIN_PASS.equals(password)) {
                String token = "AUTH-" + UUID.randomUUID().toString();
                activeAdminSessions.add(token);
                String resp = String.format("{\"success\":true,\"token\":\"%s\",\"message\":\"Staff authenticated successfully.\"}", token);
                sendJsonResponse(exchange, 200, resp);
            } else {
                sendJsonResponse(exchange, 401, JsonUtils.errorResponse("Invalid Staff ID or Password. Access denied."));
            }
        }
    }

    // --- API: Get All Appointments (Protected for Hospital Staff) ---
    static class AppointmentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!isAuthorizedStaff(exchange)) {
                sendJsonResponse(exchange, 401, JsonUtils.errorResponse("Unauthorized. Doctor/Admin login required to view patient records."));
                return;
            }

            StringBuilder sb = new StringBuilder("[\n");
            Collection<Appointment> appts = dataStore.getFastLookup().getAllAppointments();
            int i = 0;
            for (Appointment a : appts) {
                sb.append("  ").append(a.toJson());
                if (i < appts.size() - 1) sb.append(",\n");
                i++;
            }
            sb.append("\n]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // --- API: Verify Appointment & Mark Vaccinated (Protected for Hospital Staff) ---
    static class VerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, JsonUtils.errorResponse("Method Not Allowed"));
                return;
            }

            if (!isAuthorizedStaff(exchange)) {
                sendJsonResponse(exchange, 401, JsonUtils.errorResponse("Unauthorized. Doctor/Admin authentication required."));
                return;
            }

            String body = readRequestBody(exchange);
            String query = JsonUtils.getString(body, "query");
            boolean verified = bookingService.verifyAndVaccinate(query);

            if (verified) {
                sendJsonResponse(exchange, 200, JsonUtils.successResponse("Patient verified and marked as VACCINATED successfully!", null));
            } else {
                sendJsonResponse(exchange, 404, JsonUtils.errorResponse("No active confirmed appointment found for the given Token/Aadhaar ID."));
            }
        }
    }

    // --- API: Get Priority Waitlist Entries ---
    static class WaitlistHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            List<PriorityWaitlist.WaitlistEntry> list = dataStore.getPriorityWaitlist().getOrderedList();
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append("  ").append(list.get(i).toJson());
                if (i < list.size() - 1) sb.append(",\n");
            }
            sb.append("\n]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    // --- API: System Stats ---
    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            int totalCenters = dataStore.getCenters().size();
            int totalAvailableSlots = 0;
            for (Center c : dataStore.getCenters().values()) {
                totalAvailableSlots += c.getTotalAvailableSlots();
            }

            int totalBooked = 0;
            int totalVaccinated = 0;
            for (Appointment a : dataStore.getFastLookup().getAllAppointments()) {
                if ("CONFIRMED".equalsIgnoreCase(a.getStatus())) totalBooked++;
                else if ("VACCINATED".equalsIgnoreCase(a.getStatus())) totalVaccinated++;
            }
            int waitlistSize = dataStore.getPriorityWaitlist().size();

            String statsJson = String.format(
                "{\"totalCenters\":%d,\"availableSlots\":%d,\"totalBooked\":%d,\"totalVaccinated\":%d,\"waitlistSize\":%d}",
                totalCenters, totalAvailableSlots, totalBooked, totalVaccinated, waitlistSize
            );
            sendJsonResponse(exchange, 200, statsJson);
        }
    }

    // --- Helper Methods ---
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static boolean isAuthorizedStaff(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            return activeAdminSessions.contains(token);
        }
        return false;
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    // --- API: Azure OpenAI Healthcare Assistant ("Aarogya AI") ---
    static class AiChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            String prompt = JsonUtils.getString(body, "prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                prompt = JsonUtils.getString(body, "message");
            }

            String advice = azureAiService.getClinicalAdvice(prompt);
            boolean isConfigured = azureAiService.isConfigured();

            String jsonResponse = String.format(
                "{\"success\":true,\"reply\":\"%s\",\"azureConnected\":%b}",
                JsonUtils.escape(advice), isConfigured
            );
            sendJsonResponse(exchange, 200, jsonResponse);
        }
    }

    private static String getQueryParam(String query, String param) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length == 2 && parts[0].equalsIgnoreCase(param)) {
                try {
                    return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return parts[1];
                }
            }
        }
        return null;
    }
}
