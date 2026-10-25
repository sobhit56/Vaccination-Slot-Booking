package dsa;

import model.Patient;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Max-Heap Priority Queue implementation for Waitlist Management.
 * Senior citizens and patients with comorbidities receive higher priority scores.
 * 
 * Time Complexities:
 *   - Insertion (Heapify Up): O(log N)
 *   - Extract Max (Heapify Down): O(log N)
 *   - Peek Highest Priority: O(1)
 */
public class PriorityWaitlist {

    public static class WaitlistEntry {
        private String patientId;
        private String patientName;
        private int age;
        private boolean hasComorbidity;
        private int priorityScore;
        private String centerId;
        private String centerName;
        private String slotTime;
        private long timestamp;

        public WaitlistEntry(Patient patient, String centerId, String centerName, String slotTime) {
            this.patientId = patient.getId();
            this.patientName = patient.getName();
            this.age = patient.getAge();
            this.hasComorbidity = patient.hasComorbidity();
            this.priorityScore = patient.getPriorityScore();
            this.centerId = centerId;
            this.centerName = centerName;
            this.slotTime = slotTime;
            this.timestamp = System.currentTimeMillis();
        }

        public String getPatientId() { return patientId; }
        public String getPatientName() { return patientName; }
        public int getAge() { return age; }
        public boolean isHasComorbidity() { return hasComorbidity; }
        public int getPriorityScore() { return priorityScore; }
        public String getCenterId() { return centerId; }
        public String getCenterName() { return centerName; }
        public String getSlotTime() { return slotTime; }

        public String toJson() {
            return String.format(
                "{\"patientId\":\"%s\",\"patientName\":\"%s\",\"age\":%d,\"hasComorbidity\":%b," +
                "\"priorityScore\":%d,\"centerId\":\"%s\",\"centerName\":\"%s\",\"slotTime\":\"%s\"}",
                patientId, patientName.replace("\"", "\\\""), age, hasComorbidity,
                priorityScore, centerId, centerName.replace("\"", "\\\""), slotTime
            );
        }
    }

    private final List<WaitlistEntry> heap;
    private int totalSwaps = 0;

    public PriorityWaitlist() {
        this.heap = new ArrayList<>();
    }

    public synchronized void insert(WaitlistEntry entry) {
        heap.add(entry);
        siftUp(heap.size() - 1);
    }

    public synchronized WaitlistEntry extractMax() {
        if (heap.isEmpty()) return null;
        WaitlistEntry max = heap.get(0);
        WaitlistEntry last = heap.remove(heap.size() - 1);
        if (!heap.isEmpty()) {
            heap.set(0, last);
            siftDown(0);
        }
        return max;
    }

    public synchronized WaitlistEntry peek() {
        return heap.isEmpty() ? null : heap.get(0);
    }

    public synchronized int size() {
        return heap.size();
    }

    public synchronized boolean isEmpty() {
        return heap.isEmpty();
    }

    public int getTotalSwaps() {
        return totalSwaps;
    }

    private void siftUp(int index) {
        while (index > 0) {
            int parentIndex = (index - 1) / 2;
            if (compare(heap.get(index), heap.get(parentIndex)) > 0) {
                swap(index, parentIndex);
                index = parentIndex;
            } else {
                break;
            }
        }
    }

    private void siftDown(int index) {
        int size = heap.size();
        while (index < size) {
            int left = 2 * index + 1;
            int right = 2 * index + 2;
            int largest = index;

            if (left < size && compare(heap.get(left), heap.get(largest)) > 0) {
                largest = left;
            }
            if (right < size && compare(heap.get(right), heap.get(largest)) > 0) {
                largest = right;
            }

            if (largest != index) {
                swap(index, largest);
                index = largest;
            } else {
                break;
            }
        }
    }

    private int compare(WaitlistEntry a, WaitlistEntry b) {
        // Primary: Highest priority score first
        if (a.getPriorityScore() != b.getPriorityScore()) {
            return Integer.compare(a.getPriorityScore(), b.getPriorityScore());
        }
        // Secondary: Earlier registration timestamp (FIFO for same score)
        return Long.compare(b.timestamp, a.timestamp);
    }

    private void swap(int i, int j) {
        WaitlistEntry temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);
        totalSwaps++;
    }

    public synchronized int getWaitlistPosition(String patientId) {
        if (patientId == null) return -1;
        for (int i = 0; i < heap.size(); i++) {
            if (patientId.equalsIgnoreCase(heap.get(i).getPatientId())) {
                return i + 1;
            }
        }
        return -1;
    }

    public synchronized WaitlistEntry getWaitlistEntry(String patientId) {
        if (patientId == null) return null;
        for (WaitlistEntry entry : heap) {
            if (patientId.equalsIgnoreCase(entry.getPatientId())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Returns a copy of all waitlisted patients in priority order for UI inspection.
     */
    public synchronized List<WaitlistEntry> getOrderedList() {
        PriorityWaitlist copy = new PriorityWaitlist();
        for (WaitlistEntry e : heap) {
            copy.insert(e);
        }
        List<WaitlistEntry> ordered = new ArrayList<>();
        while (!copy.isEmpty()) {
            ordered.add(copy.extractMax());
        }
        return ordered;
    }
}
