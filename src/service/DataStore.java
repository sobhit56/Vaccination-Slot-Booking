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
 * Features a nationwide network of top Indian vaccination centers across all zones.
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
        loadPatients();
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
        seedDefaultCenters();
        saveCentersToDisk();

        // Index all centers in Trie (by name, district, and 6-digit pincode)
        for (Center c : centers.values()) {
            centerTrie.indexCenter(c);
        }
    }

    private void addCenterWithStandardSlots(Center c, int cap1, int bk1, int cap2, int bk2, int cap3, int bk3, int cap4, int bk4) {
        c.addSlot(new Slot("S1", "09:00 - 10:00 AM", cap1, bk1));
        c.addSlot(new Slot("S2", "10:30 - 11:30 AM", cap2, bk2));
        c.addSlot(new Slot("S3", "02:00 - 03:00 PM", cap3, bk3));
        c.addSlot(new Slot("S4", "03:30 - 04:30 PM", cap4, bk4));
        centers.put(c.getId(), c);
    }

    /**
     * Seeds top-tier government and multi-speciality vaccination centers across all zones in India.
     */
    private void seedDefaultCenters() {
        centers.clear();

        // ==========================================
        // 1. NATIONAL CAPITAL REGION & NORTH INDIA
        // ==========================================
        // AIIMS New Delhi
        Center c101 = new Center("C101", "AIIMS Community Health Center", "Ansari Nagar, Ring Road", "110029", "New Delhi, Delhi", "Covishield", 28.5672, 77.2100);
        addCenterWithStandardSlots(c101, 10, 3, 10, 5, 8, 8, 12, 4);

        // Safdarjung Hospital Delhi
        Center c102 = new Center("C102", "Safdarjung Hospital & VMMC", "Mahatma Gandhi Marg, Safdarjung", "110029", "New Delhi, Delhi", "Covaxin", 28.5694, 77.2076);
        addCenterWithStandardSlots(c102, 12, 4, 12, 8, 10, 2, 10, 5);

        // Max Super Speciality Saket Delhi
        Center c103 = new Center("C103", "Max Super Speciality Hospital", "Saket District Centre", "110017", "South Delhi, Delhi", "Covaxin", 28.5284, 77.2127);
        addCenterWithStandardSlots(c103, 10, 4, 10, 9, 12, 2, 10, 1);

        // Sir Ganga Ram Hospital Central Delhi
        Center c104 = new Center("C104", "Sir Ganga Ram Hospital", "Rajinder Nagar", "110060", "Central Delhi, Delhi", "Pfizer", 28.6385, 77.1895);
        addCenterWithStandardSlots(c104, 15, 6, 15, 12, 10, 5, 10, 3);

        // PGIMER Chandigarh
        Center c105 = new Center("C105", "PGIMER Medical Research Hospital", "Sector 12", "160012", "Chandigarh", "Covishield", 30.7645, 76.7766);
        addCenterWithStandardSlots(c105, 14, 5, 14, 10, 12, 4, 10, 2);

        // SMS Hospital Jaipur Rajasthan
        Center c106 = new Center("C106", "Sawai Man Singh (SMS) Hospital", "JLN Marg, Ashok Nagar", "302004", "Jaipur, Rajasthan", "Covishield", 26.9015, 75.8152);
        addCenterWithStandardSlots(c106, 12, 6, 12, 8, 10, 4, 12, 3);

        // SKIMS Srinagar Jammu & Kashmir
        Center c107 = new Center("C107", "SKIMS Healthcare & Research Institute", "Soura", "190011", "Srinagar, Jammu & Kashmir", "Covishield", 34.1352, 74.8025);
        addCenterWithStandardSlots(c107, 10, 3, 10, 6, 8, 2, 8, 1);

        // ==========================================
        // 2. UTTAR PRADESH & CENTRAL INDIA
        // ==========================================
        // KGMU Lucknow
        Center c108 = new Center("C108", "King George's Medical University (KGMU)", "Shah Mina Road, Chowk", "226003", "Lucknow, Uttar Pradesh", "Covishield", 26.8688, 80.9135);
        addCenterWithStandardSlots(c108, 15, 5, 15, 10, 12, 6, 10, 2);

        // Fortis Hospital Noida
        Center c109 = new Center("C109", "Fortis Hospital Sector 62", "B-22, Sector 62", "201301", "Noida, Uttar Pradesh", "Covaxin", 28.6186, 77.3725);
        addCenterWithStandardSlots(c109, 10, 4, 10, 8, 10, 5, 12, 3);

        // IMS BHU Hospital Varanasi
        Center c110 = new Center("C110", "Sir Sunderlal Hospital (IMS BHU)", "Banaras Hindu University Campus", "221005", "Varanasi, Uttar Pradesh", "Covishield", 25.2755, 82.9995);
        addCenterWithStandardSlots(c110, 12, 4, 12, 9, 10, 3, 10, 2);

        // AIIMS Bhopal Madhya Pradesh
        Center c111 = new Center("C111", "AIIMS Bhopal Speciality Center", "Saket Nagar", "462020", "Bhopal, Madhya Pradesh", "Covishield", 23.2081, 77.4589);
        addCenterWithStandardSlots(c111, 12, 5, 12, 7, 10, 4, 10, 2);

        // Bombay Hospital Indore
        Center c112 = new Center("C112", "Bombay Hospital & Research Centre", "Ring Road, IDA Scheme 94", "452010", "Indore, Madhya Pradesh", "Covaxin", 22.7533, 75.8937);
        addCenterWithStandardSlots(c112, 10, 3, 10, 7, 10, 2, 8, 1);

        // ==========================================
        // 3. MAHARASHTRA & GUJARAT (WEST INDIA)
        // ==========================================
        // Lilavati Hospital Mumbai
        Center c113 = new Center("C113", "Lilavati Hospital & Research Centre", "Bandra Reclamation, Bandra West", "400050", "Mumbai, Maharashtra", "Covaxin", 19.0522, 72.8295);
        addCenterWithStandardSlots(c113, 10, 3, 10, 7, 10, 10, 10, 2);

        // KEM Hospital Mumbai
        Center c114 = new Center("C114", "KEM Hospital & Seth GS Medical College", "Acharya Donde Marg, Parel", "400012", "Mumbai, Maharashtra", "Covishield", 19.0028, 72.8423);
        addCenterWithStandardSlots(c114, 15, 6, 15, 11, 12, 5, 10, 4);

        // Ruby Hall Clinic Pune
        Center c115 = new Center("C115", "Ruby Hall Clinic Multispeciality", "40 Sassoon Road, Sangamvadi", "411001", "Pune, Maharashtra", "Covishield", 18.5323, 73.8741);
        addCenterWithStandardSlots(c115, 12, 4, 12, 8, 10, 3, 10, 2);

        // AIIMS Nagpur
        Center c116 = new Center("C116", "AIIMS Nagpur Apex Medical Center", "Plot 2, Sector 20, MIHAN", "441108", "Nagpur, Maharashtra", "Covaxin", 21.0422, 79.0270);
        addCenterWithStandardSlots(c116, 10, 3, 10, 6, 10, 4, 8, 2);

        // Ahmedabad Civil Hospital
        Center c117 = new Center("C117", "Ahmedabad Civil Hospital & BJ Medical", "Asarwa", "380016", "Ahmedabad, Gujarat", "Covaxin", 23.0519, 72.6033);
        addCenterWithStandardSlots(c117, 14, 5, 14, 9, 12, 4, 10, 3);

        // Sterling Hospital Ahmedabad
        Center c118 = new Center("C118", "Sterling Hospital Multispeciality", "Sterling Hospital Road, Memnagar", "380052", "Ahmedabad, Gujarat", "Covishield", 23.0543, 72.5348);
        addCenterWithStandardSlots(c118, 10, 2, 10, 6, 10, 4, 8, 1);

        // ==========================================
        // 4. KARNATAKA, TAMIL NADU & KERALA (SOUTH INDIA)
        // ==========================================
        // Apollo Bannerghatta Bengaluru
        Center c119 = new Center("C119", "Apollo Multispeciality Hospital", "Bannerghatta Main Road", "560076", "Bengaluru, Karnataka", "Pfizer", 12.8953, 77.5988);
        addCenterWithStandardSlots(c119, 12, 3, 12, 11, 15, 6, 10, 2);

        // Manipal Hospital Old Airport Rd Bengaluru
        Center c120 = new Center("C120", "Manipal Hospital", "HAL Old Airport Road, Kodihalli", "560017", "Bengaluru, Karnataka", "Covishield", 12.9582, 77.6506);
        addCenterWithStandardSlots(c120, 10, 8, 10, 3, 12, 4, 10, 1);

        // Victoria Hospital Bengaluru
        Center c121 = new Center("C121", "Victoria Hospital (BMCRI)", "Fort Road, Kalasipalya", "560002", "Bengaluru, Karnataka", "Covaxin", 12.9629, 77.5744);
        addCenterWithStandardSlots(c121, 14, 6, 14, 10, 12, 5, 10, 3);

        // Apollo Hospitals Greams Road Chennai
        Center c122 = new Center("C122", "Apollo Hospitals Greams Road", "21 Greams Lane, Thousand Lights", "600006", "Chennai, Tamil Nadu", "Covishield", 13.0583, 80.2507);
        addCenterWithStandardSlots(c122, 12, 4, 12, 9, 10, 4, 10, 2);

        // Rajiv Gandhi Govt General Hospital Chennai
        Center c123 = new Center("C123", "Rajiv Gandhi Govt General Hospital", "EVR Periyar Salai, Park Town", "600003", "Chennai, Tamil Nadu", "Covaxin", 13.0807, 80.2785);
        addCenterWithStandardSlots(c123, 15, 7, 15, 12, 12, 6, 10, 3);

        // NIMS Hyderabad Telangana
        Center c124 = new Center("C124", "Nizam's Institute of Medical Sciences (NIMS)", "Punjagutta", "500082", "Hyderabad, Telangana", "Covaxin", 17.4243, 78.4526);
        addCenterWithStandardSlots(c124, 12, 4, 12, 8, 10, 5, 10, 2);

        // Apollo Health City Jubilee Hills Hyderabad
        Center c125 = new Center("C125", "Apollo Health City", "Road No 72, Jubilee Hills", "500033", "Hyderabad, Telangana", "Covishield", 17.4265, 78.4118);
        addCenterWithStandardSlots(c125, 12, 3, 12, 8, 12, 4, 10, 1);

        // Amrita Hospital Kochi Kerala
        Center c126 = new Center("C126", "Amrita Institute of Medical Sciences", "AIMS Ponekkara, Edappally", "682041", "Kochi, Kerala", "Covaxin", 10.0328, 76.2942);
        addCenterWithStandardSlots(c126, 10, 4, 10, 7, 10, 3, 8, 2);

        // ==========================================
        // 5. EAST & NORTH-EAST INDIA
        // ==========================================
        // SSKM Hospital Kolkata West Bengal
        Center c127 = new Center("C127", "SSKM Hospital & IPGMER", "244 AJC Bose Road, Bhowanipore", "700020", "Kolkata, West Bengal", "Covishield", 22.5393, 88.3426);
        addCenterWithStandardSlots(c127, 15, 6, 15, 11, 12, 6, 10, 3);

        // Apollo Multispeciality Hospital Kolkata
        Center c128 = new Center("C128", "Apollo Multispeciality Hospital Kolkata", "58 Canal Circular Road, Kadapara", "700054", "Kolkata, West Bengal", "Covaxin", 22.5731, 88.3976);
        addCenterWithStandardSlots(c128, 12, 4, 12, 9, 10, 3, 10, 2);

        // AIIMS Patna Bihar
        Center c129 = new Center("C129", "AIIMS Patna Medical Center", "Phulwari Sharif", "801507", "Patna, Bihar", "Covishield", 25.5606, 85.0444);
        addCenterWithStandardSlots(c129, 15, 5, 15, 10, 12, 4, 10, 3);

        // PMCH Patna Bihar
        Center c130 = new Center("C130", "Patna Medical College Hospital (PMCH)", "Ashok Rajpath, Muradpur", "800004", "Patna, Bihar", "Covaxin", 25.6207, 85.1586);
        addCenterWithStandardSlots(c130, 14, 6, 14, 11, 12, 5, 10, 2);

        // AIIMS Bhubaneswar Odisha
        Center c131 = new Center("C131", "AIIMS Bhubaneswar Apex Hospital", "Sijua, Patrapada", "751019", "Bhubaneswar, Odisha", "Covaxin", 20.2312, 85.7766);
        addCenterWithStandardSlots(c131, 12, 4, 12, 8, 10, 4, 10, 2);

        // GMCH Guwahati Assam
        Center c132 = new Center("C132", "Gauhati Medical College & Hospital (GMCH)", "Narakasur Hilltop, Bhangagarh", "781032", "Guwahati, Assam", "Covishield", 26.1558, 91.7702);
        addCenterWithStandardSlots(c132, 12, 5, 12, 8, 10, 3, 8, 2);
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
            Collection<Patient> list = fastLookup.getAllPatients();
            int i = 0;
            for (Patient p : list) {
                sb.append("  ").append(p.toJson());
                if (i < list.size() - 1) sb.append(",\n");
                i++;
            }
            sb.append("\n]");
            Files.write(Paths.get(PATIENTS_FILE), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Failed to save patients: " + e.getMessage());
        }
    }

    private void loadPatients() {
        File f = new File(PATIENTS_FILE);
        if (!f.exists()) return;
        try {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            if (content.isEmpty() || content.equals("[]")) return;
            String[] parts = content.split("\\},\\s*\\{");
            for (String p : parts) {
                String id = JsonUtils.getString(p, "id");
                String name = JsonUtils.getString(p, "name");
                int age = JsonUtils.getInt(p, "age", 30);
                String phone = JsonUtils.getString(p, "phone");
                boolean comorbidity = JsonUtils.getBoolean(p, "hasComorbidity", false);
                if (id != null && !id.trim().isEmpty() && name != null) {
                    fastLookup.indexPatient(new Patient(id.trim(), name.trim(), age, phone != null ? phone.trim() : "", comorbidity));
                }
            }
        } catch (Exception e) {
            System.out.println("Note: Starting with fresh registered patient data.");
        }
    }

    private void loadAppointments() {
        File f = new File(APPOINTMENTS_FILE);
        if (!f.exists()) return;
        try {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            if (content.isEmpty() || content.equals("[]")) return;
            String[] parts = content.split("\\},\\s*\\{");
            for (String p : parts) {
                String token = JsonUtils.getString(p, "tokenId");
                String pid = JsonUtils.getString(p, "patientId");
                String pname = JsonUtils.getString(p, "patientName");
                int age = JsonUtils.getInt(p, "patientAge", 30);
                String phone = JsonUtils.getString(p, "patientPhone");
                String email = JsonUtils.getString(p, "patientEmail");
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
                    if (phone != null) appt.setPatientPhone(phone);
                    if (email != null) appt.setPatientEmail(email);
                    if (vaccinatedAt != null) appt.setVaccinatedAt(vaccinatedAt);
                    fastLookup.indexAppointment(appt);
                }
            }
        } catch (Exception e) {
            System.out.println("Note: Starting with clean appointment memory.");
        }
    }
}