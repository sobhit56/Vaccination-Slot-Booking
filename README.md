# 🏥 Vaccination Slot Booking System (VaxSlot Connect)

A production-grade, full-featured **Vaccination Slot Booking & Hospital Capacity Management System** built for college and engineering evaluations. It combines **in-memory Data Structures & Algorithms (DSA) in pure Java 21 (LTS)** with a **modern, responsive web interface (HTML5, CSS3, JavaScript)**, Microsoft Azure Cloud hosting, and local disk JSON persistence.

> 🌐 **Live Public URL:** [https://vaxslot-india-sobhit.azurewebsites.net](https://vaxslot-india-sobhit.azurewebsites.net)  
> 📋 **Project Version:** `v1.5.0` | 📝 **Detailed History:** See [CHANGELOG.md](CHANGELOG.md)

---

## 📅 Project Evolution & Version Roadmap

| Version | Release Date | Key Milestones & Capabilities Added |
| :--- | :--- | :--- |
| **`v1.5.0`** | 2026-11-23 | **System-Aware Dark / Light Theme Engine**, Automated Email Pass Dispatch, WCAG Color Contrast Fixes |
| **`v1.4.0`** | 2026-10-25 | **Microsoft Azure Live Cloud Hosting**, Aarogya AI Clinical Assistant, 32 Pan-India Hospitals, Dynamic Patient Storage |
| **`v1.3.0`** | 2026-10-25 | Fast2SMS Gateway Integration, Direct WhatsApp & SMS Pass Generation, Hospital Walk-in Desk, Live Low-Stock Inventory Monitor |
| **`v1.2.0`** | 2026-10-25 | "Track My Booking" Self-Service Portal, 1-Click Slot Rescheduling, Aadhaar & Dose Interval Validation Guards |
| **`v1.1.0`** | 2026-10-25 | CoWIN-Style 3-Step UI, Live Indian Postal Pincode Auto-Resolution API, Viva DSA Inspector Drawer |
| **`v1.0.0`** | 2026-10-24 | Core Java HTTP Server, Trie Search, Max-Heap Priority Waitlist, Mutex Concurrency, Admin Verification Desk |

---

## 🌟 Key Features & Architecture Highlights

1. **Instant Autocomplete Search (Trie - Prefix Tree)**:
   * Real-time search across Pincodes (e.g., `110029`, `201301`), Hospital names (e.g., `AIIMS`, `Apollo`, `Fortis`), and Districts.
   * Search time complexity: **$O(L)$** (where $L$ is query length).
   * Live execution metrics (nodes traversed and $\mu s$ elapsed) viewable in the Technical Inspector.

2. **Priority Allocation & Automatic Waitlist Promotion (Max-Heap)**:
   * When slots fill up, patients join an in-memory **Max-Heap** ordered by age and clinical factors.
   * **Automatic Promotion on Cancellation**: When an appointment is cancelled, the system executes **`extractMax()`** in $O(\log N)$ to immediately auto-promote the highest-priority waiting patient!
   * Heap operations: Insert $O(\log N)$, Extract Max $O(\log N)$, Peek $O(1)$.

3. **System-Aware Dark / Light Mode Toggle**:
   * Modern accessible theme switcher with automatic OS preference detection and persistent user overrides via `localStorage`.
   * High-contrast healthcare color palette meeting WCAG accessibility standards across cards, badges, and modals.

4. **Multi-Channel Digital Pass Dispatch (SMS, Email & WhatsApp)**:
   * Automated background SMS notification engine via **Fast2SMS**.
   * Direct **Email Confirmation Pass** dispatch with token instructions.
   * One-click **"Send to WhatsApp"** and **"Open in Mobile SMS"** buttons on the digital appointment slip for instant zero-cost delivery.

5. **Live Indian Postal Pincode Auto-Resolution API**:
   * Integrated with official Indian Postal data (`https://api.postalpincode.in`).
   * Automatically resolves District, Taluk, and Circle when users enter their residential 6-digit pincode.

6. **Patient Self-Service "Track My Booking" & 1-Click Rescheduling**:
   * Patients can enter their Token ID, 10-digit Mobile, or 12-digit Aadhaar to check status, view waitlist ranking, or reprint passes.
   * Allows 1-click slot shifting to alternative open slots at the same center.

7. **Hospital Walk-In Desk & Dynamic Quota Manager (Admin)**:
   * Hospital staff can bypass queues to register emergency walk-ins and elderly citizens directly.
   * Real-time slot quota replenishment without server restarts.

8. **Real-time Vaccine Inventory & Low-Stock Alerts**:
   * Admin dashboard tracks dose stock by hospital and vaccine brand (Covishield, Covaxin, Pfizer).
   * Pulsing visual warnings for hospitals with critically low stock (< 8 doses).

9. **Dose 2 Clinical Interval Safety Algorithm**:
   * Enforces mandatory vaccine intervals (Covishield: 84 days, Covaxin: 28 days, Pfizer: 21 days) via `IntervalScheduler.java`.
   * Prevents premature vaccination and duplicate bookings for the same dose.

10. **Interactive Map & Haversine Proximity**:
    * Computes spherical geographical distances using the **Haversine Formula** ($R = 6371\text{ km}$) in `GeoDistance.java` and sorts centers by proximity to user GPS.

11. **Anti-Double Booking & Concurrency Locking (Mutex)**:
    * Eliminates race conditions during rush hours using Java `ReentrantLock` in `BookingService.java`.

12. **RBAC Staff Verification Desk & Digital Certificates**:
    * Protected by Staff Authentication credentials (`admin` / `admin123`).
    * QR Code scanning, vaccination status marking, and printable official certificates.

---

## 📂 Project Structure

```text
Vaccination Slot Booking/
│
├── bin/                               <-- Compiled Java bytecode (.class files)
│
├── src/                               <-- Pure Java 21 source code
│   ├── Main.java                      <-- Java HTTP Server (com.sun.net.httpserver)
│   ├── model/
│   │   ├── Center.java                <-- Vaccine center & slot details
│   │   ├── Slot.java                  <-- Individual time slot & capacity counter
│   │   ├── Patient.java               <-- Patient record & dynamic priority formula
│   │   └── Appointment.java           <-- Appointment token, dose, status, QR payload
│   ├── dsa/
│   │   ├── Trie.java                  <-- Prefix Tree for instant Pincode & Name search
│   │   ├── PriorityWaitlist.java      <-- Custom Max-Heap for priority allocation
│   │   ├── CircularQueue.java         <-- Ring buffer queue for rush-hour virtual room
│   │   ├── FastLookup.java            <-- O(1) Hash Map index for patient & token lookup
│   │   ├── GeoDistance.java           <-- Haversine great-circle distance algorithm
│   │   └── IntervalScheduler.java     <-- Clinical MoHFW interval gap validator
│   ├── service/
│   │   ├── BookingService.java        <-- Concurrency lock, booking workflow & verification
│   │   └── DataStore.java             <-- In-memory state & disk auto-save (data/ folder)
│   └── util/
│       └── JsonUtils.java             <-- Zero-dependency lightweight JSON parser & builder
│
├── web/                               <-- Modern Frontend (HTML, CSS, JavaScript)
│   ├── index.html                     <-- Patient booking portal & live DSA inspector
│   ├── admin.html                     <-- Doctor/Admin verification desk & certificates
│   ├── style.css                      <-- Clean healthcare UI, theme engine & responsive styling
│   ├── app.js                         <-- Client logic, fetch API, & event handlers
│   └── qrcode.min.js                  <-- Standalone pure JS QR code generator
│
├── data/                              <-- Local storage on your computer (JSON files)
│   ├── centers.json
│   ├── patients.json
│   └── appointments.json
│
├── run.bat                            <-- 1-Click compile & launch script for Windows
├── Dockerfile                         <-- Container definition for Azure App Service
├── CHANGELOG.md                       <-- Full feature release log
└── README.md                          <-- Documentation & Viva preparation guide
```

---

## 🚀 How to Run the Project

### Option A: One-Click Script (Windows)
Double-click `run.bat` in the project root folder. It will:
1. Compile the Java files automatically into `bin/`.
2. Start the Java HTTP server at `http://localhost:8080`.
3. Automatically launch your default web browser to the Patient Portal.

### Option B: From the Terminal (PowerShell or Command Prompt)
1. **Compile**:
   ```powershell
   javac -d bin src\model\*.java src\dsa\*.java src\util\*.java src\service\*.java src\Main.java
   ```
2. **Run**:
   ```powershell
   java -cp bin Main
   ```
3. **Open in Browser**:
   * **Patient Portal**: `http://localhost:8080/index.html`
   * **Admin / Doctor Portal**: `http://localhost:8080/admin.html`

---

## 📊 Data Structures & Algorithms (Viva / Exam Guide)

| Feature | Data Structure / Algorithm | Time Complexity | Purpose |
| :--- | :--- | :--- | :--- |
| **Search Autocomplete** | **Trie (Prefix Tree)** | $O(L)$ where $L = \text{length of query}$ | Rapid lookup of Centers by Pincode or Name as user types. |
| **Nearest Center on Map** | **Haversine Spherical Distance** | $O(N \log N)$ | Calculates geographical distance & ranks centers closest to user GPS. |
| **Priority Waitlist** | **Max-Heap** | $O(\log N)$ Insert / Extract | Prioritizing elderly patients (60+) & high-risk comorbidities. |
| **Rush-Hour Queue** | **Circular Queue (Ring Buffer)** | $O(1)$ Enqueue / Dequeue | Fair FCFS processing during appointment rush hours. |
| **Patient / Token Lookup** | **Hash Table (HashMap)** | $O(1)$ Average | Instant verification of Aadhaar IDs and appointment tokens. |
| **Dose Interval Validation** | **Interval Scheduler Engine** | $O(1)$ | Enforces clinical MoHFW gap rules between doses (84d/28d/21d). |
| **Anti-Double Booking** | **Mutex (`ReentrantLock`)** | $O(1)$ | Eliminates race conditions for final slot availability. |

---

## 🎓 Teacher & Viva Questions Preparation

**Q1: Where did you store the data?**
> *"We used a two-tier storage model. In runtime, data lives in Java in-memory Data Structures (Trie, Heap, HashMap) for sub-millisecond lookups. Whenever a change occurs, Java automatically persists it to human-readable JSON files in the `data/` directory on the local disk."*

**Q2: How did you implement the Priority Queue?**
> *"Instead of relying purely on high-level libraries, we built a custom Max-Heap in `src/dsa/PriorityWaitlist.java` using `siftUp()` and `siftDown()` methods. It calculates a priority score based on age and comorbidity factors."*

**Q3: How does the system prevent two users from booking the same slot simultaneously?**
> *"In `BookingService.java`, slot allocation is wrapped in a `ReentrantLock` (Mutex). The capacity check, slot counter decrement, and token generation execute as an atomic transaction."*

**Q4: How does the Trie search work?**
> *"Each node in `Trie.java` contains child nodes for characters and a set of pointers to matching `Center` objects. When a user enters a prefix like `1100`, the algorithm traverses down 4 nodes and directly retrieves all matching centers in $O(L)$ time, regardless of how many centers exist."*

**Q5: How does the Map and Nearest Center feature work?**
> *"In `src/dsa/GeoDistance.java`, we implemented the Haversine Great-Circle Distance Algorithm to calculate real-world spherical distances over the Earth's curvature ($R = 6371\text{ km}$) from the user's GPS coordinates to each center. The UI displays an interactive map modal for each location and allows one-click route directions in Google Maps."*

**Q6: How does the Clinical Interval Scheduler protect patient safety?**
> *"In `src/dsa/IntervalScheduler.java`, we enforce national clinical intervals (Covishield: 84 days, Covaxin: 28 days, Pfizer: 21 days). If a patient attempts to book Dose 2 prematurely, the engine rejects the invalid booking and computes the earliest eligible date in $O(1)$ time."*

**Q7: How is the Admin/Doctor Portal secured against unauthorized patient access?**
> *"We implemented Role-Based Access Control (RBAC). Patients only have access to slot searching and booking. The Admin Portal (`admin.html`) is protected by a Staff Authentication Gate. In the Java backend, the `/api/appointments` and `/api/verify` endpoints require an authorized Bearer session token. Any unauthenticated attempt immediately returns HTTP 401 Unauthorized."*
