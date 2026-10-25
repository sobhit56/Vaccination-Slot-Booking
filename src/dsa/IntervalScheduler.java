package dsa;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * IntervalScheduler Algorithm.
 * Implements clinical vaccine interval scheduling constraints.
 * 
 * Rules:
 *   - Covishield: Minimum 84 days between Dose 1 and Dose 2
 *   - Covaxin:    Minimum 28 days between Dose 1 and Dose 2
 *   - Pfizer:     Minimum 21 days between Dose 1 and Dose 2
 */
public class IntervalScheduler {

    public static class IntervalValidationResult {
        public boolean eligible;
        public int requiredDays;
        public long elapsedDays;
        public String nextEligibleDate;
        public String message;

        public IntervalValidationResult(boolean eligible, int requiredDays, long elapsedDays, String nextEligibleDate, String message) {
            this.eligible = eligible;
            this.requiredDays = requiredDays;
            this.elapsedDays = elapsedDays;
            this.nextEligibleDate = nextEligibleDate;
            this.message = message;
        }
    }

    public static int getRequiredIntervalDays(String vaccineType) {
        if (vaccineType == null) return 28;
        String vax = vaccineType.trim().toLowerCase();
        if (vax.contains("covishield")) return 84;
        if (vax.contains("covaxin")) return 28;
        if (vax.contains("pfizer")) return 21;
        return 28; // default
    }

    /**
     * Validates whether a patient is eligible for Dose 2 based on the date of Dose 1.
     */
    public static IntervalValidationResult validateDoseInterval(String vaccineType, int doseNumber, String dose1DateStr, String bookingDateStr) {
        if (doseNumber <= 1) {
            return new IntervalValidationResult(true, 0, 0, "", "Eligible for Dose 1.");
        }

        if (dose1DateStr == null || dose1DateStr.trim().isEmpty()) {
            return new IntervalValidationResult(false, 0, 0, "", "Dose 1 vaccination date is required for Dose 2 booking.");
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date dose1Date = sdf.parse(dose1DateStr.trim());
            Date bookingDate = (bookingDateStr != null && !bookingDateStr.trim().isEmpty()) 
                               ? sdf.parse(bookingDateStr.trim()) : new Date();

            long diffInMillis = bookingDate.getTime() - dose1Date.getTime();
            long elapsedDays = TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS);

            int requiredDays = getRequiredIntervalDays(vaccineType);

            if (elapsedDays < requiredDays) {
                long neededMillis = dose1Date.getTime() + TimeUnit.DAYS.toMillis(requiredDays);
                String eligibleDate = sdf.format(new Date(neededMillis));
                String message = String.format(
                    "Ineligible for Dose 2 today. %s requires minimum %d days interval. Only %d days elapsed. Next eligible date: %s",
                    vaccineType, requiredDays, elapsedDays, eligibleDate
                );
                return new IntervalValidationResult(false, requiredDays, elapsedDays, eligibleDate, message);
            }

            return new IntervalValidationResult(true, requiredDays, elapsedDays, "", 
                String.format("Eligible for Dose 2! (%d days elapsed >= %d days required)", elapsedDays, requiredDays));

        } catch (Exception e) {
            return new IntervalValidationResult(false, 0, 0, "", "Invalid date format. Expected YYYY-MM-DD.");
        }
    }
}
