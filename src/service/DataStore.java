package service;

import dsa.CircularQueue;
import dsa.FastLookup;
import dsa.PriorityWaitlist;
import dsa.Trie;
import model.Appointment;
import model.Center;
import model.Patient;
import model.Slot;
import util.JsonUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DataStore manages In-Memory Data Structures and Local Disk Persistence (data/ folder).
 */
public class DataStore {
    private static final String DATA_DIR = "data";
    private static final String CENTERS_FILE = "data/centers.json";
    private static final String PATIENTS_FILE = "data/patients.json";
    private static final String APPOINTMENTS_FILE = "data/appointments.json";

    private final Map<String, Center> centers;
    private final Trie centerTrie;
    private final PriorityWaitlist priorityWaitlist;
    private final CircularQueue<String> virtualWaitingRoom;
    private final FastLookup fastLookup;

    public DataStore() {
        this.centers = new ConcurrentHashMap<>();
        this.centerTrie = new Trie();
        this.priorityWaitlist = new PriorityWaitlist();
        this.virtualWaitingRoom = new CircularQueue<>(100);
        this.fastLookup = new FastLookup();

        ensureDataDir();
        loadOrInitializeCenters();
        loadAppointments();
    }

    private void ensureDataDir() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public Trie getCenterTrie() { return centerTrie; }
    public PriorityWaitlist getPriorityWaitlist() { return priorityWaitlist; }
    public CircularQueue<String> getVirtualWaitingRoom() { return virtualWaitingRoom; }
    public FastLookup getFastLookup() { return fastLookup; }
    public Map<String, Center> getCenters() { return centers; }

    public List<Center> getAllCenters() {
        return new ArrayList<>(centers.values());
    }

    public Center getCenterById(String centerId) {
        if (centerId == null) return null;
        return centers.get(centerId.trim());
    }

    private void loadOrInitializeCenters() {
        File f = new File(CENTERS_FILE);
        if (!f.exists()) {
            seedDefaultCenters();
            saveCentersToDisk();
        } else {
            // For this lightweight version, if disk file exists, seed and sync
            seedDefaultCenters();
        }

        // Index all centers in Trie
        for (Center c : centers.values()) {
            centerTrie.indexCenter(c);
        }
    }

    private void seedDefaultCenters() {
        // Center 1: New Delhi (AIIMS)
        Center c1 = new Center("C101", "AIIMS Community Health Center", "Ansari Nagar, Ring Road", "110029", "New Delhi", "Covishield", 28.5672, 77.2100);
        c1.addSlot(new Slot("S1", "09:00 - 10:00 AM", 8, 2));
        c1.addSlot(new Slot("S2", "10:00 - 11:00 AM", 8, 5));
        c1.addSlot(new Slot("S3", "11:00 - 12:00 PM", 6, 6)); // Full slot to test waitlist
        c1.addSlot(new Slot("S4", "02:00 - 03:00 PM", 10, 3));
        centers.put(c1.getId(), c1);

        // Center 2: New Delhi (Max Saket)
        Center c2 = new Center("C102", "Max Super Speciality Hospital", "Saket District Centre", "110017", "New Delhi", "Covaxin", 28.5284, 77.2127);
        c2.addSlot(new Slot("S1", "09:30 - 10:30 AM", 10, 4));
        c2.addSlot(new Slot("S2", "11:30 - 12:30 PM", 10, 9));
        c2.addSlot(new Slot("S3", "03:00 - 04:00 PM", 12, 2));
        centers.put(c2.getId(), c2);

        // Center 3: Bengaluru (Apollo Bannerghatta)
        Center c3 = new Center("C103", "Apollo Multispeciality Hospital", "Bannerghatta Main Road", "560076", "Bengaluru", "Pfizer", 12.8953, 77.5988);
        c3.addSlot(new Slot("S1", "09:00 - 10:00 AM", 12, 3));
        c3.addSlot(new Slot("S2", "10:30 - 11:30 AM", 12, 11));
        c3.addSlot(new Slot("S3", "02:30 - 03:30 PM", 15, 6));
        centers.put(c3.getId(), c3);

        // Center 4: Bengaluru (Manipal Old Airport Rd)
        Center c4 = new Center("C104", "Manipal Hospital", "HAL Old Airport Road", "560017", "Bengaluru", "Covishield", 12.9582, 77.6506);
        c4.addSlot(new Slot("S1", "10:00 - 11:00 AM", 8, 8)); // Full
        c4.addSlot(new Slot("S2", "01:00 - 02:00 PM", 8, 2));
        c4.addSlot(new Slot("S3", "04:00 - 05:00 PM", 10, 1));
        centers.put(c4.getId(), c4);

        // Center 5: Mumbai (Lilavati Bandra)
        Center c5 = new Center("C105", "Lilavati Hospital & Research Centre", "Bandra Reclamation, Bandra West", "400050", "Mumbai", "Covaxin", 19.0522, 72.8295);
        c5.addSlot(new Slot("S1", "09:00 - 10:00 AM", 10, 3));
        c5.addSlot(new Slot("S2", "11:00 - 12:00 PM", 10, 7));
        c5.addSlot(new Slot("S3", "02:00 - 03:00 PM", 10, 10)); // Full
        centers.put(c5.getId(), c5);
    }

    public synchronized void saveCentersToDisk() {
        try {
            StringBuilder sb = new StringBuilder("[\n");
            int i = 0;
            for (Center c : centers.values()) {
                sb.append("  ").append(c.toJson());
                if (i < centers.size() - 1) sb.append(",\n");
                i++;
            }
            sb.append("\n]");
            Files.write(Paths.get(CENTERS_FILE), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Failed to save centers: " + e.getMessage());
        }
    }

    public synchronized void saveAppointment(Appointment appt) {
        fastLookup.indexAppointment(appt);
        saveAppointmentsToDisk();
    }

    public synchronized void savePatient(Patient patient) {
        fastLookup.indexPatient(patient);
        savePatientsToDisk();
    }

    private synchronized void saveAppointmentsToDisk() {
        try {
            StringBuilder sb = new StringBuilder("[\n");
            Collection<Appointment> list = fastLookup.getAllAppointments();
            int i = 0;
            for (Appointment a : list) {
                sb.append("  ").append(a.toJson());
                if (i < list.size() - 1) sb.append(",\n");
                i++;
            }
            sb.append("\n]");
            Files.write(Paths.get(APPOINTMENTS_FILE), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Failed to save appointments: " + e.getMessage());
        }
    }

    private synchronized void savePatientsToDisk() {
        try {
            StringBuilder sb = new StringBuilder("[\n");
            // Save registered patients
            int i = 0;
            for (Appointment a : fastLookup.getAllAppointments()) {
                Patient p = fastLookup.getPatient(a.getPatientId());
                if (p != null) {
                    sb.append("  ").append(p.toJson());
                    if (i < fastLookup.getAllAppointments().size() - 1) sb.append(",\n");
                    i++;
                }
            }
            sb.append("\n]");
            Files.write(Paths.get(PATIENTS_FILE), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Failed to save patients: " + e.getMessage());
        }
    }

    private void loadAppointments() {
        // If file exists, we can read previously saved appointments
        File f = new File(APPOINTMENTS_FILE);
        if (!f.exists()) return;
        try {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            // Basic parsing of appointment objects
            String[] parts = content.split("\\},\\s*\\{");
            for (String p : parts) {
                String token = JsonUtils.getString(p, "tokenId");
                String pid = JsonUtils.getString(p, "patientId");
                String pname = JsonUtils.getString(p, "patientName");
                int age = JsonUtils.getInt(p, "patientAge", 30);
                String cid = JsonUtils.getString(p, "centerId");
                String cname = JsonUtils.getString(p, "centerName");
                String pin = JsonUtils.getString(p, "pincode");
                String vax = JsonUtils.getString(p, "vaccineType");
                String slotTime = JsonUtils.getString(p, "slotTime");
                String date = JsonUtils.getString(p, "date");
                int dose = JsonUtils.getInt(p, "doseNumber", 1);
                String status = JsonUtils.getString(p, "status");
                String bookedAt = JsonUtils.getString(p, "bookedAt");
                String vaccinatedAt = JsonUtils.getString(p, "vaccinatedAt");

                if (token != null && pid != null) {
                    Appointment appt = new Appointment(token, pid, pname, age, cid, cname, pin, vax, slotTime, date, dose, status != null ? status : "CONFIRMED", bookedAt != null ? bookedAt : "");
                    if (vaccinatedAt != null) appt.setVaccinatedAt(vaccinatedAt);
                    fastLookup.indexAppointment(appt);

                    Patient patient = new Patient(pid, pname, age, "9876543210", false);
                    fastLookup.indexPatient(patient);
                }
            }
        } catch (Exception e) {
            System.out.println("Note: Starting with fresh appointment memory.");
        }
    }
}
