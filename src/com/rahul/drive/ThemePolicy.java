package com.rahul.drive;

import java.util.Calendar;

/** Local wall-clock policy; no location, alarm, or background service is needed. */
public final class ThemePolicy {
    public static final String AUTO = "auto", DAY = "day", NIGHT = "night";
    private ThemePolicy() { }

    public static String normalize(String mode) {
        return DAY.equals(mode) || NIGHT.equals(mode) ? mode : AUTO;
    }

    public static boolean isNight(String mode, Calendar localTime) {
        if (DAY.equals(mode)) return false;
        if (NIGHT.equals(mode)) return true;
        int hour = localTime.get(Calendar.HOUR_OF_DAY);
        return hour < 8 || hour >= 18;
    }
}
