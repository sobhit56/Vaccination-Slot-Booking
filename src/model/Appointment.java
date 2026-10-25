package model;

public class Appointment {
    private String tokenId;
    private String patientId;
    private String patientName;
    private int patientAge;
    private String centerId;
    private String centerName;
    private String pincode;
    private String vaccineType;
    private String slotTime;
    private String date;
    private int doseNumber;
    private String status; // "CONFIRMED", "VACCINATED", "CANCELLED"
    private String bookedAt;
    private String vaccinatedAt;
    private String patientPhone;
    private String patientEmail;

    public Appointment(String tokenId, String patientId, String patientName, int patientAge,
                       String centerId, String centerName, String pincode, String vaccineType,
                       String slotTime, String date, int doseNumber, String status, String bookedAt) {
        this.tokenId = tokenId;
        this.patientId = patientId;
        this.patientName = patientName;
        this.patientAge = patientAge;
        this.centerId = centerId;
        this.centerName = centerName;
        this.pincode = pincode;
        this.vaccineType = vaccineType;
        this.slotTime = slotTime;
        this.date = date;
        this.doseNumber = doseNumber;
        this.status = status;
        this.bookedAt = bookedAt;
        this.vaccinatedAt = "";
        this.patientPhone = "";
        this.patientEmail = "";
    }

    public String getTokenId() { return tokenId; }
    public String getPatientId() { return patientId; }
    public String getPatientName() { return patientName; }
    public int getPatientAge() { return patientAge; }
    public String getPatientPhone() { return patientPhone != null ? patientPhone : ""; }
    public void setPatientPhone(String phone) { this.patientPhone = phone; }
    public String getPatientEmail() { return patientEmail != null ? patientEmail : ""; }
    public void setPatientEmail(String email) { this.patientEmail = email; }
    public String getCenterId() { return centerId; }
    public String getCenterName() { return centerName; }
    public String getPincode() { return pincode; }
    public String getVaccineType() { return vaccineType; }
    public String getSlotTime() { return slotTime; }
    public String getDate() { return date; }
    public int getDoseNumber() { return doseNumber; }
    public String getStatus() { return status; }
    public String getBookedAt() { return bookedAt; }
    public String getVaccinatedAt() { return vaccinatedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setVaccinatedAt(String timestamp) { this.vaccinatedAt = timestamp; }
    public void setSlotTime(String slotTime) { this.slotTime = slotTime; }
    public void setDate(String date) { this.date = date; }

    public String getQrPayload() {
        return String.format("TOKEN:%s|PATIENT:%s|ID:%s|VAX:%s|STATUS:%s",
                tokenId, patientName, patientId, vaccineType, status);
    }

    public String toJson() {
        return String.format(
            "{\"tokenId\":\"%s\",\"patientId\":\"%s\",\"patientName\":\"%s\",\"patientAge\":%d,\"patientPhone\":\"%s\",\"patientEmail\":\"%s\"," +
            "\"centerId\":\"%s\",\"centerName\":\"%s\",\"pincode\":\"%s\",\"vaccineType\":\"%s\"," +
            "\"slotTime\":\"%s\",\"date\":\"%s\",\"doseNumber\":%d,\"status\":\"%s\"," +
            "\"bookedAt\":\"%s\",\"vaccinatedAt\":\"%s\",\"qrPayload\":\"%s\"}",
            tokenId, patientId, escapeJson(patientName), patientAge, getPatientPhone(), getPatientEmail(),
            centerId, escapeJson(centerName), pincode, vaccineType,
            slotTime, date, doseNumber, status,
            bookedAt, vaccinatedAt, escapeJson(getQrPayload())
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
