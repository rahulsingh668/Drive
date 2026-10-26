package com.rahul.drive;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** Run on a desktop JVM; no Android framework or stereo is required. */
public final class ThemePolicyTest {
    private static int checks;
    private static Calendar at(String zone, int hour, int minute) {
        Calendar value = new GregorianCalendar(TimeZone.getTimeZone(zone));
        value.clear(); value.set(2026, Calendar.OCTOBER, 26, hour, minute, 0); return value;
    }
    private static void check(String label, boolean expectedNight, String mode, Calendar clock) {
        checks++;
        if (ThemePolicy.isNight(mode, clock) != expectedNight) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check("midnight", true, "auto", at("Asia/Kolkata",0,0));
        check("before day boundary", true, "auto", at("Asia/Kolkata",7,59));
        check("day starts at 08:00", false, "auto", at("Asia/Kolkata",8,0));
        check("last day minute", false, "auto", at("Asia/Kolkata",17,59));
        check("night starts at 18:00", true, "auto", at("Asia/Kolkata",18,0));
        check("last night minute", true, "auto", at("Asia/Kolkata",23,59));
        check("manual day overrides night", false, "day", at("Asia/Kolkata",23,59));
        check("manual night overrides day", true, "night", at("Asia/Kolkata",12,0));
        check("invalid mode safely uses schedule", false, "unknown", at("Asia/Kolkata",12,0));
        Calendar local = at("Asia/Kolkata",8,0);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")); utc.setTimeInMillis(local.getTimeInMillis());
        check("local clock uses local hour", false, "auto", local);
        check("same instant in a different zone uses that zone", true, "auto", utc);
        if (!"auto".equals(ThemePolicy.normalize(null)) || !"auto".equals(ThemePolicy.normalize("unknown")))
            throw new AssertionError("Invalid preferences must default to Auto");
        System.out.println("PASS: " + checks + " schedule/override/timezone cases and invalid-preference fallback.");
    }
}
