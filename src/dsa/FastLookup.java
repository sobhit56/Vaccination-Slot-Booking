package dsa;

import model.Appointment;
import model.Patient;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fast Hash-Indexed Lookup Table.
 * Provides O(1) average time complexity for:
 *   - Verifying Patient identity (by Aadhaar / Govt ID)
 *   - Locating Appointment (by Token ID)
 *   - Tracking active bookings per patient to prevent double-booking
 */
public class FastLookup {

    private final Map<String, Patient> patientMap;
    private final Map<String, Appointment> appointmentMap;
    private final Map<String, List<Appointment>> patientAppointments;

    public FastLookup() {
        this.patientMap = new ConcurrentHashMap<>();
        this.appointmentMap = new ConcurrentHashMap<>();
        this.patientAppointments = new ConcurrentHashMap<>();
    }

    public void indexPatient(Patient patient) {
        patientMap.put(patient.getId().trim(), patient);
    }

    public Patient getPatient(String id) {
        if (id == null) return null;
        return patientMap.get(id.trim());
    }

    public void indexAppointment(Appointment appt) {
        appointmentMap.put(appt.getTokenId().trim().toUpperCase(), appt);
        patientAppointments.computeIfAbsent(appt.getPatientId().trim(), k -> new ArrayList<>()).add(appt);
    }

    public Appointment getAppointmentByToken(String tokenId) {
        if (tokenId == null) return null;
        return appointmentMap.get(tokenId.trim().toUpperCase());
    }

    public List<Appointment> getAppointmentsByPatient(String patientId) {
        if (patientId == null) return Collections.emptyList();
        return patientAppointments.getOrDefault(patientId.trim(), Collections.emptyList());
    }

    public boolean hasActiveBooking(String patientId) {
        List<Appointment> list = getAppointmentsByPatient(patientId);
        for (Appointment a : list) {
            if ("CONFIRMED".equalsIgnoreCase(a.getStatus())) {
                return true;
            }
        }
        return false;
    }

    public int getTotalRegisteredPatients() {
        return patientMap.size();
    }

    public int getTotalAppointments() {
        return appointmentMap.size();
    }

    public Collection<Appointment> getAllAppointments() {
        return appointmentMap.values();
    }

    public Collection<Patient> getAllPatients() {
        return patientMap.values();
    }
}
