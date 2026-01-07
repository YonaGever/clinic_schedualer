package com.example.myapplication;

import android.os.Bundle;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.*;

public class UpcomingActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String userId;

    private final SimpleDateFormat dateIdFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upcoming);

        ListView listUpcoming = findViewById(R.id.listUpcoming);

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseFirestore.getInstance();

        loadUpcoming7Days(listUpcoming);
    }

    private void loadUpcoming7Days(ListView listUpcoming) {
        Calendar today = Calendar.getInstance();
        normalize(today);

        List<UpcomingRow> rows = new ArrayList<>();
        final int[] done = {0};

        for (int i = 0; i < 7; i++) {
            Calendar c = (Calendar) today.clone();
            c.add(Calendar.DAY_OF_YEAR, i);

            String dateId = dateIdFormat.format(c.getTime());

            db.collection("users")
                    .document(userId)
                    .collection("schedules")
                    .document(dateId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists() && doc.get("slots") instanceof Map) {
                            Map<String, Object> slots =
                                    (Map<String, Object>) doc.get("slots");

                            List<String> times = new ArrayList<>(slots.keySet());
                            Collections.sort(times);

                            for (String time : times) {
                                String task = String.valueOf(slots.get(time)).trim();
                                if (!task.isEmpty()) {
                                    rows.add(new UpcomingRow(dateId, time, task));
                                }
                            }
                        }

                        if (++done[0] == 7) finishAndShow(listUpcoming, rows);
                    })
                    .addOnFailureListener(e -> {
                        if (++done[0] == 7) finishAndShow(listUpcoming, rows);
                    });
        }
    }

    private void finishAndShow(ListView listUpcoming, List<UpcomingRow> rows) {
        if (rows.isEmpty()) {
            rows.add(new UpcomingRow("-", "-", "אין תורים ב-7 הימים הקרובים"));
        }

        Collections.sort(rows, (a, b) -> {
            int d = a.date.compareTo(b.date);
            if (d != 0) return d;
            return a.time.compareTo(b.time);
        });

        listUpcoming.setAdapter(new UpcomingAdapter(this, rows));
    }

    private void normalize(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }
}
