package model;

import java.util.ArrayList;
import java.util.List;

public class Center {
    private String id;
    private String name;
    private String address;
    private String pincode;
    private String district;
    private String vaccineType;
    private double latitude;
    private double longitude;
    private List<Slot> slots;

    public Center(String id, String name, String address, String pincode, String district, 
                  String vaccineType, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.pincode = pincode;
        this.district = district;
        this.vaccineType = vaccineType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.slots = new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPincode() { return pincode; }
    public String getDistrict() { return district; }
    public String getVaccineType() { return vaccineType; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public List<Slot> getSlots() { return slots; }

    public void addSlot(Slot slot) {
        slots.add(slot);
    }

    public Slot findSlotById(String slotId) {
        for (Slot s : slots) {
            if (s.getId().equalsIgnoreCase(slotId)) {
                return s;
            }
        }
        return null;
    }

    public int getTotalAvailableSlots() {
        int total = 0;
        for (Slot s : slots) {
            total += s.getAvailableCount();
        }
        return total;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"name\":\"").append(escapeJson(name)).append("\",");
        sb.append("\"address\":\"").append(escapeJson(address)).append("\",");
        sb.append("\"pincode\":\"").append(escapeJson(pincode)).append("\",");
        sb.append("\"district\":\"").append(escapeJson(district)).append("\",");
        sb.append("\"vaccineType\":\"").append(escapeJson(vaccineType)).append("\",");
        sb.append("\"latitude\":").append(latitude).append(",");
        sb.append("\"longitude\":").append(longitude).append(",");
        sb.append("\"totalAvailable\":").append(getTotalAvailableSlots()).append(",");
        sb.append("\"slots\":[");
        for (int i = 0; i < slots.size(); i++) {
            sb.append(slots.get(i).toJson());
            if (i < slots.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
