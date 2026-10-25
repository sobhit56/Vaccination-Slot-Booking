package model;

public class Patient {
    private String id; // Aadhaar / National ID
    private String name;
    private int age;
    private String phone;
    private boolean hasComorbidity;
    private int priorityScore;

    public Patient(String id, String name, int age, String phone, boolean hasComorbidity) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.phone = phone;
        this.hasComorbidity = hasComorbidity;
        this.priorityScore = calculatePriority();
    }

    private int calculatePriority() {
        // High priority for senior citizens (>=60) and patients with medical risk conditions
        int score = age;
        if (age >= 60) {
            score = age * 2;
        }
        if (hasComorbidity) {
            score += 50;
        }
        return score;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getPhone() { return phone; }
    public boolean hasComorbidity() { return hasComorbidity; }
    public int getPriorityScore() { return priorityScore; }

    public String toJson() {
        return String.format(
            "{\"id\":\"%s\",\"name\":\"%s\",\"age\":%d,\"phone\":\"%s\",\"hasComorbidity\":%b,\"priorityScore\":%d}",
            id, escapeJson(name), age, phone, hasComorbidity, priorityScore
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
