package model;

public class Slot {
    private String id;
    private String timeWindow;
    private int totalCapacity;
    private int bookedCount;

    public Slot(String id, String timeWindow, int totalCapacity, int bookedCount) {
        this.id = id;
        this.timeWindow = timeWindow;
        this.totalCapacity = totalCapacity;
        this.bookedCount = bookedCount;
    }

    public String getId() { return id; }
    public String getTimeWindow() { return timeWindow; }
    public int getTotalCapacity() { return totalCapacity; }
    public int getBookedCount() { return bookedCount; }

    public int getAvailableCount() {
        return Math.max(0, totalCapacity - bookedCount);
    }

    public boolean isAvailable() {
        return getAvailableCount() > 0;
    }

    public synchronized boolean bookOneSlot() {
        if (isAvailable()) {
            bookedCount++;
            return true;
        }
        return false;
    }

    public synchronized boolean cancelOneSlot() {
        if (bookedCount > 0) {
            bookedCount--;
            return true;
        }
        return false;
    }

    public synchronized void addCapacity(int delta) {
        if (delta > 0) {
            this.totalCapacity += delta;
        }
    }

    public String toJson() {
        return String.format(
            "{\"id\":\"%s\",\"timeWindow\":\"%s\",\"totalCapacity\":%d,\"bookedCount\":%d,\"availableCount\":%d}",
            id, timeWindow, totalCapacity, bookedCount, getAvailableCount()
        );
    }
}
