# Changelog

All notable changes to the **Vaccination Slot Booking System (VaxSlot Connect)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.4.0] - 2026-10-25
### Added
- **Live Microsoft Azure Cloud Deployment**:
  - Provisioned Azure App Service Linux (Basic B1 Plan) in UAE North under the active **Azure for Students** subscription.
  - Live public endpoint: `https://vaxslot-india-sobhit.azurewebsites.net`.
  - Configured dynamic port binding (`$PORT` / `WEBSITES_PORT`) and automated Java runtime orchestration.
- **Aarogya AI Clinical Healthcare Assistant (`AzureAiService.java`)**:
  - Interactive clinical AI assistant powered by Azure OpenAI architecture (`/api/ai/chat`).
  - Floating clinical drawer in the UI answering queries on vaccine dosage intervals, comorbidity safety (diabetes, hypertension), and post-vaccination side effects according to MoHFW guidelines.
- **Pan-India Vaccination Network**:
  - Expanded from 5 local clinics to 32 premier government & multi-speciality medical institutions across 16 States/UTs (Delhi, Mumbai, Bengaluru, Kolkata, Chennai, Hyderabad, Lucknow, Patna, Jaipur, Ahmedabad, Chandigarh, Pune, Kochi, Bhopal, Bhubaneswar, Guwahati, Srinagar).
  - Authentic postal pincodes matching the Indian Postal API and real GPS coordinates.
- **Clean Slate Dynamic Patient Registration**:
  - Cleared all pre-seeded dummy records. Both `patients.json` and `appointments.json` start fresh (`[]`).
  - Patient profiles and clinical priority scores are generated dynamically on demand.
- **Self-Contained Executable Packaging (`app.jar`)**:
  - Hybrid static asset handler serving from both local disk and JAR classpath resources for cloud portability.

---

## [1.3.0] - 2026-10-25
### Added
- **Fast2SMS Gateway Integration (`SmsService.java`)**:
  - Background asynchronous SMS dispatch for appointment bookings, waitlist notifications, slot promotions, and rescheduling.
  - Supports configurable API keys via `FAST2SMS_API_KEY` environment variable or code config.
- **Direct Patient WhatsApp & SMS Action Buttons**:
  - Direct 1-click **"Receive via WhatsApp"** button on appointment passes generating pre-formatted passes with Token ID, center, and clinical instructions.
  - Direct **"Open in Mobile SMS"** link with pre-populated message payload.
- **Hospital Emergency Walk-In Registration**:
  - New Admin Desk endpoint (`/api/admin/walkin`) allowing hospital staff to register emergency, walk-in, and elderly patients directly into active slots without queue contention.
- **Admin Quota & Slot Capacity Manager**:
  - Endpoint (`/api/admin/slot/capacity`) allowing staff to dynamically replenish or increase slot capacity in real-time.
- **Live Vaccine Inventory & Low-Stock Alerts**:
  - Admin dashboard monitors total doses and highlights hospital centers with critically low stock (< 8 doses remaining) with animated pulsing badges.
- **Age-Category Slot Filtering**:
  - Fast filter chips on the patient portal for age groups: Youth (12-17), Adults (18-44), Pre-Seniors (45+), and Senior Citizens (60+).

### Fixed
- Stabilized Windows `run.bat` launcher with working directory locking (`%~dp0`), explicit Oracle Java PATH fallback, and delayed browser launching to prevent `ERR_CONNECTION_REFUSED`.

---

## [1.2.0] - 2026-10-25
### Added
- **Patient Self-Service "Track My Booking" Portal**:
  - Dedicated search endpoint (`/api/track`) allowing patients to look up live booking status, reprint digital QR passes, and check priority waitlist rank using Token ID, 10-digit Mobile, or 12-digit Aadhaar.
- **1-Click Appointment Rescheduling**:
  - Rescheduling endpoint (`/api/reschedule`) enabling patients to shift their confirmed appointment to an alternative open slot at the same center without re-entering medical records.
- **Duplicate Booking & Identity Guard**:
  - Strict validation preventing patients from booking multiple simultaneous appointments for the same dose with the same Aadhaar/Govt ID.
  - Clinical dose progression guard preventing Dose 1 duplication when already fully immunized.
- **Government Photo ID Format Validation**:
  - Enforced 12-digit numeric Aadhaar and 10-digit mobile number validation patterns.

---

## [1.1.0] - 2026-10-25
### Added
- **Live Indian Postal Pincode Auto-Resolution API**:
  - Real-time client integration with `https://api.postalpincode.in/pincode/{PIN}`.
  - Automatically resolves and displays official District, Taluk, State, and Postal Circle on entering 6-digit pincodes.
- **CoWIN-Inspired Clean Patient Interface**:
  - Revamped patient portal into an intuitive 3-step workflow (Find Center -> Select Slot -> Confirm Details).
  - Replaced technical Data Structure and Algorithm labels in the public view with accessible healthcare terms.
- **Dedicated DSA Engineering Inspector**:
  - Moved technical metrics (Trie node traversals, Max-Heap waitlist rankings, Big-O metrics) into a collapsible technical drawer for college viva demonstrations.

---

## [1.0.0] - 2026-10-24
### Added
- **Core Java HTTP Server**:
  - Zero-external-dependency backend built with `com.sun.net.httpserver.HttpServer`.
  - Static file routing for web assets (`index.html`, `admin.html`, `style.css`, `app.js`).
- **Data Structures & Algorithms Implementation**:
  - **Trie (`src/dsa/Trie.java`)**: Prefix tree for $O(L)$ sub-millisecond autocomplete across hospital names, districts, and pincodes.
  - **Priority Waitlist (`src/dsa/PriorityWaitlist.java`)**: Custom Max-Heap using `siftUp()` and `siftDown()` prioritizing seniors (60+) and comorbidity patients.
  - **Automatic Waitlist Promotion**: Instant slot reassignment on appointment cancellation using `extractMax()`.
  - **Concurrency Locking**: Thread-safe slot reservations using Java `ReentrantLock` (Mutex).
  - **Haversine Distance**: Geographical spherical proximity calculations in `GeoDistance.java`.
  - **FastLookup Index**: Hash Table ($O(1)$) indexing for patient and appointment tokens.
- **Clinical Interval Validation**:
  - Rules for Covishield (84-day min interval), Covaxin (28-day min interval), and Pfizer (21-day min interval).
- **JSON File Persistence**:
  - Auto-saving local storage in `data/centers.json`, `data/patients.json`, and `data/appointments.json`.
- **Role-Based Admin Desk (`admin.html`)**:
  - Staff authentication gate, QR code verification, and PDF/printable vaccination certificate generation.
