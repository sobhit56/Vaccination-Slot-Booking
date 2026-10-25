package service;

import dsa.IntervalScheduler;
import dsa.PriorityWaitlist;
import model.Appointment;
import model.Center;
import model.Patient;
import model.Slot;
import util.JsonUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BookingService handles atomic slot booking, concurrency locks,
 * clinical interval verification, waitlist management with Max-Heap promotion,
 * duplicate booking protection, and appointment tracking.
 */
public class BookingService {
    private final DataStore dataStore;
    private final ReentrantLock bookingLock = new ReentrantLock();

    public BookingService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public static class BookingResult {
        public boolean success;
        public boolean waitlisted;
        public String message;
        public Appointment appointment;
        public PriorityWaitlist.WaitlistEntry waitlistEntry;
        public long executionTimeMicros;
        public int waitlistPosition;

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"success\":").append(success).append(",");
            sb.append("\"waitlisted\":").append(waitlisted).append(",");
            sb.append("\"message\":\"").append(JsonUtils.escape(message)).append("\",");
            sb.append("\"executionTimeMicros\":").append(executionTimeMicros);

            if (appointment != null) {
                sb.append(",\"appointment\":").append(appointment.toJson());
            }
            if (waitlistEntry != null) {
                sb.append(",\"waitlist\":").append(waitlistEntry.toJson());
                sb.append(",\"waitlistPosition\":").append(waitlistPosition);
            }
            sb.append("}");
            return sb.toString();
        }
    }

    public static class CancellationResult {
        public boolean success;
        public String message;
        public boolean patientPromoted;
        public Appointment promotedAppointment;

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"success\":").append(success).append(",");
            sb.append("\"patientPromoted\":").append(patientPromoted).append(",");
            sb.append("\"message\":\"").append(JsonUtils.escape(message)).append("\"");
            if (promotedAppointment != null) {
                sb.append(",\"promotedAppointment\":").append(promotedAppointment.toJson());
            }
            sb.append("}");
            return sb.toString();
        }
    }

    public static class RescheduleResult {
        public boolean success;
        public String message;
        public Appointment updatedAppointment;

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"success\":").append(success).append(",");
            sb.append("\"message\":\"").append(JsonUtils.escape(message)).append("\"");
            if (updatedAppointment != null) {
                sb.append(",\"appointment\":").append(updatedAppointment.toJson());
            }
            sb.append("}");
            return sb.toString();
        }
    }

    public static class TrackingResult {
        public boolean found;
        public String status; // CONFIRMED, VACCINATED, CANCELLED, WAITLISTED, NOT_FOUND
        public String message;
        public Appointment appointment;
        public int waitlistPosition;
        public PriorityWaitlist.WaitlistEntry waitlistEntry;

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"found\":").append(found).append(",");
            sb.append("\"status\":\"").append(status != null ? status : "NOT_FOUND").append("\",");
            sb.append("\"message\":\"").append(JsonUtils.escape(message)).append("\"");

            if (appointment != null) {
                sb.append(",\"appointment\":").append(appointment.toJson());
            }
            if (waitlistEntry != null) {
                sb.append(",\"waitlist\":").append(waitlistEntry.toJson());
                sb.append(",\"waitlistPosition\":").append(waitlistPosition);
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Attempts to book a slot for a patient.
     * Thread-safe with ReentrantLock to prevent race conditions & double-booking.
     * Enforces Dose 2 interval constraints and duplicate registration guards.
     */
    public BookingResult bookSlot(String patientId, String name, int age, String phone,
                                 boolean hasComorbidity, String centerId, String slotId, String date,
                                 int doseNumber, String dose1Date) {
        return bookSlot(patientId, name, age, phone, "", hasComorbidity, centerId, slotId, date, doseNumber, dose1Date);
    }

    public BookingResult bookSlot(String patientId, String name, int age, String phone, String email,
                                 boolean hasComorbidity, String centerId, String slotId, String date,
                                 int doseNumber, String dose1Date) {
        long startTime = System.nanoTime();
        BookingResult result = new BookingResult();

        // 1. Basic Field Validation
        if (patientId == null || patientId.trim().isEmpty() ||
            name == null || name.trim().isEmpty() ||
            centerId == null || slotId == null) {
            result.success = false;
            result.message = "Missing required patient or center information.";
            result.executionTimeMicros = (System.nanoTime() - startTime) / 1000;
            return result;
        }

        String cleanId = patientId.trim().replaceAll("\\s+", "").replaceAll("-", "");
        String cleanPhone = (phone != null) ? phone.trim().replaceAll("\\s+", "").replaceAll("-", "") : "";

        // 2. Strict ID & Phone Format Guards
        if (cleanId.length() < 4) {
            result.success = false;
            result.message = "Please enter a valid Government Photo ID / Aadhaar number (min 4 characters).";
            result.executionTimeMicros = (System.nanoTime() - startTime) / 1000;
            return result;
        }

        if (!cleanPhone.isEmpty() && cleanPhone.length() == 10 && !cleanPhone.matches("^[6-9]\\d{9}$")) {
            result.success = false;
            result.message = "Please enter a valid 10-digit mobile number starting with 6, 7, 8, or 9.";
            result.executionTimeMicros = (System.nanoTime() - startTime) / 1000;
            return result;
        }

        // 3. Duplicate Booking Prevention Rules
        List<Appointment> existingAppts = dataStore.getFastLookup().getAppointmentsByPatient(cleanId);
        for (Appointment a : existingAppts) {
            if ("CONFIRMED".equalsIgnoreCase(a.getStatus())) {
                result.success = false;
                result.message = "Duplicate Booking Prevented: Patient already has an active confirmed appointment (Token: " + a.getTokenId() + "). Multiple active bookings are prohibited.";
                result.executionTimeMicros = (System.nanoTime() - startTime) / 1000;
                return result;
            }
        }

        // Dose progression check
        if (doseNumber == 1) {
            for (Appointment a : existingAppts) {
                if ("VACCINATED".equalsIgnoreCase(a.getStatus()) && a.getDoseNumber() == 1) {
                    result.success = false;
                    result.message = "Vaccination History Notice: Patient has already completed Dose 1 (Certificate Token: " + a.getTokenId() + "). Please select Dose 2 instead.";
                    result.executionTimeMicros = (System.nanoTime() - startTime) / 1000;
                    return result;
                }
            }
        } else if (doseNumber >= 2) {
            for (Appointment a : existingAppts) {
                if ("VACCINATED".equalsIgnoreCase(a.getStatus()) && a.getDoseNumber() >= 2) {
                    result.success = false;
                    result.message = "Full Immunization Notice: Patient record shows Dose 2 has already been completed. Patient is fully immunized.";
                    result.executionTimeMicros = (System.nanoTime() - startTime) / 1000;
                    return result;
                }
            }
        }

        Center center = dataStore.getCenterById(centerId);
        if (center == null) {
            result.success = false;
            result.message = "Vaccination center not found.";
            result.executionTimeMicros = (System.nanoTime() - startTime) / 1000;
            return result;
        }

        Slot slot = center.findSlotById(slotId);
        if (slot == null) {
            result.success = false;
            result.message = "Selected time slot does not exist.";
            result.executionTimeMicros = (System.nanoTime() - startTime) / 1000;
            return result;
        }

        // 4. Dose 2 Clinical Interval Verification Algorithm
        if (doseNumber >= 2) {
            IntervalScheduler.IntervalValidationResult intervalCheck = 
                IntervalScheduler.validateDoseInterval(center.getVaccineType(), doseNumber, dose1Date, date);
            if (!intervalCheck.eligible) {
                result.success = false;
                result.message = intervalCheck.message;
                result.executionTimeMicros = (System.nanoTime() - startTime) / 1000;
                return result;
            }
        }

        Patient patient = new Patient(cleanId, name.trim(), age, cleanPhone, hasComorbidity);
        dataStore.savePatient(patient);

        // 5. Acquire Lock for Critical Section (Anti-Double Booking)
        bookingLock.lock();
        try {
            if (slot.isAvailable()) {
                // Book slot
                slot.bookOneSlot();

                String tokenId = generateTokenId();
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String appointmentDate = (date != null && !date.trim().isEmpty()) ? date : new SimpleDateFormat("yyyy-MM-dd").format(new Date());

                Appointment appt = new Appointment(
                    tokenId, patient.getId(), patient.getName(), patient.getAge(),
                    center.getId(), center.getName(), center.getPincode(), center.getVaccineType(),
                    slot.getTimeWindow(), appointmentDate, doseNumber, "CONFIRMED", timestamp
                );
                appt.setPatientPhone(cleanPhone);
                String cleanEmail = (email != null) ? email.trim() : "";
                appt.setPatientEmail(cleanEmail);

                dataStore.saveAppointment(appt);
                dataStore.saveCentersToDisk();

                result.success = true;
                result.waitlisted = false;
                result.message = "Slot booked successfully! Appointment confirmed.";
                result.appointment = appt;

                // Automatically dispatch real SMS via Fast2SMS gateway
                SmsService.sendAppointmentConfirmation(
                    cleanPhone, patient.getName(), appt.getTokenId(),
                    center.getName(), appt.getDate(), slot.getTimeWindow(),
                    appt.getVaccineType(), doseNumber
                );

                // Automatically dispatch official digital pass via Azure Email Service
                if (!cleanEmail.isEmpty()) {
                    AzureEmailService.sendAppointmentPassAsync(
                        cleanEmail, patient.getName(), appt.getTokenId(),
                        center.getName(), center.getAddress(), center.getPincode(),
                        appt.getVaccineType(), doseNumber, appt.getDate(), slot.getTimeWindow(),
                        cleanId
                    );
                }
            } else {
                // Slot is FULL -> Place in Max-Heap Priority Waitlist
                PriorityWaitlist.WaitlistEntry entry = new PriorityWaitlist.WaitlistEntry(
                    patient, center.getId(), center.getName(), slot.getTimeWindow()
                );
                dataStore.getPriorityWaitlist().insert(entry);

                result.success = true;
                result.waitlisted = true;
                result.message = "Slot full. Patient placed in Priority Waitlist based on age and clinical priority.";
                result.waitlistEntry = entry;
                result.waitlistPosition = dataStore.getPriorityWaitlist().getWaitlistPosition(cleanId);

                // Automatically dispatch real waitlist SMS
                SmsService.sendWaitlistNotification(
                    cleanPhone, patient.getName(), center.getName(), result.waitlistPosition
                );
            }
        } finally {
            bookingLock.unlock();
        }

        result.executionTimeMicros = Math.max(1, (System.nanoTime() - startTime) / 1000);
        return result;
    }

    /**
     * Track booking self-service for patients by Token ID, Patient ID, or Phone.
     */
    public TrackingResult trackBooking(String query) {
        TrackingResult tr = new TrackingResult();
        if (query == null || query.trim().isEmpty()) {
            tr.found = false;
            tr.status = "NOT_FOUND";
            tr.message = "Please enter a Token ID, Aadhaar number, or Phone number.";
            return tr;
        }

        String q = query.trim().toUpperCase();

        // 1. Search by Token ID
        Appointment appt = dataStore.getFastLookup().getAppointmentByToken(q);

        // 2. Search by Patient ID / Aadhaar
        if (appt == null) {
            List<Appointment> list = dataStore.getFastLookup().getAppointmentsByPatient(query.trim());
            if (!list.isEmpty()) {
                // Sort by latest appointment
                appt = list.get(list.size() - 1);
            }
        }

        // 3. Search by Phone number or case-insensitive match across appointments
        if (appt == null) {
            for (Appointment a : dataStore.getFastLookup().getAllAppointments()) {
                if (q.equalsIgnoreCase(a.getPatientId()) || q.equalsIgnoreCase(a.getTokenId())) {
                    appt = a;
                    break;
                }
            }
        }

        // 4. Check if currently in Priority Waitlist
        PriorityWaitlist.WaitlistEntry waitlistEntry = dataStore.getPriorityWaitlist().getWaitlistEntry(query.trim());
        if (waitlistEntry == null) {
            waitlistEntry = dataStore.getPriorityWaitlist().getWaitlistEntry(q);
        }

        if (waitlistEntry != null) {
            tr.found = true;
            tr.status = "WAITLISTED";
            tr.waitlistEntry = waitlistEntry;
            tr.waitlistPosition = dataStore.getPriorityWaitlist().getWaitlistPosition(waitlistEntry.getPatientId());
            tr.message = String.format("Patient %s is currently on the Priority Waitlist at Position #%d for %s.",
                waitlistEntry.getPatientName(), tr.waitlistPosition, waitlistEntry.getCenterName());
            return tr;
        }

        if (appt != null) {
            tr.found = true;
            tr.status = appt.getStatus();
            tr.appointment = appt;
            if ("CONFIRMED".equalsIgnoreCase(appt.getStatus())) {
                tr.message = "Active appointment confirmed. You can view or print your digital appointment slip.";
            } else if ("VACCINATED".equalsIgnoreCase(appt.getStatus())) {
                tr.message = "Vaccination completed. Official digital certificate is ready for download.";
            } else if ("CANCELLED".equalsIgnoreCase(appt.getStatus())) {
                tr.message = "This appointment was cancelled.";
            }
            return tr;
        }

        tr.found = false;
        tr.status = "NOT_FOUND";
        tr.message = "No appointment or waitlist record found matching: " + query.trim();
        return tr;
    }

    /**
     * Reschedules an active confirmed appointment to a new slot window or date.
     */
    public RescheduleResult rescheduleAppointment(String tokenId, String newSlotId, String newDate) {
        RescheduleResult res = new RescheduleResult();
        if (tokenId == null || tokenId.trim().isEmpty() || newSlotId == null) {
            res.success = false;
            res.message = "Missing appointment token or new slot selection.";
            return res;
        }

        bookingLock.lock();
        try {
            Appointment appt = dataStore.getFastLookup().getAppointmentByToken(tokenId.trim());
            if (appt == null) {
                res.success = false;
                res.message = "Appointment not found for token: " + tokenId;
                return res;
            }

            if (!"CONFIRMED".equalsIgnoreCase(appt.getStatus())) {
                res.success = false;
                res.message = "Only active confirmed appointments can be rescheduled.";
                return res;
            }

            Center center = dataStore.getCenterById(appt.getCenterId());
            if (center == null) {
                res.success = false;
                res.message = "Vaccination center not found.";
                return res;
            }

            Slot targetSlot = center.findSlotById(newSlotId);
            if (targetSlot == null) {
                res.success = false;
                res.message = "Selected time slot does not exist.";
                return res;
            }

            if (!targetSlot.isAvailable()) {
                res.success = false;
                res.message = "The selected new slot is currently full. Please pick another available slot.";
                return res;
            }

            // Release 1 slot from old time window
            for (Slot s : center.getSlots()) {
                if (s.getTimeWindow().equalsIgnoreCase(appt.getSlotTime())) {
                    s.cancelOneSlot();
                    break;
                }
            }

            // Book 1 slot in new window
            targetSlot.bookOneSlot();

            appt.setSlotTime(targetSlot.getTimeWindow());
            if (newDate != null && !newDate.trim().isEmpty()) {
                appt.setDate(newDate.trim());
            }

            dataStore.saveAppointment(appt);
            dataStore.saveCentersToDisk();

            // Automatically dispatch reschedule SMS
            SmsService.sendRescheduleNotification(
                appt.getPatientPhone(), appt.getPatientName(), appt.getTokenId(),
                center.getName(), appt.getDate(), appt.getSlotTime()
            );

            res.success = true;
            res.message = "Appointment rescheduled successfully to " + appt.getDate() + " (" + appt.getSlotTime() + ").";
            res.updatedAppointment = appt;
            return res;

        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Admin Direct Emergency Walk-in Registration.
     */
    public BookingResult registerWalkin(String patientId, String name, int age, String phone,
                                       boolean hasComorbidity, String centerId, String slotId,
                                       int doseNumber, boolean autoVaccinate) {
        long startTime = System.nanoTime();
        BookingResult result = new BookingResult();

        if (patientId == null || patientId.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            result.success = false;
            result.message = "Patient name and ID are required for walk-in registration.";
            return result;
        }

        String cleanId = patientId.trim().replaceAll("\\s+", "").replaceAll("-", "");
        Center center = dataStore.getCenterById(centerId);
        if (center == null) {
            List<Center> centers = dataStore.getAllCenters();
            if (!centers.isEmpty()) center = centers.get(0);
        }

        Slot slot = (center != null) ? center.findSlotById(slotId) : null;
        if (slot == null && center != null && !center.getSlots().isEmpty()) {
            slot = center.getSlots().get(0);
        }

        bookingLock.lock();
        try {
            Patient patient = new Patient(cleanId, name.trim(), age, phone, hasComorbidity);
            dataStore.savePatient(patient);

            if (slot != null && slot.isAvailable()) {
                slot.bookOneSlot();
            }

            String tokenId = generateTokenId();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            String status = autoVaccinate ? "VACCINATED" : "CONFIRMED";

            Appointment appt = new Appointment(
                tokenId, patient.getId(), patient.getName(), patient.getAge(),
                (center != null ? center.getId() : "C1"),
                (center != null ? center.getName() : "Emergency Walk-in Center"),
                (center != null ? center.getPincode() : "110001"),
                (center != null ? center.getVaccineType() : "Covishield"),
                (slot != null ? slot.getTimeWindow() : "Walk-in Priority"),
                today, doseNumber, status, timestamp
            );

            if (autoVaccinate) {
                appt.setVaccinatedAt(timestamp);
            }

            dataStore.saveAppointment(appt);
            dataStore.saveCentersToDisk();

            result.success = true;
            result.waitlisted = false;
            result.message = autoVaccinate 
                ? "Emergency Walk-in successfully registered & vaccinated! Official certificate generated."
                : "Emergency Walk-in appointment registered successfully.";
            result.appointment = appt;
        } finally {
            bookingLock.unlock();
        }

        result.executionTimeMicros = Math.max(1, (System.nanoTime() - startTime) / 1000);
        return result;
    }

    /**
     * Admin Slot Capacity Increase.
     * If patients are waiting in the Max-Heap, automatically promotes the top waitlisted patient!
     */
    public boolean increaseSlotCapacity(String centerId, String slotId, int addedCapacity) {
        if (addedCapacity <= 0) return false;

        bookingLock.lock();
        try {
            Center center = dataStore.getCenterById(centerId);
            if (center == null) return false;

            Slot slot = center.findSlotById(slotId);
            if (slot == null) return false;

            slot.addCapacity(addedCapacity);

            // If there are waitlisted patients, auto-promote them!
            PriorityWaitlist waitlist = dataStore.getPriorityWaitlist();
            while (!waitlist.isEmpty() && slot.isAvailable()) {
                PriorityWaitlist.WaitlistEntry top = waitlist.extractMax();
                if (top == null) break;

                slot.bookOneSlot();
                String newToken = generateTokenId();
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

                Appointment promotedAppt = new Appointment(
                    newToken, top.getPatientId(), top.getPatientName(), top.getAge(),
                    center.getId(), center.getName(), center.getPincode(), center.getVaccineType(),
                    slot.getTimeWindow(), today, 1, "CONFIRMED", timestamp
                );
                dataStore.saveAppointment(promotedAppt);
            }

            dataStore.saveCentersToDisk();
            return true;
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Cancels an appointment and automatically triggers Max-Heap Promotion:
     * If patients are waiting in the Max-Heap, extractMax() promotes the highest
     * priority patient into the newly opened slot!
     */
    public CancellationResult cancelAppointment(String tokenId) {
        CancellationResult res = new CancellationResult();
        if (tokenId == null || tokenId.trim().isEmpty()) {
            res.success = false;
            res.message = "Token ID is required.";
            return res;
        }

        bookingLock.lock();
        try {
            Appointment appt = dataStore.getFastLookup().getAppointmentByToken(tokenId.trim());
            if (appt == null) {
                res.success = false;
                res.message = "Appointment not found for token: " + tokenId;
                return res;
            }

            if ("CANCELLED".equalsIgnoreCase(appt.getStatus())) {
                res.success = false;
                res.message = "Appointment is already cancelled.";
                return res;
            }

            if ("VACCINATED".equalsIgnoreCase(appt.getStatus())) {
                res.success = false;
                res.message = "Cannot cancel an appointment that has already been vaccinated.";
                return res;
            }

            // 1. Mark as CANCELLED
            appt.setStatus("CANCELLED");
            dataStore.saveAppointment(appt);

            Center center = dataStore.getCenterById(appt.getCenterId());
            Slot slot = (center != null) ? center.findSlotById("S1") : null;
            if (center != null) {
                for (Slot s : center.getSlots()) {
                    if (s.getTimeWindow().equalsIgnoreCase(appt.getSlotTime())) {
                        slot = s;
                        break;
                    }
                }
            }

            // 2. Max-Heap Promotion (extractMax)
            PriorityWaitlist waitlist = dataStore.getPriorityWaitlist();
            if (!waitlist.isEmpty()) {
                PriorityWaitlist.WaitlistEntry top = waitlist.extractMax();

                String newToken = generateTokenId();
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

                Appointment promotedAppt = new Appointment(
                    newToken, top.getPatientId(), top.getPatientName(), top.getAge(),
                    appt.getCenterId(), appt.getCenterName(), appt.getPincode(), appt.getVaccineType(),
                    appt.getSlotTime(), appt.getDate(), 1, "CONFIRMED", timestamp
                );

                dataStore.saveAppointment(promotedAppt);
                res.patientPromoted = true;
                res.promotedAppointment = promotedAppt;
                res.message = String.format(
                    "Appointment %s cancelled. Slot reassigned: Next eligible waitlisted patient %s was automatically scheduled into this slot.",
                    tokenId, top.getPatientName()
                );

                // Automatically dispatch real SMS to promoted patient
                Patient promotedPatient = dataStore.getFastLookup().getPatient(top.getPatientId());
                if (promotedPatient != null && promotedPatient.getPhone() != null && !promotedPatient.getPhone().isEmpty()) {
                    promotedAppt.setPatientPhone(promotedPatient.getPhone());
                    SmsService.sendPromotionNotification(
                        promotedPatient.getPhone(), top.getPatientName(), newToken,
                        appt.getCenterName(), appt.getDate(), appt.getSlotTime()
                    );
                }
            } else {
                // No one in waitlist, return 1 slot back to pool
                if (slot != null) {
                    slot.cancelOneSlot();
                }
                res.patientPromoted = false;
                res.message = "Appointment " + tokenId + " cancelled. Slot returned to availability pool.";
            }

            dataStore.saveCentersToDisk();
            res.success = true;
            return res;

        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Verifies patient appointment and marks as vaccinated (Doctor/Admin check-in).
     */
    public boolean verifyAndVaccinate(String tokenIdOrPatientId) {
        if (tokenIdOrPatientId == null) return false;
        String query = tokenIdOrPatientId.trim().toUpperCase();

        Appointment appt = dataStore.getFastLookup().getAppointmentByToken(query);
        if (appt == null) {
            List<Appointment> list = dataStore.getFastLookup().getAppointmentsByPatient(tokenIdOrPatientId.trim());
            for (Appointment a : list) {
                if ("CONFIRMED".equalsIgnoreCase(a.getStatus())) {
                    appt = a;
                    break;
                }
            }
        }

        if (appt != null && "CONFIRMED".equalsIgnoreCase(appt.getStatus())) {
            appt.setStatus("VACCINATED");
            appt.setVaccinatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            dataStore.saveAppointment(appt);
            return true;
        }

        return false;
    }

    private String generateTokenId() {
        int randomNum = 1000 + new Random().nextInt(9000);
        return "VAC-" + new SimpleDateFormat("yyyy").format(new Date()) + "-" + randomNum;
    }
}
