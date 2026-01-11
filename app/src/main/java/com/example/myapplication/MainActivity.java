package com.example.myapplication;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

//firebase libs
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
//time libs
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private LinearLayout hourSlotsContainer;
    private TextView tvSelectedDate;
    private String userId;


    private Calendar selectedDate = Calendar.getInstance();
    private SimpleDateFormat dateFormatUI = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
    private SimpleDateFormat dateIdFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private FirebaseFirestore db;
    //save slot key hash
    private Map<String, String> loadedSlots = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);
        db = FirebaseFirestore.getInstance();

        calendarView = findViewById(R.id.calendarView);
        hourSlotsContainer = findViewById(R.id.hourSlotsContainer);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        normalizeSelectedDate();
        updateSelectedDateLabel();

        //check if loged in - remember me
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        ImageButton signoutBtn = findViewById(R.id.btnSignout);

        signoutBtn.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        ImageButton upcomingBtn = findViewById(R.id.btnUpcoming);

        upcomingBtn.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, UpcomingActivity.class));
        });

        // Handle date changes
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate.set(year, month, dayOfMonth);
            normalizeSelectedDate();
            updateSelectedDateLabel();
            loadDayFromFirestore();
        });
    }


    private void normalizeSelectedDate() {
        selectedDate.set(Calendar.HOUR_OF_DAY, 0);
        selectedDate.set(Calendar.MINUTE, 0);
        selectedDate.set(Calendar.SECOND, 0);
        selectedDate.set(Calendar.MILLISECOND, 0);
    }

    private void updateSelectedDateLabel() {
        tvSelectedDate.setText("Selected: " + dateFormatUI.format(selectedDate.getTime()));
    }

    // FIRESTORE: LOAD DAY
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
                        Object rawSlots = doc.get("slots");
                        if (rawSlots instanceof Map) {
                            Map<String, Object> mapObj = (Map<String, Object>) rawSlots;
                            for (String key : mapObj.keySet()) {
                                loadedSlots.put(key, mapObj.get(key).toString());
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        generateHourSlots();
                    }
                })
                .addOnFailureListener(e -> {
                    loadedSlots.clear();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        generateHourSlots();
                    }
                });
    }


    // BUILD FULL HOUR SLOTS UI
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void generateHourSlots() {
        hourSlotsContainer.removeAllViews();
        Log.d("UI", "Generating hour slots for date: " + dateFormatUI.format(selectedDate.getTime()));

        LocalTime start = LocalTime.of(6, 0);
        LocalTime end = LocalTime.of(18, 0);

        for (LocalTime t = start; !t.isAfter(end); t = t.plusMinutes(10)) {
            String timeStr = String.format(Locale.getDefault(), "%02d:%02d", t.getHour(), t.getMinute());
            Log.d("UI", "Creating slot for time: " + timeStr);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(6, 12, 6, 12);

            TextView hourLabel = new TextView(this);
            hourLabel.setText(timeStr);
            hourLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            EditText taskInput = new EditText(this);
            taskInput.setHint("Add appointment");
            taskInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));

            if (loadedSlots.containsKey(timeStr)) {
                String taskValue = loadedSlots.get(timeStr);
                taskInput.setText(taskValue);
                Log.d("UI", "Setting appointment for " + timeStr + ": " + taskValue);
            }

            ImageView saveBtn = new ImageView(this);
            saveBtn.setImageResource(android.R.drawable.checkbox_on_background);
            saveBtn.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
            saveBtn.setPadding(8, 8, 8, 8);

            saveBtn.setOnClickListener(v -> {
                String task = taskInput.getText().toString().trim();
                saveSlotToFirestore(timeStr, task);
                Log.d("UI", "Save button clicked for " + timeStr + ": " + task);
                Toast.makeText(this, "Saved " + timeStr, Toast.LENGTH_SHORT).show();
            });

            ImageView deleteBtn = new ImageView(this);
            deleteBtn.setImageResource(android.R.drawable.ic_delete);
            deleteBtn.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
            deleteBtn.setPadding(8, 8, 8, 8);

            deleteBtn.setOnClickListener(v -> {
                taskInput.setText("");
                deleteSlotFromFirestore(timeStr);
                Log.d("UI", "Delete button clicked for " + timeStr);
                Toast.makeText(this, "Deleted " + timeStr, Toast.LENGTH_SHORT).show();
            });

            row.addView(hourLabel);
            row.addView(taskInput);
            row.addView(saveBtn);
            row.addView(deleteBtn);

            hourSlotsContainer.addView(row);
        }
    }


    private void saveSlotToFirestore(String time, String task) {
        String dateId = dateIdFormat.format(selectedDate.getTime());

        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(dateId)
                .update("slots." + time, task)
                .addOnSuccessListener(aVoid -> {
                })
                .addOnFailureListener(e -> {
                    Map<String, Object> slotsMap = new HashMap<>();
                    slotsMap.put(time, task);
                    Map<String, Object> newDoc = new HashMap<>();
                    newDoc.put("slots", slotsMap);

                    db.collection("users")
                            .document(userId)
                            .collection("schedules")
                            .document(dateId)
                            .set(newDoc);
                });
    }



    private void deleteSlotFromFirestore(String time) {
        String dateId = dateIdFormat.format(selectedDate.getTime());

        db.collection("users")
                .document(userId)
                .collection("schedules")
                .document(dateId)
                .update("slots." + time, FieldValue.delete());
    }
}
