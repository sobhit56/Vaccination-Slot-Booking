package dsa;

import java.util.ArrayList;
import java.util.List;

/**
 * Circular Queue (Ring Buffer) implementation for Rush-Hour Traffic Management.
 * Acts as a Virtual Waiting Room to process slot bookings sequentially without overloading the server.
 * 
 * Time Complexities:
 *   - Enqueue: O(1)
 *   - Dequeue: O(1)
 *   - Space: O(Capacity)
 */
public class CircularQueue<T> {
    private final Object[] elements;
    private final int capacity;
    private int front;
    private int rear;
    private int count;

    public CircularQueue(int capacity) {
        this.capacity = capacity;
        this.elements = new Object[capacity];
        this.front = 0;
        this.rear = -1;
        this.count = 0;
    }

    public synchronized boolean enqueue(T item) {
        if (isFull()) {
            return false;
        }
        rear = (rear + 1) % capacity;
        elements[rear] = item;
        count++;
        return true;
    }

    @SuppressWarnings("unchecked")
    public synchronized T dequeue() {
        if (isEmpty()) {
            return null;
        }
        T item = (T) elements[front];
        elements[front] = null; // clear reference
        front = (front + 1) % capacity;
        count--;
        return item;
    }

    public synchronized boolean isFull() {
        return count == capacity;
    }

    public synchronized boolean isEmpty() {
        return count == 0;
    }

    public synchronized int size() {
        return count;
    }

    public int getCapacity() {
        return capacity;
    }

    @SuppressWarnings("unchecked")
    public synchronized List<T> toList() {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = (front + i) % capacity;
            list.add((T) elements[idx]);
        }
        return list;
    }
}
