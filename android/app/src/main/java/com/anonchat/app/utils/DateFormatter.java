package com.anonchat.app.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateFormatter {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public static String formatMessageTime(long timestamp) {
        if (timestamp <= 0) return "";
        return TIME_FORMAT.format(new Date(timestamp));
    }

    public static String formatConversationTime(long timestamp) {
        if (timestamp <= 0) return "";
        Calendar msgTime = Calendar.getInstance();
        msgTime.setTimeInMillis(timestamp);

        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.DATE) == msgTime.get(Calendar.DATE) &&
            now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)) {
            return TIME_FORMAT.format(new Date(timestamp));
        }

        return DATE_FORMAT.format(new Date(timestamp));
    }
}
