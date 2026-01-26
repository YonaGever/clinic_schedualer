package com.example.myapplication;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.*;
import android.Manifest;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private LinearLayout hourSlotsContainer;
    private TextView tvSelectedDate;
    private String userId;

    private Calendar selectedDate = Calendar.getInstance();
    private SimpleDateFormat dateFormatUI =
            new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
    private SimpleDateFormat dateIdFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private FirebaseFirestore db;

    // ===== SLOT MODEL =====
    private static class SlotData {
        String text;
        boolean isAlert;

        SlotData(String text, boolean isAlert) {
            this.text = text;
            this.isAlert = isAlert;
        }
    }

    private Map<String, SlotData> loadedSlots = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);
        db = FirebaseFirestore.getInstance();

        calendarView = findViewById(R.id.calendarView);
        hourSlotsContainer = findViewById(R.id.hourSlotsContainer);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        normalizeSelectedDate();
        updateSelectedDateLabel();
        loadDayFromFirestore();

        calendarView.setOnDateChangeListener((view, year, month, day) -> {
            selectedDate.set(year, month, day);
            normalizeSelectedDate();
            updateSelectedDateLabel();
            loadDayFromFirestore();
        });

        ImageButton signoutBtn = findViewById(R.id.btnSignout);
        signoutBtn.setOnClickListener(v -> { FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        ImageButton upcomingBtn = findViewById(R.id.btnUpcoming);
        upcomingBtn.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, UpcomingActivity.class));
        });

        fetchWeatherAndShowDialog();
        checkNotificationPermission();

    }

    private void fetchWeatherAndShowDialog() {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=32.707454261749376&longitude=35.12029674468908&daily=temperature_2m_mean&timezone=auto&forecast_days=1";

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        // Navigate the JSON: daily -> temperature_2m_mean -> first index [0]
                        JSONObject daily = response.getJSONObject("daily");
                        JSONArray temps = daily.getJSONArray("temperature_2m_mean");
                        double avgTemp = temps.getDouble(0);

                        showWeatherDialog(avgTemp);
                    } catch (Exception e) {
                        Log.e("WeatherError", "Parsing error", e);
                    }
                },
                error -> Log.e("WeatherError", "Request failed", error)
        );

        queue.add(jsonObjectRequest);
    }

    private void showWeatherDialog(double temp) {
        new AlertDialog.Builder(this)
                .setTitle("תחזית מזג האוויר להיום")
                .setMessage("הטמפ' הממוצעת להיום תיהיה " + temp + "°C")
                .setPositiveButton("OK", null)
                .show();
    }


    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                // Request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    // Optional: Handle the user's response
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notifications denied. You won't see alerts.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void normalizeSelectedDate() {
        selectedDate.set(Calendar.HOUR_OF_DAY, 0);
        selectedDate.set(Calendar.MINUTE, 0);
        selectedDate.set(Calendar.SECOND, 0);
        selectedDate.set(Calendar.MILLISECOND, 0);
    }

    private void updateSelectedDateLabel() {
        tvSelectedDate.setText("Selected: " +
                dateFormatUI.format(selectedDate.getTime()));
    }

    // ===== LOAD DAY =====
    private void loadDayFromFirestore() {
        String dateId = dateIdFormat.format(selectedDate.getTime());

        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(dateId)
                .get()
                .addOnSuccessListener(doc -> {
                    loadedSlots.clear();

                    if (doc.exists()) {
                        Object raw = doc.get("slots");
                        if (raw instanceof Map) {
                            Map<String, Object> map = (Map<String, Object>) raw;
                            for (String key : map.keySet()) {
                                Map<String, Object> slot =
                                        (Map<String, Object>) map.get(key);
                                String text = (String) slot.get("text");
                                boolean isAlert =
                                        slot.get("is_alert") != null &&
                                                (boolean) slot.get("is_alert");
                                loadedSlots.put(key,
                                        new SlotData(text, isAlert));
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        generateHourSlots();
                    }
                });
    }

    // ===== BUILD UI =====
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void generateHourSlots() {
        hourSlotsContainer.removeAllViews();

        LocalTime start = LocalTime.of(6, 0);
        LocalTime end = LocalTime.of(18, 0);

        for (LocalTime t = start; !t.isAfter(end); t = t.plusMinutes(10)) {

            String timeStr = String.format(
                    Locale.getDefault(), "%02d:%02d",
                    t.getHour(), t.getMinute());

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(6, 12, 6, 12);
            row.setBackgroundColor(Color.TRANSPARENT);

            TextView hourLabel = new TextView(this);
            hourLabel.setText(timeStr);
            hourLabel.setLayoutParams(
                    new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            EditText taskInput = new EditText(this);
            taskInput.setHint("Add appointment");
            taskInput.setLayoutParams(
                    new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 2));

            if (loadedSlots.containsKey(timeStr)) {
                SlotData data = loadedSlots.get(timeStr);
                taskInput.setText(data.text);
                if (data.isAlert) {
                    row.setBackgroundColor(0x88FF0000);
                }
            }

            ImageView saveBtn = new ImageView(this);
            saveBtn.setImageResource(android.R.drawable.checkbox_on_background);
            saveBtn.setOnClickListener(v -> {
                boolean isAlert =
                        loadedSlots.containsKey(timeStr)
                                && loadedSlots.get(timeStr).isAlert;

                saveSlotWithAlert(
                        timeStr,
                        taskInput.getText().toString(),
                        isAlert
                );

                Toast.makeText(this,
                        "Saved " + timeStr,
                        Toast.LENGTH_SHORT).show();
            });

            ImageView deleteBtn = new ImageView(this);
            deleteBtn.setImageResource(android.R.drawable.ic_delete);
            deleteBtn.setOnClickListener(v -> {
                deleteSlot(timeStr);
                cancelAlarm(timeStr);
                loadedSlots.remove(timeStr);

                taskInput.setText("");
                row.setBackgroundColor(Color.TRANSPARENT);

                Toast.makeText(this,
                        "Deleted " + timeStr,
                        Toast.LENGTH_SHORT).show();
            });

            // ===== LONG PRESS ALERT TOGGLE =====
            // Update the Long Click Listener inside generateHourSlots:
            row.setOnLongClickListener(v -> {
                SlotData currentData = loadedSlots.get(timeStr);
                boolean newAlertStatus = (currentData == null) || !currentData.isAlert;
                String currentText = taskInput.getText().toString();

                // 1. Update UI and Local Cache
                if (newAlertStatus) {
                    row.setBackgroundColor(0x88FF0000);
                    scheduleAlarm(timeStr, currentText);
                    Toast.makeText(this, "Alert enabled", Toast.LENGTH_SHORT).show();
                } else {
                    row.setBackgroundColor(Color.TRANSPARENT);
                    cancelAlarm(timeStr);
                    Toast.makeText(this, "Alert disabled", Toast.LENGTH_SHORT).show();
                }

                // 2. Persist to Firestore
                saveSlotWithAlert(timeStr, currentText, newAlertStatus);

                return true;
            });

            row.addView(hourLabel);
            row.addView(taskInput);
            row.addView(saveBtn);
            row.addView(deleteBtn);
            hourSlotsContainer.addView(row);
        }
    }

    // ===== FIRESTORE =====
    private void saveSlot(String time, String text) {
        saveSlotWithAlert(time, text, false);
    }

    private void saveSlotWithAlert(String time, String text, boolean isAlert) {
        String dateId = dateIdFormat.format(selectedDate.getTime());

        // Update local cache immediately for UI consistency
        loadedSlots.put(time, new SlotData(text, isAlert));

        Map<String, Object> slotData = new HashMap<>();
        slotData.put("text", text);
        slotData.put("is_alert", isAlert);

        Map<String, Object> update = new HashMap<>();
        Map<String, Object> nestedSlot = new HashMap<>();
        nestedSlot.put(time, slotData);
        update.put("slots", nestedSlot);

        // .set with merge is safer than .update for new documents
        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(dateId)
                .set(update, SetOptions.merge())
                .addOnFailureListener(e -> Log.e("Firestore", "Error saving", e));
    }

    private void deleteSlot(String time) {
        String dateId = dateIdFormat.format(selectedDate.getTime());

        loadedSlots.remove(time); // Remove from local cache

        Map<String, Object> updates = new HashMap<>();
        updates.put("slots." + time, FieldValue.delete());

        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(dateId)
                .update(updates);
    }



    // ===== ALARM MANAGER =====
    private void scheduleAlarm(String time, String text) {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);

        // 1. Check for Exact Alarm permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                // Option A: Redirect user to system settings
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
                Toast.makeText(this, "Please allow exact alarms for reminders", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Calendar alarmTime = (Calendar) selectedDate.clone();
        String[] p = time.split(":");
        alarmTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0]));
        alarmTime.set(Calendar.MINUTE, Integer.parseInt(p[1]));
        alarmTime.add(Calendar.MINUTE, -10);

        // 2. Prevent scheduling in the past
        if (alarmTime.getTimeInMillis() <= System.currentTimeMillis()) {
            return;
        }

        Intent intent = new Intent(this, AlertReceiver.class);
        intent.putExtra("time", time);
        intent.putExtra("text", text);

        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                Objects.hash(selectedDate.getTimeInMillis(), time),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 3. Set the alarm safely
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pi);
        } catch (SecurityException e) {
            // Fallback for edge cases where permission was revoked mid-process
            am.set(AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pi);
        }
    }

    private void cancelAlarm(String time) {
        Intent intent = new Intent(this, AlertReceiver.class);

        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                Objects.hash(selectedDate.getTimeInMillis(), time),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am =
                (AlarmManager) getSystemService(ALARM_SERVICE);
        am.cancel(pi);
    }
}
