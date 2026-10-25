// State management
let allCenters = [];
let rawAppointments = [];
let searchDebounceTimer = null;
let currentWaitlist = [];
let pendingCancelTokenId = null;

// API Base URL (Relative path works because Java serves static files)
const API_BASE = '';

// On page load
window.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('centers-container')) {
    loadCenters();
    loadStats();
  }
});

// --- Toast Notification System ---
function showToast(title, message, type = 'info') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const icons = {
    success: '✅',
    warning: '⚠️',
    error: '❌',
    info: 'ℹ️'
  };

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <div class="toast-icon">${icons[type] || 'ℹ️'}</div>
    <div class="toast-body">
      <div class="toast-title">${escapeHtml(title)}</div>
      <div class="toast-msg">${escapeHtml(message)}</div>
    </div>
    <button class="toast-close-btn" onclick="dismissToast(this)">&times;</button>
  `;

  container.appendChild(toast);

  // Auto-dismiss after 4.5 seconds
  setTimeout(() => {
    if (toast.parentElement) {
      toast.classList.add('toast-fadeout');
      setTimeout(() => toast.remove(), 250);
    }
  }, 4500);
}

function dismissToast(btn) {
  const toast = btn.closest('.toast');
  if (toast) {
    toast.classList.add('toast-fadeout');
    setTimeout(() => toast.remove(), 250);
  }
}

// --- Fetch and Render Centers ---
async function loadCenters() {
  const container = document.getElementById('centers-container');
  if (container) {
    container.innerHTML = `
      <div style="grid-column: 1/-1; text-align: center; padding: 3rem; color: var(--text-muted);">
        <div style="font-size: 2rem; margin-bottom: 0.5rem;">⏳</div>
        <p>Loading available vaccination centers...</p>
      </div>
    `;
  }

  try {
    const res = await fetch(`${API_BASE}/api/centers`);
    const data = await res.json();
    allCenters = data;
    renderCenters(allCenters);
  } catch (err) {
    console.error('Failed to load centers:', err);
    if (container) {
      container.innerHTML = `
        <div style="grid-column: 1/-1; text-align: center; padding: 2.5rem; background: #fff1f2; border: 1px solid #fecdd3; border-radius: 12px; color: #9f1239;">
          <h3 style="margin-bottom: 0.4rem;">Cannot connect to vaccination server</h3>
          <p style="font-size: 0.9rem;">Please verify that the Java backend is active on port 8080 (run <code>run.bat</code>).</p>
        </div>
      `;
    }
  }
}

function renderCenters(centers) {
  const container = document.getElementById('centers-container');
  if (!container) return;

  // Update counter badges
  const countBadge = document.getElementById('centers-count');
  const countLabel = document.getElementById('search-count-label');
  if (countBadge) countBadge.innerText = `${centers.length} Centers Found`;

  let totalSlots = 0;
  centers.forEach(c => totalSlots += (c.totalAvailable || 0));

  if (countLabel) {
    countLabel.innerText = `Showing ${centers.length} centers (${totalSlots} slots open)`;
  }

  if (centers.length === 0) {
    container.innerHTML = `
      <div style="grid-column: 1/-1; text-align: center; background: white; border: 1px dashed var(--border); border-radius: 12px; padding: 3.5rem 1rem;">
        <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">🔍</div>
        <h3 style="font-size: 1.15rem; color: #0f172a; margin-bottom: 0.3rem;">No Centers Found</h3>
        <p style="color: var(--text-muted); font-size: 0.9rem; margin-bottom: 1rem;">No vaccination centers matched your search query.</p>
        <button class="btn-search-action btn-reset" onclick="resetSearch()" style="display: inline-flex;">
          View All Centers
        </button>
      </div>
    `;
    return;
  }

  container.innerHTML = centers.map(center => {
    const lat = center.latitude || 28.6139;
    const lng = center.longitude || 77.2090;
    const distanceBadge = center.distanceKm !== undefined 
      ? `<span class="badge badge-distance">📍 ${center.distanceKm} km away</span>` 
      : '';

    return `
      <div class="center-card">
        <div class="center-header">
          <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 0.5rem;">
            <div class="center-title">${escapeHtml(center.name)}</div>
            <button class="btn-map" onclick="openMapModal('${escapeJs(center.name)}', '${escapeJs(center.address)}, ${escapeJs(center.district)}', ${lat}, ${lng})">
              🗺️ Map
            </button>
          </div>
          <div class="center-address">📍 ${escapeHtml(center.address)}, ${escapeHtml(center.district)}</div>
          <div class="center-badges">
            <span class="badge badge-vaccine">💉 ${escapeHtml(center.vaccineType)}</span>
            <span class="badge badge-pincode">📮 ${escapeHtml(center.pincode)}</span>
            <span class="badge badge-available">✨ ${center.totalAvailable} slots open</span>
            ${distanceBadge}
          </div>
        </div>

        <div>
          <div class="slots-title">
            <span>Select Time Slot</span>
            <span style="font-weight: 500; font-size: 0.72rem; text-transform: none;">Click to book</span>
          </div>
          <div class="slots-container">
            ${center.slots.map(slot => {
              const isFull = slot.availableCount === 0;
              return `
                <div 
                  class="slot-btn ${isFull ? 'slot-full' : ''}" 
                  onclick="openBookingModal('${center.id}', '${escapeJs(center.name)}', '${escapeJs(center.address)}', '${slot.id}', '${slot.timeWindow}', ${slot.availableCount})"
                  title="${isFull ? 'Slot is fully booked. Click to join Priority Waitlist.' : 'Click to book this time slot'}"
                >
                  <div class="slot-time">${slot.timeWindow}</div>
                  <div class="slot-count">
                    <span class="slot-dot ${isFull ? 'amber' : 'green'}"></span>
                    ${isFull ? 'Waitlist (Full)' : `${slot.availableCount} slots left`}
                  </div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      </div>
    `;
  }).join('');
}

// --- Postal Pincode Auto-Detection API ---
const pincodeCache = new Map([
  ['110029', { name: 'Safdarjung Enclave', district: 'South West Delhi', state: 'Delhi', pincode: '110029' }],
  ['110017', { name: 'Saket', district: 'South Delhi', state: 'Delhi', pincode: '110017' }],
  ['560076', { name: 'Bannerghatta Road', district: 'Bengaluru Urban', state: 'Karnataka', pincode: '560076' }],
  ['560017', { name: 'HAL Old Airport Road', district: 'Bengaluru Urban', state: 'Karnataka', pincode: '560017' }],
  ['400050', { name: 'Bandra West', district: 'Mumbai Suburban', state: 'Maharashtra', pincode: '400050' }],
  ['110001', { name: 'Connaught Place', district: 'Central Delhi', state: 'Delhi', pincode: '110001' }],
  ['400001', { name: 'Fort Mumbai', district: 'Mumbai', state: 'Maharashtra', pincode: '400001' }]
]);

async function fetchPostalDetails(pincode) {
  if (pincodeCache.has(pincode)) {
    return pincodeCache.get(pincode);
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 4000);
    const res = await fetch(`https://api.postalpincode.in/pincode/${pincode}`, { signal: controller.signal });
    clearTimeout(timeoutId);

    const data = await res.json();
    if (Array.isArray(data) && data[0]?.Status === 'Success' && data[0]?.PostOffice?.length > 0) {
      const office = data[0].PostOffice[0];
      const result = {
        name: office.Name,
        district: office.District,
        state: office.State,
        pincode: pincode
      };
      pincodeCache.set(pincode, result);
      return result;
    }
  } catch (err) {
    console.warn('Live postal API lookup deferred:', err);
  }
  return null;
}

// --- Instant Search & Clear Button ---
function handleSearchInput() {
  const query = document.getElementById('search-input').value;
  const clearBtn = document.getElementById('search-clear-btn');
  if (clearBtn) {
    clearBtn.style.display = query.length > 0 ? 'block' : 'none';
  }

  // Deactivate quick filter chips when manually typing
  document.querySelectorAll('.filter-chip').forEach(el => el.classList.remove('active'));

  // Live Pincode API Detection
  const pinMatch = query.match(/\b\d{6}\b/);
  const badgeEl = document.getElementById('pincode-detected-badge');
  if (pinMatch) {
    fetchPostalDetails(pinMatch[0]).then(details => {
      if (details && badgeEl) {
        document.getElementById('pincode-detected-area').innerText = `${details.name}, ${details.district}`;
        document.getElementById('pincode-detected-region').innerText = `${details.state}, India • Verified Postal Circle (${details.pincode})`;
        badgeEl.style.display = 'flex';
      }
    });
  } else if (badgeEl) {
    badgeEl.style.display = 'none';
  }

  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(async () => {
    const trimmed = query.trim();
    if (!trimmed) {
      resetSearch();
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/api/search?q=${encodeURIComponent(trimmed)}`);
      const data = await res.json();
      renderCenters(data.results || []);
    } catch (err) {
      console.error('Search failed:', err);
    }
  }, 150);
}

function clearSearchInput() {
  const input = document.getElementById('search-input');
  if (input) input.value = '';
  const clearBtn = document.getElementById('search-clear-btn');
  if (clearBtn) clearBtn.style.display = 'none';
  const badgeEl = document.getElementById('pincode-detected-badge');
  if (badgeEl) badgeEl.style.display = 'none';
  resetSearch();
}

function resetSearch() {
  const input = document.getElementById('search-input');
  if (input) input.value = '';
  const clearBtn = document.getElementById('search-clear-btn');
  if (clearBtn) clearBtn.style.display = 'none';
  const badgeEl = document.getElementById('pincode-detected-badge');
  if (badgeEl) badgeEl.style.display = 'none';

  document.querySelectorAll('.filter-chip').forEach(el => el.classList.remove('active'));
  const chipAll = document.getElementById('chip-all');
  if (chipAll) chipAll.classList.add('active');

  renderCenters(allCenters);
}

// --- Quick Filters ---
function applyQuickFilter(filter) {
  document.querySelectorAll('.filter-chip').forEach(el => el.classList.remove('active'));

  if (filter === 'all') {
    const chip = document.getElementById('chip-all');
    if (chip) chip.classList.add('active');
    resetSearch();
    return;
  }

  const input = document.getElementById('search-input');
  if (input) {
    input.value = filter;
    const clearBtn = document.getElementById('search-clear-btn');
    if (clearBtn) clearBtn.style.display = 'block';
  }

  // Find corresponding chip
  const chipMap = {
    '110029': 'chip-delhi',
    '560076': 'chip-blr',
    '400050': 'chip-mum',
    'Covishield': 'chip-covi',
    'Covaxin': 'chip-covax',
    'Pfizer': 'chip-pfizer'
  };
  const activeId = chipMap[filter];
  if (activeId) {
    const el = document.getElementById(activeId);
    if (el) el.classList.add('active');
  }

  // Search API
  fetch(`${API_BASE}/api/search?q=${encodeURIComponent(filter)}`)
    .then(res => res.json())
    .then(data => renderCenters(data.results || []))
    .catch(err => console.error(err));
}

// --- Map Modal Functions ---
function openMapModal(name, address, lat, lng) {
  document.getElementById('map-center-name').innerText = name;
  document.getElementById('map-center-address').innerText = `📍 ${address}`;
  document.getElementById('map-coords').innerText = `GPS: ${lat.toFixed(4)}°, ${lng.toFixed(4)}°`;

  const mapUrl = `https://maps.google.com/maps?q=${lat},${lng}&hl=en&z=15&output=embed`;
  document.getElementById('map-iframe').src = mapUrl;

  const directionsBtn = document.getElementById('map-directions-btn');
  directionsBtn.href = `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;

  document.getElementById('map-modal').style.display = 'flex';
}

function closeMapModal() {
  document.getElementById('map-modal').style.display = 'none';
  document.getElementById('map-iframe').src = '';
}

// --- Nearest Centers Lookup ---
async function findNearestCenters() {
  showToast('Locating Centers', 'Detecting your approximate GPS position...', 'info');

  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        await fetchNearestFromApi(lat, lng);
      },
      async (err) => {
        console.warn('Geolocation denied or unavailable. Using central coordinates.');
        await fetchNearestFromApi(28.6139, 77.2090);
      },
      { timeout: 5000 }
    );
  } else {
    await fetchNearestFromApi(28.6139, 77.2090);
  }
}

async function fetchNearestFromApi(lat, lng) {
  try {
    const res = await fetch(`${API_BASE}/api/nearest?lat=${lat}&lng=${lng}`);
    const data = await res.json();

    const mappedCenters = data.map(item => {
      const c = item.center;
      c.distanceKm = item.distanceKm;
      return c;
    });

    renderCenters(mappedCenters);
    showToast('Sorted by Distance', 'Centers are now ordered from closest to farthest.', 'success');
  } catch (err) {
    console.error('Failed to fetch nearest centers:', err);
    showToast('Location Search Failed', 'Could not compute distance. Showing default list.', 'warning');
  }
}

// --- System Stats ---
async function loadStats() {
  try {
    const res = await fetch(`${API_BASE}/api/stats`);
    const data = await res.json();
    document.getElementById('stat-centers').innerText = data.totalCenters;
    document.getElementById('stat-slots').innerText = data.availableSlots;
    document.getElementById('stat-waitlist').innerText = data.waitlistSize;
    document.getElementById('stat-vaccinated').innerText = data.totalVaccinated;
  } catch (err) {
    console.error('Failed to load stats:', err);
  }
}

// --- Dose Selection & Clinical Interval Verification ---
let selectedCenterVaccine = 'Covishield';

function selectDose(num) {
  document.getElementById('form-dose-number').value = num;
  const btn1 = document.getElementById('dose-btn-1');
  const btn2 = document.getElementById('dose-btn-2');
  const dateGroup = document.getElementById('dose1-date-group');

  if (num === 1) {
    btn1.classList.add('active');
    btn2.classList.remove('active');
    dateGroup.style.display = 'none';
    document.getElementById('dose-interval-feedback').style.display = 'none';
  } else {
    btn2.classList.add('active');
    btn1.classList.remove('active');
    dateGroup.style.display = 'block';
    checkDoseInterval();
  }
}

function checkDoseInterval() {
  const doseNum = parseInt(document.getElementById('form-dose-number').value);
  if (doseNum !== 2) return;

  const dateVal = document.getElementById('form-dose1-date').value;
  const feedback = document.getElementById('dose-interval-feedback');
  if (!feedback) return;

  if (!dateVal) {
    feedback.style.display = 'block';
    feedback.className = 'interval-feedback interval-ineligible';
    feedback.innerText = 'Please select the date when you took Dose 1.';
    return;
  }

  const dose1Date = new Date(dateVal);
  const today = new Date();
  const diffTime = today.getTime() - dose1Date.getTime();
  const elapsedDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

  let requiredDays = 28;
  const vax = selectedCenterVaccine.toLowerCase();
  if (vax.includes('covishield')) requiredDays = 84;
  else if (vax.includes('covaxin')) requiredDays = 28;
  else if (vax.includes('pfizer')) requiredDays = 21;

  feedback.style.display = 'block';
  if (elapsedDays >= requiredDays) {
    feedback.className = 'interval-feedback interval-eligible';
    feedback.innerHTML = `✅ <strong>Eligible for Dose 2!</strong> (${elapsedDays} days elapsed &ge; ${requiredDays} days required for ${selectedCenterVaccine})`;
  } else {
    const neededDate = new Date(dose1Date.getTime() + (requiredDays * 24 * 60 * 60 * 1000));
    const eligibleStr = neededDate.toISOString().split('T')[0];
    feedback.className = 'interval-feedback interval-ineligible';
    feedback.innerHTML = `❌ <strong>Ineligible for Dose 2 today:</strong> ${selectedCenterVaccine} requires a minimum ${requiredDays}-day interval. Only ${elapsedDays} days elapsed.<br>Earliest recommended date: <strong>${eligibleStr}</strong>.`;
  }
}

// --- Booking Modal & Priority Preview ---
function openBookingModal(centerId, centerName, centerAddress, slotId, timeWindow, availableCount) {
  const centerObj = allCenters.find(c => c.id === centerId);
  if (centerObj) selectedCenterVaccine = centerObj.vaccineType;

  document.getElementById('form-center-id').value = centerId;
  document.getElementById('form-slot-id').value = slotId;
  document.getElementById('modal-center-name').innerText = `${centerName} (${selectedCenterVaccine})`;
  document.getElementById('modal-center-sub').innerText = centerAddress || '';
  document.getElementById('modal-slot-time').innerText = `${timeWindow} • ${availableCount > 0 ? `${availableCount} Open` : 'Full'}`;

  selectDose(1); // Default to Dose 1

  const submitBtn = document.getElementById('btn-book-submit');
  if (availableCount === 0) {
    submitBtn.innerText = 'Join Priority Waitlist';
    submitBtn.style.background = '#d97706';
  } else {
    submitBtn.innerText = 'Confirm & Book Slot';
    submitBtn.style.background = 'var(--primary)';
  }

  updatePriorityPreview();
  document.getElementById('booking-modal').style.display = 'flex';
}

function closeBookingModal() {
  document.getElementById('booking-modal').style.display = 'none';
  document.getElementById('booking-form').reset();
  const feedback = document.getElementById('booking-pincode-feedback');
  if (feedback) feedback.style.display = 'none';
}

let bookingPincodeTimer = null;
function handleBookingPincodeInput() {
  clearTimeout(bookingPincodeTimer);
  const val = document.getElementById('form-pincode')?.value.trim();
  const feedback = document.getElementById('booking-pincode-feedback');
  if (!feedback) return;

  if (val && val.length === 6 && /^\d{6}$/.test(val)) {
    feedback.style.display = 'block';
    feedback.innerText = '🔍 Resolving postal jurisdiction...';
    bookingPincodeTimer = setTimeout(async () => {
      const details = await fetchPostalDetails(val);
      if (details) {
        feedback.innerHTML = `📍 <strong>${details.name}</strong>, ${details.district}, ${details.state} (Postal API Verified)`;
        feedback.style.color = '#0369a1';
      } else {
        feedback.innerHTML = `⚠️ Pincode ${val} recorded (Postal registry offline)`;
        feedback.style.color = '#64748b';
      }
    }, 200);
  } else {
    feedback.style.display = 'none';
  }
}

function updatePriorityPreview() {
  const age = parseInt(document.getElementById('form-age').value) || 30;
  const comorbidity = document.getElementById('form-comorbidity').checked;

  let score = age >= 60 ? (age * 2) : age;
  if (comorbidity) score += 50;

  const scoreEl = document.getElementById('preview-priority-score');
  if (scoreEl) {
    if (score >= 120) {
      scoreEl.innerHTML = '<span style="color:#0369a1;">Priority Queue ⚡ (Senior Citizen / Medical Care)</span>';
    } else if (score >= 60) {
      scoreEl.innerHTML = '<span style="color:#0284c7;">Priority Queue (Senior Citizen)</span>';
    } else {
      scoreEl.innerHTML = '<span style="color:#059669;">Standard Queue</span>';
    }
  }
}

async function submitBooking(e) {
  e.preventDefault();

  const name = document.getElementById('form-name').value.trim();
  const id = document.getElementById('form-id').value.trim();
  const age = parseInt(document.getElementById('form-age').value);
  const phone = document.getElementById('form-phone').value.trim();
  const hasComorbidity = document.getElementById('form-comorbidity').checked;
  const centerId = document.getElementById('form-center-id').value;
  const slotId = document.getElementById('form-slot-id').value;
  const doseNumber = parseInt(document.getElementById('form-dose-number').value) || 1;
  const dose1Date = document.getElementById('form-dose1-date').value;

  const cleanId = id.replace(/\s+/g, '').replace(/-/g, '');
  if (cleanId.length < 4) {
    showToast('Invalid ID', 'Please enter a valid Government Photo ID / Aadhaar number.', 'warning');
    return;
  }

  const cleanPhone = phone.replace(/\s+/g, '').replace(/-/g, '');
  if (cleanPhone.length === 10 && !/^[6-9]\d{9}$/.test(cleanPhone)) {
    showToast('Invalid Mobile', 'Please enter a valid 10-digit mobile number starting with 6, 7, 8, or 9.', 'warning');
    return;
  }

  const payload = {
    name, id: cleanId, age, phone: cleanPhone, hasComorbidity, centerId, slotId,
    doseNumber, dose1Date,
    date: new Date().toISOString().split('T')[0]
  };

  try {
    const res = await fetch(`${API_BASE}/api/book`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const result = await res.json();

    if (!result.success) {
      showToast('Booking Alert', result.message || result.error, 'warning');
      return;
    }

    closeBookingModal();
    loadCenters();
    loadStats();

    if (result.waitlisted) {
      showToast(
        'Placed on Priority Waitlist',
        `Patient ${name} is at position #${result.waitlistPosition}. You will be auto-assigned when a slot opens.`,
        'warning'
      );
      triggerSimulatedSms('sms', 'Priority Waitlist Alert', `Dear ${name}, you are placed on the Priority Waitlist (#${result.waitlistPosition}) for ${result.waitlist ? result.waitlist.centerName : 'the selected center'}. You will receive an alert as soon as a slot opens.`);
    } else {
      showToast('Appointment Confirmed', `Slot reserved for ${name}! Token: ${result.appointment.tokenId}`, 'success');
      showReceiptModal(result.appointment);
      triggerSimulatedSms('sms', 'Vaccination Pass Confirmed', `Dear ${name}, your Dose ${doseNumber} appointment at ${result.appointment.centerName} is CONFIRMED for ${result.appointment.date} (${result.appointment.slotTime}). Token: ${result.appointment.tokenId}. Please bring your original Aadhaar/ID.`);
    }
  } catch (err) {
    console.error('Booking submission failed:', err);
    showToast('Connection Error', 'Could not connect to the vaccination server.', 'error');
  }
}

// --- Appointment Confirmation & QR Code ---
function showReceiptModal(appt) {
  document.getElementById('receipt-token').innerText = appt.tokenId;
  document.getElementById('receipt-patient').innerText = appt.patientName;
  document.getElementById('receipt-id').innerText = appt.patientId;
  document.getElementById('receipt-center').innerText = `${appt.centerName} (${appt.pincode})`;
  document.getElementById('receipt-vaccine').innerText = `${appt.vaccineType} (Dose ${appt.doseNumber || 1})`;
  document.getElementById('receipt-time').innerText = `${appt.date} | ${appt.slotTime}`;

  const patientPhone = appt.patientPhone || document.getElementById('form-phone')?.value.trim() || '';
  const cleanPhone = patientPhone.replace(/\D/g, '');

  const phoneRow = document.getElementById('receipt-mobile-row');
  if (phoneRow) phoneRow.innerText = cleanPhone ? `+91 ${cleanPhone}` : 'Registered Mobile';

  const phoneDisp = document.getElementById('receipt-phone-display');
  if (phoneDisp) phoneDisp.innerText = cleanPhone || 'Mobile';

  // Format Official SMS & WhatsApp text message directly for the patient's phone
  const passMsg = `🏥 *NATIONAL HEALTH PORTAL - VACCINATION APPOINTMENT PASS*\n` +
    `Dear ${appt.patientName},\n` +
    `Your appointment has been successfully scheduled.\n\n` +
    `🎟️ *Token ID:* ${appt.tokenId}\n` +
    `🏥 *Hospital:* ${appt.centerName} (${appt.pincode})\n` +
    `💉 *Vaccine:* ${appt.vaccineType} (Dose ${appt.doseNumber || 1})\n` +
    `📅 *Date & Slot:* ${appt.date} | ${appt.slotTime}\n` +
    `🆔 *Govt ID:* ${appt.patientId}\n` +
    `✅ *Status:* CONFIRMED\n\n` +
    `*Instructions:* Please present this WhatsApp/SMS message or your digital QR pass at the hospital check-in counter. Carry your original photo ID.`;

  const waBtn = document.getElementById('receipt-whatsapp-link');
  if (waBtn) {
    waBtn.href = `https://api.whatsapp.com/send?phone=91${cleanPhone}&text=${encodeURIComponent(passMsg)}`;
  }

  const smsBtn = document.getElementById('receipt-sms-link');
  if (smsBtn) {
    smsBtn.href = `sms:${cleanPhone}?body=${encodeURIComponent(passMsg)}`;
  }

  // Native Device/Browser Push Notification
  if ('Notification' in window) {
    if (Notification.permission === 'granted') {
      try {
        new Notification(`VaxSlot Pass: ${appt.tokenId}`, {
          body: `Confirmed for ${appt.patientName} at ${appt.centerName} (${appt.slotTime}).`,
        });
      } catch (e) {}
    } else if (Notification.permission !== 'denied') {
      Notification.requestPermission();
    }
  }

  // Render QR Code
  const qrBox = document.getElementById('qrcode-box');
  qrBox.innerHTML = '';
  new QRCode(qrBox, {
    text: appt.qrPayload || appt.tokenId,
    width: 150,
    height: 150
  });

  document.getElementById('receipt-modal').style.display = 'flex';
}

function closeReceiptModal() {
  document.getElementById('receipt-modal').style.display = 'none';
}

// --- Hospital Staff & Doctor Authentication (RBAC) ---
function checkAdminAuth() {
  const token = sessionStorage.getItem('adminToken');
  const loginScreen = document.getElementById('admin-login-screen');
  const dashboard = document.getElementById('admin-dashboard');
  const userNav = document.getElementById('admin-user-nav');

  if (!loginScreen || !dashboard) return;

  if (token) {
    loginScreen.style.display = 'none';
    dashboard.style.display = 'block';
    if (userNav) userNav.style.display = 'flex';
    refreshAdminData();
  } else {
    loginScreen.style.display = 'block';
    dashboard.style.display = 'none';
    if (userNav) userNav.style.display = 'none';
  }
}

function refreshAdminData() {
  loadAppointmentsTable();
  loadAnalytics();
  loadAdminInventory();
}

async function handleAdminLogin(e) {
  e.preventDefault();
  const username = document.getElementById('admin-username').value.trim();
  const password = document.getElementById('admin-password').value.trim();
  const errorEl = document.getElementById('admin-login-error');

  errorEl.style.display = 'none';

  try {
    const res = await fetch(`${API_BASE}/api/admin/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    const data = await res.json();

    if (res.ok && data.success) {
      sessionStorage.setItem('adminToken', data.token);
      showToast('Login Successful', `Welcome, ${data.user || 'Staff Member'}!`, 'success');
      checkAdminAuth();
    } else {
      errorEl.style.display = 'block';
      errorEl.innerText = data.error || 'Invalid credentials. Access denied.';
    }
  } catch (err) {
    console.error('Login error:', err);
    errorEl.style.display = 'block';
    errorEl.innerText = 'Server connection error during login.';
  }
}

function handleAdminLogout() {
  sessionStorage.removeItem('adminToken');
  showToast('Logged Out', 'You have securely logged out.', 'info');
  checkAdminAuth();
}

// --- Visual Analytics Dashboard ---
async function loadAnalytics() {
  const token = sessionStorage.getItem('adminToken');
  if (!token) return;

  try {
    const res = await fetch(`${API_BASE}/api/analytics`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (!res.ok) return;

    const data = await res.json();
    const total = data.totalAppointments || 1;

    // 1. Vaccine Distribution
    const vax = data.vaccineDistribution;
    const coviPct = Math.round((vax.covishield / total) * 100);
    const covaxPct = Math.round((vax.covaxin / total) * 100);
    const pfizerPct = Math.round((vax.pfizer / total) * 100);

    const coviEl = document.getElementById('stat-covishield-count');
    if (coviEl) {
      coviEl.innerText = `${vax.covishield} (${coviPct}%)`;
      document.getElementById('bar-covishield').style.width = `${coviPct}%`;
      document.getElementById('stat-covaxin-count').innerText = `${vax.covaxin} (${covaxPct}%)`;
      document.getElementById('bar-covaxin').style.width = `${covaxPct}%`;
      document.getElementById('stat-pfizer-count').innerText = `${vax.pfizer} (${pfizerPct}%)`;
      document.getElementById('bar-pfizer').style.width = `${pfizerPct}%`;
    }

    // 2. Age Demographics
    const age = data.ageDemographics;
    const seniorsPct = Math.round((age.seniors / total) * 100);
    const adultsPct = Math.round((age.adults / total) * 100);
    const youthPct = Math.round((age.youth / total) * 100);

    const senEl = document.getElementById('stat-seniors-count');
    if (senEl) {
      senEl.innerText = `${age.seniors} (${seniorsPct}%)`;
      document.getElementById('bar-seniors').style.width = `${seniorsPct}%`;
      document.getElementById('stat-adults-count').innerText = `${age.adults} (${adultsPct}%)`;
      document.getElementById('bar-adults').style.width = `${adultsPct}%`;
      document.getElementById('stat-youth-count').innerText = `${age.youth} (${youthPct}%)`;
      document.getElementById('bar-youth').style.width = `${youthPct}%`;
    }

    // 3. Operational Overview
    const metrics = data.statusMetrics;
    const vaxStat = document.getElementById('stat-status-vax');
    if (vaxStat) {
      vaxStat.innerText = `${metrics.vaccinated} Patients`;
      document.getElementById('stat-status-conf').innerText = `${metrics.confirmed} Slots`;
      document.getElementById('stat-status-wait').innerText = `${metrics.waitlisted} Waitlisted`;
      document.getElementById('stat-status-canc').innerText = `${metrics.cancelled} Cancelled`;
    }

  } catch (err) {
    console.error('Analytics load error:', err);
  }
}

// --- 1-Click Export to CSV ---
async function exportAuditCsv() {
  const token = sessionStorage.getItem('adminToken');
  if (!token) {
    showToast('Unauthorized', 'Please log in as Admin first.', 'error');
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/export/csv`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (!res.ok) {
      showToast('Export Failed', 'Failed to generate audit report.', 'error');
      return;
    }

    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `vaccination_audit_report_${new Date().toISOString().split('T')[0]}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);

    showToast('Export Downloaded', 'Audit CSV report generated and downloaded.', 'success');
  } catch (err) {
    console.error('CSV export failed:', err);
    showToast('Export Error', 'Failed to export CSV report.', 'error');
  }
}

// --- Cancel Appointment Confirmation Modal ---
function cancelAppointment(tokenId) {
  pendingCancelTokenId = tokenId;
  const msgEl = document.getElementById('cancel-modal-msg');
  if (msgEl) {
    msgEl.innerHTML = `Are you sure you want to cancel appointment <strong>${escapeHtml(tokenId)}</strong>?<br><br>If there are eligible waitlisted patients, this vacant slot will be immediately and automatically reallocated.`;
  }
  const confirmBtn = document.getElementById('cancel-confirm-btn');
  if (confirmBtn) {
    confirmBtn.onclick = executeCancelAppointment;
  }
  document.getElementById('cancel-modal').style.display = 'flex';
}

function closeCancelModal() {
  document.getElementById('cancel-modal').style.display = 'none';
  pendingCancelTokenId = null;
}

async function executeCancelAppointment() {
  if (!pendingCancelTokenId) return;
  const tokenId = pendingCancelTokenId;
  closeCancelModal();

  try {
    const res = await fetch(`${API_BASE}/api/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tokenId })
    });

    const result = await res.json();
    if (result.success) {
      if (result.patientPromoted) {
        showToast('Slot Reallocated', `Slot reallocated to waitlisted patient! ${result.message}`, 'success');
        triggerSimulatedSms('whatsapp', 'Slot Auto-Reallocated!', `Great news! A slot opened up. Waitlisted patient was auto-scheduled into this slot. Token: ${result.promotedAppointment.tokenId}.`);
      } else {
        showToast('Cancelled', result.message, 'info');
        triggerSimulatedSms('sms', 'Appointment Cancelled', `Appointment ${tokenId} was successfully cancelled.`);
      }
      refreshAdminData();
    } else {
      showToast('Cancellation Failed', result.message || result.error, 'warning');
    }
  } catch (err) {
    console.error('Cancel request error:', err);
    showToast('Server Error', 'Error connecting to server for cancellation.', 'error');
  }
}

// --- Admin Appointments Table & Live Filter ---
async function loadAppointmentsTable() {
  const tbody = document.getElementById('appointments-tbody');
  if (!tbody) return;

  const token = sessionStorage.getItem('adminToken');
  if (!token) {
    checkAdminAuth();
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/appointments`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (res.status === 401) {
      handleAdminLogout();
      return;
    }

    rawAppointments = await res.json();
    renderAppointmentsTable(rawAppointments);
  } catch (err) {
    console.error('Failed to load appointments:', err);
    tbody.innerHTML = `<tr><td colspan="8" style="color: red; text-align: center;">Error loading data from server.</td></tr>`;
  }
}

function filterAppointmentsTable() {
  const query = (document.getElementById('admin-table-search')?.value || '').toLowerCase().trim();
  if (!query) {
    renderAppointmentsTable(rawAppointments);
    return;
  }

  const filtered = rawAppointments.filter(a => {
    return (a.tokenId && a.tokenId.toLowerCase().includes(query)) ||
           (a.patientName && a.patientName.toLowerCase().includes(query)) ||
           (a.patientId && a.patientId.toLowerCase().includes(query)) ||
           (a.centerName && a.centerName.toLowerCase().includes(query)) ||
           (a.vaccineType && a.vaccineType.toLowerCase().includes(query)) ||
           (a.status && a.status.toLowerCase().includes(query));
  });

  renderAppointmentsTable(filtered);
}

function renderAppointmentsTable(appts) {
  const tbody = document.getElementById('appointments-tbody');
  if (!tbody) return;

  if (appts.length === 0) {
    tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 2rem;">No appointments found.</td></tr>`;
    return;
  }

  tbody.innerHTML = appts.map(a => {
    const isVaccinated = a.status === 'VACCINATED';
    const isCancelled = a.status === 'CANCELLED';
    const statusBadge = isVaccinated 
      ? '<span class="badge badge-available">VACCINATED</span>'
      : isCancelled 
      ? '<span class="badge" style="background:#fee2e2; color:#b91c1c;">CANCELLED</span>'
      : '<span class="badge badge-vaccine">CONFIRMED</span>';

    let actionButtons = '';
    if (isVaccinated) {
      actionButtons = `<button class="nav-btn" style="padding: 0.3rem 0.6rem; font-size: 0.78rem; background: #e0f2fe; color: #0369a1; cursor: pointer;" onclick='openCertModal(${JSON.stringify(a)})'>📜 Certificate</button>`;
    } else if (isCancelled) {
      actionButtons = `<span style="color: #94a3b8; font-size: 0.75rem;">Cancelled</span>`;
    } else {
      actionButtons = `
        <button class="nav-btn primary" style="padding: 0.3rem 0.6rem; font-size: 0.78rem; cursor: pointer;" onclick="verifyPatientByToken('${a.tokenId}')">💉 Vaccinate</button>
        <button class="nav-btn" style="padding: 0.3rem 0.6rem; font-size: 0.78rem; background: #fee2e2; color: #dc2626; border: 1px solid #fecaca; cursor: pointer; margin-left: 0.3rem;" onclick="cancelAppointment('${a.tokenId}')">❌ Cancel</button>
      `;
    }

    return `
      <tr>
        <td><strong>${a.tokenId}</strong></td>
        <td>${escapeHtml(a.patientName)}</td>
        <td>${a.patientAge}</td>
        <td>${escapeHtml(a.centerName)} (${a.pincode})</td>
        <td><span class="badge badge-vaccine">${a.vaccineType} (Dose ${a.doseNumber || 1})</span></td>
        <td>${a.date}<br><small style="color:#64748b;">${a.slotTime}</small></td>
        <td>${statusBadge}</td>
        <td>${actionButtons}</td>
      </tr>
    `;
  }).join('');
}

async function verifyPatient() {
  const query = document.getElementById('verify-input').value.trim();
  if (!query) {
    showToast('Input Required', 'Please enter a Token ID or Aadhaar number.', 'warning');
    return;
  }
  await verifyPatientByToken(query);
}

async function verifyPatientByToken(token) {
  const adminToken = sessionStorage.getItem('adminToken');
  if (!adminToken) {
    showToast('Session Expired', 'Please log in again.', 'error');
    handleAdminLogout();
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/verify`, {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`
      },
      body: JSON.stringify({ query: token })
    });

    if (res.status === 401) {
      showToast('Unauthorized', 'Staff login required.', 'error');
      handleAdminLogout();
      return;
    }

    const result = await res.json();

    const feedback = document.getElementById('verify-feedback');
    if (feedback) {
      feedback.style.display = 'block';
      if (result.success) {
        feedback.style.color = '#059669';
        feedback.innerHTML = `✅ ${result.message}`;
        document.getElementById('verify-input').value = '';
      } else {
        feedback.style.color = '#dc2626';
        feedback.innerHTML = `❌ ${result.error}`;
      }
    }

    if (result.success) {
      showToast('Patient Vaccinated', result.message, 'success');
    } else {
      showToast('Verification Failed', result.error, 'warning');
    }

    loadAppointmentsTable();
    loadAnalytics();
  } catch (err) {
    console.error('Verification failed:', err);
    showToast('Error', 'Server connection error during verification.', 'error');
  }
}

function openCertModal(appt) {
  document.getElementById('cert-token').innerText = appt.tokenId;
  document.getElementById('cert-patient-name').innerText = appt.patientName;
  document.getElementById('cert-patient-id').innerText = appt.patientId;
  document.getElementById('cert-age').innerText = `${appt.patientAge} Years`;
  document.getElementById('cert-vaccine').innerText = `${appt.vaccineType} (Dose ${appt.doseNumber || 1})`;
  document.getElementById('cert-center').innerText = `${appt.centerName}, ${appt.pincode}`;
  document.getElementById('cert-date').innerText = appt.vaccinatedAt || appt.bookedAt || 'Confirmed';

  const qrBox = document.getElementById('cert-qr-box');
  qrBox.innerHTML = '';
  new QRCode(qrBox, {
    text: `CERT:${appt.tokenId}|PATIENT:${appt.patientName}|VAX:${appt.vaccineType}|VERIFIED:TRUE`,
    width: 140,
    height: 140
  });

  document.getElementById('cert-modal').style.display = 'flex';
}

function closeCertModal() {
  document.getElementById('cert-modal').style.display = 'none';
}

// --- Track My Booking (Self-Service) ---
function openTrackModal() {
  document.getElementById('track-modal').style.display = 'flex';
  const input = document.getElementById('track-search-input');
  if (input) {
    input.value = '';
    input.focus();
  }
  const resultBox = document.getElementById('track-result-box');
  if (resultBox) {
    resultBox.style.display = 'none';
    resultBox.innerHTML = '';
  }
}

function closeTrackModal() {
  document.getElementById('track-modal').style.display = 'none';
}

async function executeTrackBooking() {
  const query = document.getElementById('track-search-input')?.value.trim();
  const resultBox = document.getElementById('track-result-box');
  if (!query || !resultBox) {
    showToast('Input Required', 'Please enter your Token ID, Aadhaar, or Mobile Number.', 'warning');
    return;
  }

  resultBox.style.display = 'block';
  resultBox.innerHTML = '<div style="text-align:center; padding:1.5rem; color:#64748b;">🔍 Searching records...</div>';

  try {
    const res = await fetch(`${API_BASE}/api/track?q=${encodeURIComponent(query)}`);
    const data = await res.json();

    if (!data.found) {
      resultBox.innerHTML = `
        <div style="background:#fff1f2; border:1px solid #fecdd3; border-radius:10px; padding:1.25rem; text-align:center; color:#9f1239;">
          <div style="font-size:1.8rem; margin-bottom:0.3rem;">❌</div>
          <h4 style="font-size:1rem; margin-bottom:0.25rem;">No Record Found</h4>
          <p style="font-size:0.85rem; color:#64748b;">${escapeHtml(data.message)}</p>
        </div>
      `;
      return;
    }

    if (data.status === 'WAITLISTED') {
      const entry = data.waitlist;
      resultBox.innerHTML = `
        <div style="background:#fffbeb; border:1.5px solid #fde68a; border-radius:12px; padding:1.25rem;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.75rem;">
            <span class="badge" style="background:#fef3c7; color:#b45309; font-weight:700;">PRIORITY WAITLIST</span>
            <span style="font-size:0.82rem; font-weight:700; color:#b45309;">Position #${data.waitlistPosition} in Queue</span>
          </div>
          <h4 style="font-size:1.1rem; color:#0f172a; margin-bottom:0.3rem;">${escapeHtml(entry.patientName)}</h4>
          <p style="font-size:0.85rem; color:#64748b; margin-bottom:0.6rem;">📍 Center: <strong>${escapeHtml(entry.centerName)}</strong> • Slot: ${escapeHtml(entry.slotTime)}</p>
          <div style="background:white; border-radius:8px; padding:0.65rem; font-size:0.8rem; color:#475569; border:1px dashed #cbd5e1;">
            ⚡ <strong>Auto-Promotion Active:</strong> Because of our priority Max-Heap scheduler, you will be automatically assigned to this slot immediately when any patient cancels!
          </div>
        </div>
      `;
      return;
    }

    const a = data.appointment;
    const isVax = a.status === 'VACCINATED';
    const isCanc = a.status === 'CANCELLED';
    const badge = isVax 
      ? '<span class="badge badge-available">VACCINATED</span>' 
      : isCanc 
      ? '<span class="badge" style="background:#fee2e2; color:#b91c1c;">CANCELLED</span>' 
      : '<span class="badge badge-vaccine">CONFIRMED</span>';

    resultBox.innerHTML = `
      <div style="background:#ffffff; border:1.5px solid var(--border); border-radius:12px; padding:1.25rem; box-shadow:var(--shadow-sm);">
        <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:0.5rem;">
          <div>
            <div style="font-size:0.75rem; text-transform:uppercase; letter-spacing:0.8px; color:#64748b;">Token ID</div>
            <strong style="font-size:1.2rem; color:var(--primary); font-family:monospace;">${a.tokenId}</strong>
          </div>
          ${badge}
        </div>
        <div style="font-size:0.95rem; font-weight:700; color:#0f172a; margin:0.4rem 0 0.2rem 0;">${escapeHtml(a.patientName)} (Age: ${a.patientAge})</div>
        <div style="font-size:0.85rem; color:#64748b; margin-bottom:0.75rem;">
          🏥 ${escapeHtml(a.centerName)} • 💉 ${a.vaccineType} (Dose ${a.doseNumber || 1})<br>
          📅 Date: <strong>${a.date}</strong> | ⏰ Slot: <strong>${a.slotTime}</strong>
        </div>
        <div style="display:flex; gap:0.5rem; margin-top:0.75rem; flex-wrap:wrap;">
          <button class="btn-submit" style="background:#0f172a; padding:0.5rem 0.85rem; font-size:0.85rem; flex:1;" onclick='showReceiptModal(${JSON.stringify(a)})'>
            🖨️ View Appointment Pass
          </button>
          ${(!isVax && !isCanc) ? `
            <button class="btn-submit" style="background:#e0f2fe; color:#0369a1; border:1px solid #bae6fd; padding:0.5rem 0.85rem; font-size:0.85rem; flex:1;" onclick='openRescheduleModal(${JSON.stringify(a)})'>
              🔄 Reschedule Slot
            </button>
          ` : ''}
          ${isVax ? `
            <button class="btn-submit" style="background:#0284c7; padding:0.5rem 0.85rem; font-size:0.85rem; flex:1;" onclick='openCertModal(${JSON.stringify(a)})'>
              📜 View Certificate
            </button>
          ` : ''}
        </div>
      </div>
    `;

  } catch (err) {
    console.error('Track booking failed:', err);
    resultBox.innerHTML = '<div style="color:red; text-align:center; padding:1rem;">Failed to connect to server.</div>';
  }
}

// --- Admin Emergency Walk-in ---
function openWalkinModal() {
  document.getElementById('walkin-modal').style.display = 'flex';
}

function closeWalkinModal() {
  document.getElementById('walkin-modal').style.display = 'none';
  document.getElementById('walkin-form')?.reset();
}

async function submitAdminWalkin(e) {
  e.preventDefault();
  const token = sessionStorage.getItem('adminToken');
  if (!token) {
    showToast('Unauthorized', 'Staff login required.', 'error');
    handleAdminLogout();
    return;
  }

  const name = document.getElementById('walkin-name').value.trim();
  const id = document.getElementById('walkin-id').value.trim();
  const age = parseInt(document.getElementById('walkin-age').value) || 30;
  const phone = document.getElementById('walkin-phone').value.trim();
  const centerId = document.getElementById('walkin-center').value;
  const doseNumber = parseInt(document.getElementById('walkin-dose').value) || 1;
  const autoVaccinate = document.getElementById('walkin-autovaccinate').checked;

  const payload = {
    id, name, age, phone,
    centerId, slotId: 'S1',
    doseNumber, autoVaccinate,
    hasComorbidity: age >= 60
  };

  try {
    const res = await fetch(`${API_BASE}/api/admin/walkin`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(payload)
    });

    const result = await res.json();
    if (result.success) {
      closeWalkinModal();
      showToast('Walk-in Registered', result.message, 'success');
      refreshAdminData();

      if (autoVaccinate && result.appointment) {
        openCertModal(result.appointment);
      }
    } else {
      showToast('Registration Notice', result.message || result.error, 'warning');
    }
  } catch (err) {
    console.error('Walkin error:', err);
    showToast('Server Error', 'Failed to register walk-in patient.', 'error');
  }
}

// --- Admin Slot Capacity & Quota Manager ---
async function openCapacityModal() {
  const listContainer = document.getElementById('capacity-center-list');
  if (!listContainer) return;

  listContainer.innerHTML = '<div style="text-align:center; padding:1.5rem; color:#64748b;">Loading active center quotas...</div>';
  document.getElementById('capacity-modal').style.display = 'flex';

  try {
    const res = await fetch(`${API_BASE}/api/centers`);
    const centers = await res.json();

    listContainer.innerHTML = centers.map(c => `
      <div style="background:#f8fafc; border:1px solid var(--border); border-radius:10px; padding:1rem;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.6rem;">
          <div>
            <strong style="font-size:0.95rem; color:#0f172a;">${escapeHtml(c.name)}</strong>
            <span class="badge badge-vaccine" style="margin-left:0.4rem;">${c.vaccineType}</span>
          </div>
          <span class="badge badge-available">${c.totalAvailable} total slots open</span>
        </div>
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:0.5rem;">
          ${c.slots.map(s => `
            <div style="background:white; border:1px solid #e2e8f0; border-radius:8px; padding:0.6rem; display:flex; justify-content:space-between; align-items:center;">
              <div>
                <div style="font-size:0.8rem; font-weight:700;">${s.timeWindow}</div>
                <div style="font-size:0.75rem; color:${s.availableCount > 0 ? '#059669' : '#b45309'}; font-weight:600;">
                  ${s.availableCount} / ${s.totalCapacity} Available
                </div>
              </div>
              <button class="nav-btn primary" style="padding:0.35rem 0.65rem; font-size:0.75rem; cursor:pointer;" onclick="increaseSlotQuota('${c.id}', '${s.id}', 5)">
                +5 Doses
              </button>
            </div>
          `).join('')}
        </div>
      </div>
    `).join('');
  } catch (err) {
    console.error('Failed to load quota list:', err);
    listContainer.innerHTML = '<div style="color:red; text-align:center;">Failed to load quotas.</div>';
  }
}

function closeCapacityModal() {
  document.getElementById('capacity-modal').style.display = 'none';
}

async function increaseSlotQuota(centerId, slotId, delta) {
  const token = sessionStorage.getItem('adminToken');
  if (!token) {
    showToast('Unauthorized', 'Staff login required.', 'error');
    handleAdminLogout();
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/admin/slot/capacity`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ centerId, slotId, addedCapacity: delta })
    });

    const result = await res.json();
    if (result.success) {
      showToast('Quota Expanded', `Added ${delta} doses! Waiting patients auto-promoted if available.`, 'success');
      openCapacityModal(); // Re-render modal
      refreshAdminData();
      loadCenters();
    } else {
      showToast('Quota Error', result.message || 'Failed to update quota.', 'warning');
    }
  } catch (err) {
    console.error('Capacity update failed:', err);
    showToast('Server Error', 'Could not update slot quota.', 'error');
  }
}

// --- Simulated Phone / SMS & WhatsApp Notifications ---
let simulatedSmsHistory = [];
let unreadSmsCount = 0;

function triggerSimulatedSms(platform, title, message) {
  const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const entry = { platform, title, message, timestamp };
  simulatedSmsHistory.unshift(entry);
  unreadSmsCount++;

  // Update navbar badge
  const badge = document.getElementById('sms-unread-badge');
  if (badge) {
    badge.innerText = unreadSmsCount;
    badge.style.display = 'inline-block';
  }

  // Floating banner
  const wrapper = document.getElementById('sms-popup-wrapper');
  if (wrapper) {
    const isWhatsApp = platform === 'whatsapp';
    const banner = document.createElement('div');
    banner.className = 'sms-banner';
    banner.onclick = toggleSmsDrawer;
    banner.innerHTML = `
      <div class="sms-app-icon ${isWhatsApp ? 'whatsapp' : 'sms'}">${isWhatsApp ? '🟢' : '💬'}</div>
      <div class="sms-banner-body">
        <div class="sms-banner-header">
          <span class="sms-app-name">${isWhatsApp ? 'WhatsApp Alert' : 'VaxSlot Health SMS'}</span>
          <span class="sms-banner-time">${timestamp}</span>
        </div>
        <div class="sms-banner-title">${escapeHtml(title)}</div>
        <div class="sms-banner-text">${escapeHtml(message)}</div>
      </div>
    `;
    wrapper.appendChild(banner);

    setTimeout(() => {
      banner.classList.add('fade-out');
      setTimeout(() => banner.remove(), 350);
    }, 6000);
  }

  updateSmsDrawerList();
}

function toggleSmsDrawer() {
  const drawer = document.getElementById('sms-drawer');
  if (!drawer) return;
  const isOpening = drawer.style.display !== 'flex';
  drawer.style.display = isOpening ? 'flex' : 'none';

  if (isOpening) {
    unreadSmsCount = 0;
    const badge = document.getElementById('sms-unread-badge');
    if (badge) badge.style.display = 'none';
    updateSmsDrawerList();
  }
}

function updateSmsDrawerList() {
  const listEl = document.getElementById('sms-drawer-list');
  if (!listEl) return;

  if (simulatedSmsHistory.length === 0) {
    listEl.innerHTML = `
      <div style="text-align:center; padding:2.5rem 1rem; color:#94a3b8; font-size:0.85rem;">
        <div style="font-size:2rem; margin-bottom:0.5rem;">📱</div>
        No notifications yet.<br>Book, reschedule, or cancel a slot to see simulated SMS alerts!
      </div>
    `;
    return;
  }

  listEl.innerHTML = simulatedSmsHistory.map(item => `
    <div class="sms-bubble-card">
      <div class="sms-bubble-header">
        <span>${item.platform === 'whatsapp' ? '🟢 WhatsApp Broadcast' : '💬 Carrier SMS (TX-VAXSLOT)'}</span>
        <span>${item.timestamp}</span>
      </div>
      <div style="font-weight:700; font-size:0.88rem; color:#0f172a; margin-bottom:0.25rem;">
        ${escapeHtml(item.title)}
      </div>
      <div class="sms-bubble-content">
        ${escapeHtml(item.message)}
      </div>
    </div>
  `).join('');
}

// --- 1-Click Slot Rescheduling ---
function openRescheduleModal(appt) {
  document.getElementById('reschedule-token-id').value = appt.tokenId;
  document.getElementById('reschedule-current-desc').innerHTML = `
    ${escapeHtml(appt.patientName)} • ${escapeHtml(appt.centerName)}<br>
    Current Slot: <strong>${appt.date} (${appt.slotTime})</strong>
  `;

  // Set min date to today
  const todayStr = new Date().toISOString().split('T')[0];
  const dateInput = document.getElementById('reschedule-date');
  if (dateInput) {
    dateInput.min = todayStr;
    dateInput.value = appt.date || todayStr;
  }

  document.getElementById('reschedule-modal').style.display = 'flex';
}

function closeRescheduleModal() {
  document.getElementById('reschedule-modal').style.display = 'none';
}

async function submitReschedule(e) {
  e.preventDefault();
  const tokenId = document.getElementById('reschedule-token-id').value;
  const newSlotId = document.getElementById('reschedule-slot-select').value;
  const newDate = document.getElementById('reschedule-date').value;

  try {
    const res = await fetch(`${API_BASE}/api/reschedule`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tokenId, newSlotId, newDate })
    });

    const result = await res.json();
    if (result.success) {
      closeRescheduleModal();
      showToast('Appointment Rescheduled', result.message, 'success');
      loadCenters();

      // Trigger simulated SMS
      triggerSimulatedSms(
        'sms',
        'Appointment Rescheduled',
        `Dear ${result.appointment.patientName}, your appointment ${tokenId} was rescheduled to ${result.appointment.date} (${result.appointment.slotTime}) at ${result.appointment.centerName}.`
      );

      // Re-trigger track lookup to update UI if track modal is open
      const trackInput = document.getElementById('track-search-input');
      if (trackInput && trackInput.value) {
        executeTrackBooking();
      }
    } else {
      showToast('Reschedule Notice', result.message || 'Unable to reschedule.', 'warning');
    }
  } catch (err) {
    console.error('Reschedule error:', err);
    showToast('Server Error', 'Failed to connect for rescheduling.', 'error');
  }
}

// --- Age Category Filtering ---
let currentAgeFilter = 'all';

function applyAgeFilter(category) {
  currentAgeFilter = category;
  document.querySelectorAll('.age-chip').forEach(el => el.classList.remove('active'));

  const chipMap = {
    'all': 'age-all',
    'youth': 'age-youth',
    'adults': 'age-adults',
    'presenior': 'age-presenior',
    'seniors': 'age-senior'
  };
  const activeId = chipMap[category];
  if (activeId) {
    document.getElementById(activeId)?.classList.add('active');
  }

  if (category === 'all') {
    renderCenters(allCenters);
    return;
  }

  // Filter criteria based on medical approval & priority
  const filtered = allCenters.filter(c => {
    const vax = c.vaccineType.toLowerCase();
    if (category === 'youth') {
      // 12-17 years approved for Covaxin and Pfizer
      return vax.includes('covaxin') || vax.includes('pfizer');
    } else if (category === 'adults') {
      return true;
    } else if (category === 'presenior') {
      return true;
    } else if (category === 'seniors') {
      return true;
    }
    return true;
  });

  renderCenters(filtered);
  showToast('Age Category Filter', `Showing centers matching ${category.toUpperCase()} criteria.`, 'info');
}

// --- Real-time Admin Vaccine Inventory Monitor & Low Stock Alerts ---
async function loadAdminInventory() {
  const container = document.getElementById('admin-inventory-grid');
  if (!container) return;

  try {
    const res = await fetch(`${API_BASE}/api/centers`);
    const centers = await res.json();

    container.innerHTML = centers.map(c => {
      const isLowStock = (c.totalAvailable || 0) < 8;
      const statusPill = isLowStock 
        ? `<span class="badge-low-stock">⚠️ LOW STOCK (${c.totalAvailable} Doses Left)</span>`
        : `<span class="badge-stock-ok">✅ Healthy Quota (${c.totalAvailable} Available)</span>`;

      return `
        <div style="background:white; border:1.5px solid ${isLowStock ? '#fca5a5' : 'var(--border)'}; border-radius:12px; padding:1.1rem; box-shadow:var(--shadow-sm);">
          <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:0.5rem; gap:0.5rem;">
            <div>
              <strong style="font-size:0.95rem; color:#0f172a;">${escapeHtml(c.name)}</strong>
              <div style="font-size:0.8rem; color:#64748b;">📍 ${escapeHtml(c.district)} (${c.pincode})</div>
            </div>
            <span class="badge badge-vaccine">${c.vaccineType}</span>
          </div>

          <div style="margin: 0.75rem 0;">
            ${statusPill}
          </div>

          <div style="display:flex; justify-content:space-between; align-items:center; margin-top:0.75rem; padding-top:0.6rem; border-top:1px dashed #e2e8f0;">
            <span style="font-size:0.75rem; color:#64748b;">Automated replenishment:</span>
            <button class="nav-btn primary" style="padding:0.35rem 0.75rem; font-size:0.75rem; cursor:pointer;" onclick="quickRestockCenter('${c.id}', 20)">
              📦 Restock (+20 Doses)
            </button>
          </div>
        </div>
      `;
    }).join('');
  } catch (err) {
    console.error('Inventory monitor error:', err);
  }
}

async function quickRestockCenter(centerId, delta) {
  const token = sessionStorage.getItem('adminToken');
  if (!token) {
    showToast('Unauthorized', 'Staff login required.', 'error');
    handleAdminLogout();
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/admin/slot/capacity`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ centerId, slotId: 'S1', addedCapacity: delta })
    });

    const result = await res.json();
    if (result.success) {
      showToast('Inventory Replenished', `Added ${delta} doses! Waiting patients auto-promoted if available.`, 'success');
      loadAdminInventory();
      refreshAdminData();
    } else {
      showToast('Restock Failed', result.message, 'warning');
    }
  } catch (err) {
    console.error('Restock error:', err);
    showToast('Server Error', 'Could not replenish inventory.', 'error');
  }
}

// Utility Helpers
function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeJs(str) {
  if (!str) return '';
  return str.replace(/'/g, "\\'").replace(/"/g, '\\"');
}
