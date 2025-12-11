// ==========================================
// IMPORTY I DEKLARACJE
// ==========================================

package com.example.mobilesensor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.StatFs;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;  // Poprawiony import

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

// ==========================================
// STAŁE I ZMIENNE GLOBALNE
// ==========================================

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String API_KEY = "73388daab4f30826e3f8cca01c2ddb04";
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=pl";
    public static final String FIREBASE_URL = "https://mobilesensormonitor-default-rtdb.europe-west1.firebasedatabase.app";

    private static final int REQUEST_CODE_GPS_PERMISSION = 100;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 101;
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 102;

    private static final int EKRAN_OGOLNE = 1;
    private static final int EKRAN_GPS = 2;
    private static final int EKRAN_ZYROSKOP = 3;
    private static final int EKRAN_SYSTEM = 4;
    private static final int EKRAN_APLIKACJA = 5;

    private static final int MAX_LOCAL_POINTS = 240;
    private static final long RAM_PUBLISH_INTERVAL_MS = 60_000;

    private TextView opisParametrow;
    private LinearLayout layoutWykresy;
    private LinearLayout layoutAplikacjaWykres;
    private LineChart chartRam, chartBattery, chartTemp, chartLight, chartCpu;
    private LineChart chartAppRam, chartAppBattery, chartAppTemp, chartAppCpu;
    private Spinner spinnerDevices;
    private EditText etLimit;
    private Button btnLoad;

    private int aktualnieWybranyEkran = EKRAN_OGOLNE;

    private SensorManager sensorManager;
    private Sensor lightSensor, gyroscopeSensor, accelerometerSensor;
    private float aktualneSwiatloLx = 0.0f;
    private final float[] aktualnyZyroskop = {0,0,0};
    private final float[] aktualnyAkcelerometr = {0,0,0};

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Geocoder geocoder;
    private double aktualnaSzerokosc = 0.0;
    private double aktualnaDlugosc = 0.0;
    private float aktualnaDokladnosc = 0.0f;

    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashlightOn = false;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler ramHandler = new Handler(Looper.getMainLooper());
    private Runnable ramRunnable;

    private long lastPublishTs = 0;
    private double sumRam = 0;
    private double sumTemp = 0;
    private double sumCpu = 0;
    private int countSamples = 0;

    private boolean firebasePolaczony = false;
    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference firebaseRootRef;
    private DatabaseReference firebaseDeviceRef;
    private ValueEventListener ramFirebaseListener;
    private String deviceId;

// ==========================================
// KLASA WEWNĘTRZNA RAMSAMPLE
// ==========================================

    public static class RamSample {
        public long czas;
        public double ram_uzycie;  // Zmienione z ram_wolne na ram_uzycie
        public int bateria_poziom;
        public double bateria_temp;
        public double cpu_freq;
        public RamSample() {}
        public RamSample(long czas, double ram_uzycie, int bateria_poziom, double bateria_temp, double cpu_freq){
            this.czas=czas; this.ram_uzycie=ram_uzycie; this.bateria_poziom=bateria_poziom; this.bateria_temp=bateria_temp; this.cpu_freq=cpu_freq;
        }
    }

// ==========================================
// METODY ŻYCIA AKTYWNOŚCI
// ==========================================

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initFirebase();
        setupUI();
        setupSensors();
        setupLocation();
        setupFlashlight();
        setupButtons();
        setupRamRefresher();

        ensureNotificationPermissionAndStartService();

        startRamRefresher();
        sprawdzIpoprosOPermISjeGPS();
        wyswietlInformacjeOgolne();
    }

    @Override protected void onResume(){
        super.onResume();
        if (sensorManager != null) {
            if (lightSensor != null) sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_GAME);
            if (gyroscopeSensor != null) sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
            if (accelerometerSensor != null) sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        if (aktualnieWybranyEkran == EKRAN_GPS) uruchomNasluchiwanieGPS();
        if (aktualnieWybranyEkran == EKRAN_OGOLNE || aktualnieWybranyEkran == EKRAN_SYSTEM) startRamRefresher();
    }

    @Override protected void onPause(){
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        zatrzymajNasluchiwanieGPS();
        stopRamRefresher();
        if (isFlashlightOn) {
            try { cameraManager.setTorchMode(cameraId,false); isFlashlightOn=false; } catch (Exception ignored){}
        }
    }

    @Override protected void onDestroy(){
        stopRamRefresher();
        stopFirebaseRamListener();
        executorService.shutdownNow();
        super.onDestroy();
    }

// ==========================================
// INICJALIZACJA I USŁUGI
// ==========================================

    private void ensureNotificationPermissionAndStartService(){
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
            return;
        }
        startMonitoringService();
    }
    private void startMonitoringService(){
        Intent i = new Intent(this, SensorMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private void initFirebase(){
        try {
            FirebaseApp.initializeApp(this);
            firebaseDatabase = FirebaseDatabase.getInstance(FIREBASE_URL);
            firebaseRootRef = firebaseDatabase.getReference("statystyki_urzadzen");
            deviceId = Build.MODEL.replace(" ", "_") + "_" + (System.currentTimeMillis() % 10000);
            firebaseDeviceRef = firebaseRootRef.child(deviceId);
            firebasePolaczony = true;
            pushCurrentSampleNow();
        } catch (Exception e){
            firebasePolaczony = false;
            Toast.makeText(this, "Błąd Firebase: "+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void pushCurrentSampleNow(){
        if (!firebasePolaczony || firebaseDeviceRef==null) return;
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memInfo);
        double totalRamMB = memInfo.totalMem / (1024.0 * 1024.0);
        double freeRamMB = memInfo.availMem / (1024.0 * 1024.0);
        double usedRamMB = totalRamMB - freeRamMB;  // Użycie RAM
        float batteryPct = 0;
        float batteryTemp = 0;
        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat != null) {
            int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int tempInt = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            if (level != -1 && scale != -1) batteryPct = (level / (float)scale) * 100;
            batteryTemp = tempInt / 10.0f;
        }
        float cpuFreq = getCpuFreqFloat();
        RamSample sample = new RamSample(System.currentTimeMillis(), zaokraglij(usedRamMB, 0), (int)batteryPct, zaokraglij(batteryTemp, 1), zaokraglij(cpuFreq, 2));
        firebaseDeviceRef.push().setValue(sample);
    }

// ==========================================
// SETUP UI I WYKRESÓW
// ==========================================

    private void setupUI(){
        if (getSupportActionBar()!=null) getSupportActionBar().hide();
        opisParametrow = findViewById(R.id.opis_parametrow);
        layoutWykresy = findViewById(R.id.layout_wykresy);
        layoutAplikacjaWykres = findViewById(R.id.layout_aplikacja_wykres);
        chartRam = findViewById(R.id.chart_ram);
        chartBattery = findViewById(R.id.chart_battery);
        chartTemp = findViewById(R.id.chart_temp);
        chartLight = findViewById(R.id.chart_light);
        chartCpu = findViewById(R.id.chart_cpu);

        chartAppRam = findViewById(R.id.chart_app_ram);
        chartAppBattery = findViewById(R.id.chart_app_battery);
        chartAppTemp = findViewById(R.id.chart_app_temp);
        chartAppCpu = findViewById(R.id.chart_app_cpu);

        spinnerDevices = findViewById(R.id.spinner_devices);
        etLimit = findViewById(R.id.et_limit);
        btnLoad = findViewById(R.id.btn_load);

        setupSingleChart(chartRam, Color.GREEN, 0, 0, " MB");
        setupSingleChart(chartBattery, Color.YELLOW, 0, 100, " %");
        setupSingleChart(chartTemp, Color.RED, 15, 50, " °C");
        setupSingleChart(chartLight, Color.CYAN, 0, 1000, " lx");
        setupSingleChart(chartCpu, Color.MAGENTA, 0, 3.5f, " GHz");

        // aplikacja - osobne wykresy (te same konfiguracje)
        setupSingleChart(chartAppRam, Color.GREEN, 0, 0, " MB");
        setupSingleChart(chartAppBattery, Color.YELLOW, 0, 100, " %");
        setupSingleChart(chartAppTemp, Color.RED, 15, 50, " °C");
        setupSingleChart(chartAppCpu, Color.MAGENTA, 0, 3.5f, " GHz");

        loadDeviceListIntoSpinner();

        btnLoad.setOnClickListener(v -> {
            wibruj(20);
            Object sel = spinnerDevices.getSelectedItem();
            String selectedDevice = sel == null ? "" : sel.toString().trim();
            int limit = 20;
            try { String s = etLimit.getText().toString().trim(); if (!s.isEmpty()) limit = Integer.parseInt(s); } catch (Exception ignored){}
            if (selectedDevice.isEmpty()) {
                Toast.makeText(this, "Wybierz urządzenie z listy.", Toast.LENGTH_SHORT).show();
                return;
            }
            String path = "statystyki_urzadzen/" + selectedDevice;
            loadAndPlotIntoFourCharts(path, limit);
        });

        opisParametrow.setOnClickListener(v -> {
            if (aktualnieWybranyEkran == EKRAN_GPS) {
                if (aktualnaSzerokosc != 0.0 && aktualnaDlugosc != 0.0) { wibruj(50); otworzMapyGoogle(); }
                else Toast.makeText(this,"Brak współrzędnych GPS.",Toast.LENGTH_SHORT).show();
            }
        });
        opisParametrow.setOnLongClickListener(v -> { wibruj(100); kopiujDoSchowka(opisParametrow.getText().toString()); return true; });
    }

// ==========================================
// KONFIGURACJA WYKRESÓW
// ==========================================

    private void setupSingleChart(LineChart chart, int color, float min, float max, String unit){
        if (chart == null) return;
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);
        chart.setBackgroundColor(Color.parseColor("#222222"));
        chart.setMinOffset(12f);
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        chart.setData(data);

        XAxis x = chart.getXAxis();
        x.setEnabled(true);
        x.setTextColor(Color.LTGRAY);
        x.setTextSize(9f);
        x.setDrawGridLines(true);
        x.setGridColor(Color.DKGRAY);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setValueFormatter(new ValueFormatter() { @Override public String getFormattedValue(float value) { return String.format(Locale.US, "%.0fs", value); } });

        YAxis left = chart.getAxisLeft();
        left.setTextColor(color);
        left.setTextSize(10f);
        if (max > 0) { left.setAxisMinimum(min); left.setAxisMaximum(max); }
        left.setDrawGridLines(true);
        left.setGridColor(Color.DKGRAY);
        left.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                if (unit.contains("GHz")) return String.format(Locale.US, "%.2f%s", value, unit);
                else if (unit.contains("°C")) return String.format(Locale.US, "%.1f%s", value, unit);
                else if (unit.contains("MB")) return String.format(Locale.US, "%.0f%s", value, unit);
                else if (unit.contains("%")) return String.format(Locale.US, "%.0f%s", value, unit);
                else if (unit.contains("lx")) return String.format(Locale.US, "%.0f%s", value, unit);
                return String.format(Locale.US, "%.0f", value);
            }
        });
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
    }

// ==========================================
// OBSŁUGA PRZYCISKÓW
// ==========================================

    private void setupButtons(){
        View.OnClickListener listener = v -> {
            wibruj(15);
            stopRamRefresher();
            zatrzymajNasluchiwanieGPS();
            stopFirebaseRamListener();

            layoutWykresy.setVisibility(View.GONE);
            layoutAplikacjaWykres.setVisibility(View.GONE);

            int id = v.getId();
            if (id == R.id.btn_ogolne) {
                aktualnieWybranyEkran = EKRAN_OGOLNE;
                wyswietlInformacjeOgolne();
                startRamRefresher();
            } else if (id == R.id.btn_gps) {
                aktualnieWybranyEkran = EKRAN_GPS;
                wyswietlInformacjeGPS();
                sprawdzIpoprosOPermISjeGPS();
            } else if (id == R.id.btn_zyroskop) {
                aktualnieWybranyEkran = EKRAN_ZYROSKOP;
                wyswietlZyroskop();
            } else if (id == R.id.btn_system) {
                aktualnieWybranyEkran = EKRAN_SYSTEM;
                layoutWykresy.setVisibility(View.VISIBLE);
                wyswietlInformacjeSystemowe();
                startRamRefresher();
            } else if (id == R.id.btn_aplikacja) {
                aktualnieWybranyEkran = EKRAN_APLIKACJA;
                opisParametrow.setText(daneAplikacjiTekst());
                layoutAplikacjaWykres.setVisibility(View.VISIBLE);
            }
        };
        int[] buttons = {R.id.btn_ogolne, R.id.btn_gps, R.id.btn_zyroskop, R.id.btn_system, R.id.btn_aplikacja};
        for (int id : buttons) { View btn = findViewById(id); if (btn != null) btn.setOnClickListener(listener); }
    }

// ==========================================
// MONITORING RAM I DANYCH
// ==========================================

    private void setupRamRefresher(){
        ramRunnable = new Runnable() {
            @Override public void run() {
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memInfo);
                double freeRamMB = memInfo.availMem / (1024.0 * 1024.0);
                double totalRamMB = memInfo.totalMem / (1024.0 * 1024.0);
                double usedRamMB = totalRamMB - freeRamMB;  // Użycie RAM

                float batteryPct = 0;
                float batteryTemp = 0;
                Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (bat != null) {
                    int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int tempInt = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                    if (level != -1 && scale != -1) batteryPct = (level / (float)scale) * 100;
                    batteryTemp = tempInt / 10.0f;
                }
                float cpuFreq = getCpuFreqFloat();

                if (aktualnieWybranyEkran == EKRAN_SYSTEM && layoutWykresy.getVisibility() == View.VISIBLE) {
                    updateSingleChart(chartRam, clamp((float)usedRamMB, 0f, 65536f), Color.GREEN);
                    updateSingleChart(chartBattery, clamp(batteryPct, 0f, 100f), Color.YELLOW);
                    updateSingleChart(chartTemp, clamp(batteryTemp, -10f, 80f), Color.RED);

                    float light = clamp(aktualneSwiatloLx, 0f, 100_000f);
                    YAxis lightAxis = chartLight.getAxisLeft();
                    if (lightAxis != null) {
                        float curMax = lightAxis.getAxisMaximum();
                        if (light > curMax * 0.95f) { float newMax = Math.min(light * 1.2f, 50000f); lightAxis.setAxisMaximum(newMax); }
                    }
                    updateSingleChart(chartLight, light, Color.CYAN);

                    updateSingleChart(chartCpu, clamp(cpuFreq, 0f, 3.5f), Color.MAGENTA);
                }

                sumRam += usedRamMB; sumTemp += batteryTemp; sumCpu += cpuFreq; countSamples++;  // Zmienione na usedRamMB

                long now = System.currentTimeMillis();
                if (firebasePolaczony && firebaseDeviceRef!=null && (now - lastPublishTs) >= RAM_PUBLISH_INTERVAL_MS) {
                    lastPublishTs = now;
                    double avgUsedRam = countSamples>0 ? sumRam/countSamples : usedRamMB;  // Średnie użycie
                    double avgTemp = countSamples>0 ? sumTemp/countSamples : batteryTemp;
                    double avgCpu  = countSamples>0 ? sumCpu/countSamples  : cpuFreq;
                    sumRam = sumTemp = sumCpu = 0; countSamples = 0;
                    RamSample sample = new RamSample(now, zaokraglij(avgUsedRam, 0), (int)batteryPct, zaokraglij(avgTemp, 1), zaokraglij(avgCpu, 2));
                    firebaseDeviceRef.push().setValue(sample)
                            .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Błąd zapisu: "+e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                if (aktualnieWybranyEkran==EKRAN_OGOLNE) wyswietlInformacjeOgolne();
                else if (aktualnieWybranyEkran==EKRAN_SYSTEM) wyswietlInformacjeSystemowe();

                ramHandler.postDelayed(this, 1000);
            }
        };
    }

// ==========================================
// POMOCNICZE FUNKCJE
// ==========================================

    private float clamp(float v, float min, float max){ return Math.max(min, Math.min(max, v)); }

    private float getCpuFreqFloat(){
        try (RandomAccessFile reader = new RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq","r")){
            String line = reader.readLine();
            return line==null?0:Float.parseFloat(line)/1_000_000.0f;
        } catch (Exception e){ return 0; }
    }

    private double zaokraglij(double v, int places){
        long factor = (long)Math.pow(10, places);
        long tmp = Math.round(v*factor);
        return tmp/(double)factor;
    }

    private void updateSingleChart(LineChart chart, float val, int color){
        if (chart == null) return;
        if (chart.getData() == null) chart.setData(new LineData());
        LineData data = chart.getData();
        LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
        if (set == null) {
            set = new LineDataSet(null, "Dane");
            set.setColor(color);
            set.setLineWidth(2f);
            set.setDrawCircles(false);
            set.setDrawValues(false);
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setDrawFilled(true);
            set.setFillColor(color);
            set.setFillAlpha(50);
            data.addDataSet(set);
        }
        float xVal = set.getEntryCount();
        data.addEntry(new Entry(xVal, val), 0);
        data.notifyDataChanged();

        if (set.getEntryCount() > MAX_LOCAL_POINTS) {
            set.removeFirst();
            for (int i=0;i<set.getEntryCount();i++){
                Entry e = set.getEntryForIndex(i);
                if (e!=null) e.setX(i);
            }
        }
        chart.notifyDataSetChanged();
        chart.moveViewToX(data.getEntryCount());
    }

// ==========================================
// ŁADOWANIE DANYCH Z FIREBASE
// ==========================================

    private void loadDeviceListIntoSpinner() {
        if (firebaseRootRef == null) return;
        firebaseRootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> deviceIds = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String key = child.getKey();
                    if (key != null && !key.isEmpty()) deviceIds.add(key);
                }
                if (deviceIds.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Brak urządzeń w bazie.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,
                        android.R.layout.simple_spinner_item, deviceIds);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerDevices.setAdapter(adapter);
                int idx = deviceIds.indexOf(deviceId);
                if (idx >= 0) spinnerDevices.setSelection(idx);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Błąd listy urządzeń: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAndPlotIntoFourCharts(String path, int limit) {
        try {
            DatabaseReference ref = firebaseDatabase.getReference(path);

            clearChart(chartAppRam); clearChart(chartAppBattery); clearChart(chartAppTemp); clearChart(chartAppCpu);

            ref.orderByChild("czas").limitToLast(limit)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            LineData dataRam = chartAppRam.getData(); if (dataRam==null) { dataRam=new LineData(); chartAppRam.setData(dataRam); }
                            LineData dataBat = chartAppBattery.getData(); if (dataBat==null) { dataBat=new LineData(); chartAppBattery.setData(dataBat); }
                            LineData dataTemp= chartAppTemp.getData(); if (dataTemp==null){ dataTemp=new LineData(); chartAppTemp.setData(dataTemp); }
                            LineData dataCpu = chartAppCpu.getData(); if (dataCpu==null) { dataCpu=new LineData(); chartAppCpu.setData(dataCpu); }

                            LineDataSet ramSet = (LineDataSet) dataRam.getDataSetByLabel("RAM użycie (MB)", true);
                            LineDataSet batSet = (LineDataSet) dataBat.getDataSetByLabel("Bateria (%)", true);
                            LineDataSet tempSet= (LineDataSet) dataTemp.getDataSetByLabel("Temperatura (°C)", true);
                            LineDataSet cpuSet = (LineDataSet) dataCpu.getDataSetByLabel("CPU (GHz)", true);

                            if (ramSet==null) { ramSet = baseSet(Color.GREEN, "RAM użycie (MB)"); dataRam.addDataSet(ramSet); }
                            if (batSet==null) { batSet = baseSet(Color.YELLOW, "Bateria (%)"); dataBat.addDataSet(batSet); }
                            if (tempSet==null){ tempSet= baseSet(Color.RED, "Temperatura (°C)"); dataTemp.addDataSet(tempSet); }
                            if (cpuSet==null) { cpuSet = baseSet(Color.MAGENTA, "CPU (GHz)"); dataCpu.addDataSet(cpuSet); }

                            int idx = 0;
                            for (DataSnapshot child : snapshot.getChildren()) {
                                RamSample s = child.getValue(RamSample.class);
                                if (s == null) continue;
                                dataRam.addEntry(new Entry(idx, clamp((float) s.ram_uzycie, 0f, 65536f)), dataRam.getIndexOfDataSet(ramSet));  // Użycie RAM
                                dataBat.addEntry(new Entry(idx, clamp((float) s.bateria_poziom, 0f, 100f)), dataBat.getIndexOfDataSet(batSet));
                                dataTemp.addEntry(new Entry(idx, clamp((float) s.bateria_temp, -10f, 80f)), dataTemp.getIndexOfDataSet(tempSet));
                                dataCpu.addEntry(new Entry(idx, clamp((float) s.cpu_freq, 0f, 3.5f)), dataCpu.getIndexOfDataSet(cpuSet));
                                idx++;
                            }

                            dataRam.notifyDataChanged(); chartAppRam.notifyDataSetChanged(); chartAppRam.moveViewToX(dataRam.getEntryCount());
                            dataBat.notifyDataChanged(); chartAppBattery.notifyDataSetChanged(); chartAppBattery.moveViewToX(dataBat.getEntryCount());
                            dataTemp.notifyDataChanged(); chartAppTemp.notifyDataSetChanged(); chartAppTemp.moveViewToX(dataTemp.getEntryCount());
                            dataCpu.notifyDataChanged(); chartAppCpu.notifyDataSetChanged(); chartAppCpu.moveViewToX(dataCpu.getEntryCount());

                            Toast.makeText(MainActivity.this, "Załadowano " + (snapshot.getChildrenCount()) + " próbek z " + path, Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(MainActivity.this, "Błąd odczytu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });

        } catch (Exception e) {
            Toast.makeText(this, "Błąd: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private LineDataSet baseSet(int color, String label) {
        LineDataSet set = new LineDataSet(null, label);
        set.setColor(color);
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setDrawFilled(true);
        set.setFillColor(color);
        set.setFillAlpha(50);
        return set;
    }

    private void clearChart(LineChart chart) {
        if (chart == null) return;
        LineData d = chart.getData();
        if (d == null) { chart.setData(new LineData()); return; }
        d.clearValues();
        chart.invalidate();
    }

// ==========================================
// SETUP SENSORS I LOKALIZACJI
// ==========================================

    private void setupSensors(){
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private void setupFlashlight(){
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null) {
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics ch = cameraManager.getCameraCharacteristics(id);
                    Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) { cameraId = id; break; }
                }
            }
        } catch (CameraAccessException ignored){}
    }

    private void setupLocation(){
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        geocoder = new Geocoder(this, Locale.getDefault());
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {
                aktualnaSzerokosc = location.getLatitude();
                aktualnaDlugosc = location.getLongitude();
                aktualnaDokladnosc = location.getAccuracy();
                if (aktualnieWybranyEkran == EKRAN_GPS) {
                    wyswietlInformacjeGPS();
                    pobierzDanePogodowe(aktualnaSzerokosc, aktualnaDlugosc);
                }
            }
            @Override public void onStatusChanged(String provider,int status,Bundle extras){}
            @Override public void onProviderEnabled(@NonNull String provider){}
            @Override public void onProviderDisabled(@NonNull String provider){
                aktualnaSzerokosc=0.0; aktualnaDlugosc=0.0;
                if (aktualnieWybranyEkran==EKRAN_GPS) wyswietlInformacjeGPS();
            }
        };
    }

// ==========================================
// TEKSTOWE FUNKCJE WYŚWIETLANIA
// ==========================================

    private String daneAplikacjiTekst(){
        return "DANE APLIKACJI\n=====================================\n\n" +
                "• Wersja: 2.0 (Kompletna)\n" +
                "• Status: Aktywna\n" +
                "• Ostatnia aktualizacja: 11.12.2025\n\n" +
                "UPRAWNIENIA\n=====================================\n" +
                " • Lokalizacja (GPS/Sieć)\n" +
                " • Internet (Pogoda/Mapy)\n" +
                " • Stan telefonu (Bateria)\n" +
                " • Aparat (Latarka)\n\n" +
                "WYKORZYSTYWANE SENSORY\n=====================================\n" +
                " • Żyroskop\n" +
                " • Akcelerometr\n" +
                " • Czujnik światła\n\n" +
                "WCZYTYWANIE BAZY FIREBASE\n=====================================\n";
    }

    private void wyswietlInformacjeOgolne(){
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        double total=mi.totalMem/(1024.0*1024.0*1024.0);
        double free=mi.availMem/(1024.0*1024.0*1024.0);

        Intent bs=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        String pct="---", temp="---", volt="---";
        if(bs!=null) {
            int level=bs.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
            int scale=bs.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
            if(level!=-1 && scale!=-1) pct=(int)((level/(float)scale)*100)+"%";
            temp=String.format(Locale.US,"%.1f°C", bs.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10.0f);
            volt=bs.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0)+" mV";
        }

        String dysk=getPojemnoscDysku();
        String txt=String.format(Locale.getDefault(),
                "INFORMACJE O URZĄDZENIU\n-----------------------------------\n• Model: %s\n• Producent: %s\n\nPAMIĘĆ I DYSK\n-----------------------------------\n• RAM Wolne: %.2f GB\n• RAM Całkowite: %.2f GB\n• DYSK %s\n\nBATERIA\n-----------------------------------\n• Poziom: %s\n• Temperatura: %s\n• Napięcie: %s\n\n(Przytrzymaj tekst, aby skopiować)",
                Build.MODEL, Build.MANUFACTURER, free, total, dysk, pct, temp, volt);
        opisParametrow.setText(txt);
    }

    @SuppressLint("MissingPermission")
    private void wyswietlInformacjeGPS(){
        StringBuilder sb=new StringBuilder("PARAMETRY GPS\n-----------------------------------\n\n");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            sb.append("🔴 Brak uprawnień GPS.");
        } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            sb.append("🔴 GPS wyłączony.");
        } else if (aktualnaSzerokosc==0.0) {
            sb.append("🟡 Szukanie satelitów...");
        } else {
            sb.append("• Adres: ").append(pobierzAdres(aktualnaSzerokosc,aktualnaDlugosc)).append("\n");
            sb.append(String.format(Locale.US,"• Szerokość: %.6f\n",aktualnaSzerokosc));
            sb.append(String.format(Locale.US,"• Długość: %.6f\n",aktualnaDlugosc));
            sb.append(String.format(Locale.US,"• Dokładność: %.1f m\n",aktualnaDokladnosc));
            sb.append("\n🗺️ Kliknij by otworzyć mapę");
        }
        opisParametrow.setText(sb.toString());
    }

    private void wyswietlInformacjeSystemowe(){
        int widthPx,heightPx,densityDpi;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowManager wm = getSystemService(WindowManager.class);
            WindowMetrics metrics = wm.getCurrentWindowMetrics();
            Rect b = metrics.getBounds(); widthPx=b.width(); heightPx=b.height();
            densityDpi = getResources().getDisplayMetrics().densityDpi;
        } else {
            DisplayMetrics m=new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(m);
            widthPx=m.widthPixels; heightPx=m.heightPixels; densityDpi=m.densityDpi;
        }
        int cores=Runtime.getRuntime().availableProcessors();
        String abi=(Build.SUPPORTED_ABIS!=null && Build.SUPPORTED_ABIS.length>0)? Build.SUPPORTED_ABIS[0]:"N/D";
        String torch=isFlashlightOn? "Włączona 💡":"Wyłączona";
        float cpuVal = getCpuFreqFloat();

        String s=String.format(Locale.US,
                "PROCESOR\n-----------------------------------\n• Rdzenie: %d\n• Taktowanie: %.2f GHz\n• Architektura: %s\n\nINFORMACJE SYSTEMOWE\n-----------------------------------\n• Światło: %.1f lx\n• Android: %s (API %d)\n• Latarka: %s\n\nEKRAN\n-----------------------------------\n• Rozdzielczość: %dx%d px\n• Gęstość: %d dpi\n\n(Poniżej wykres użycia pamięci RAM na żywo)",
                cores,cpuVal,abi,aktualneSwiatloLx,Build.VERSION.RELEASE,Build.VERSION.SDK_INT,torch,widthPx,heightPx,densityDpi);
        opisParametrow.setText(s);
    }

    private void wyswietlZyroskop(){
        String s=String.format(Locale.US,
                "ŻYROSKOP (rad/s)\n-----------------------------------\nX: %.2f\nY: %.2f\nZ: %.2f\n\nAKCELEROMETR (m/s²)\n-----------------------------------\nX: %.2f\nY: %.2f\nZ: %.2f\n\n(Przytrzymaj tekst, aby skopiować)",
                aktualnyZyroskop[0],aktualnyZyroskop[1],aktualnyZyroskop[2],
                aktualnyAkcelerometr[0],aktualnyAkcelerometr[1],aktualnyAkcelerometr[2]);
        opisParametrow.setText(s);
    }

// ==========================================
// POMOCNICZE FUNKCJE SYSTEMOWE
// ==========================================

    private String getPojemnoscDysku(){
        try {
            File path= Environment.getDataDirectory();
            StatFs stat=new StatFs(path.getPath());
            long blk=stat.getBlockSizeLong();
            long t=stat.getBlockCountLong();
            long a=stat.getAvailableBlocksLong();
            double total=(t*blk)/(1024.0*1024.0*1024.0);
            double free=(a*blk)/(1024.0*1024.0*1024.0);
            return String.format(Locale.US,"Wolne: %.2f GB\n• Całkowite: %.2f GB",free,total);
        } catch (Exception e){ return "Błąd odczytu"; }
    }
    private String pobierzAdres(double lat,double lon){
        if(!Geocoder.isPresent()) return "Geokodowanie niedostępne";
        try {
            List<Address> list=geocoder.getFromLocation(lat,lon,1);
            if(list!=null && !list.isEmpty()) {
                Address a=list.get(0);
                String loc=a.getLocality()!=null? a.getLocality():"";
                String th=a.getThoroughfare()!=null? a.getThoroughfare():"";
                return (loc+" "+th).trim();
            }
        } catch (Exception ignored){}
        return "Adres nieznany";
    }
    private void sprawdzIpoprosOPermISjeGPS(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            uruchomNasluchiwanieGPS();
        } else {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_CODE_GPS_PERMISSION);
        }
    }
    @SuppressLint("MissingPermission")
    private void uruchomNasluchiwanieGPS(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,5,locationListener);
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,2000,5,locationListener);
        } catch (Exception ignored){}
    }
    private void zatrzymajNasluchiwanieGPS(){
        if(locationManager!=null && locationListener!=null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored){}
        }
    }
    private void pobierzDanePogodowe(double lat,double lon){
        executorService.execute(() -> {
            String resp="";
            try {
                String urlString=String.format(Locale.US,WEATHER_URL,lat,lon,API_KEY);
                HttpURLConnection c=(HttpURLConnection)new URL(urlString).openConnection();
                c.setRequestMethod("GET"); c.setConnectTimeout(5000);
                if (c.getResponseCode()==HttpURLConnection.HTTP_OK) {
                    BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb=new StringBuilder(); String line;
                    while((line=in.readLine())!=null) sb.append(line);
                    in.close(); resp=sb.toString();
                }
                c.disconnect();
            } catch (Exception ignored){}
            String finalResp=resp;
            mainHandler.post(() -> { if(aktualnieWybranyEkran==EKRAN_GPS && !finalResp.isEmpty()) sformatujIWyswietlPogode(finalResp); });
        });
    }
    private void sformatujIWyswietlPogode(String json){
        try {
            JSONObject j=new JSONObject(json);
            String miasto=j.optString("name","Nieznane");
            JSONObject main=j.getJSONObject("main");
            JSONObject w=j.getJSONArray("weather").getJSONObject(0);
            JSONObject wind=j.getJSONObject("wind");
            String opis=w.optString("description","");
            if(!opis.isEmpty()) opis=opis.substring(0,1).toUpperCase()+opis.substring(1);
            String blok=String.format(Locale.getDefault(),
                    "\n\n───── POGODA: %s ────\n🌡️ Temp: %.1f°C (Odczuwalna: %.1f°C)\n☁️ Niebo: %s\n------------------------------------\n💧 Wilgotność: %d%%\n⏱️ Ciśnienie: %d hPa\n💨 Wiatr: %.1f m/s",
                    miasto.toUpperCase(), main.getDouble("temp"), main.getDouble("feels_like"),
                    opis, main.getInt("humidity"), main.getInt("pressure"), wind.getDouble("speed"));
            String base=opisParametrow.getText().toString();
            String sep="───── POGODA:";
            if(base.contains(sep)) {
                int idx=base.indexOf("\n\n"+sep);
                if(idx>=0) base=base.substring(0,idx);
            }
            opisParametrow.setText(base+blok);
        } catch (Exception ignored){}
    }
    private void startRamRefresher(){ ramHandler.removeCallbacks(ramRunnable); ramHandler.post(ramRunnable); }
    private void stopRamRefresher(){ ramHandler.removeCallbacks(ramRunnable); }
    private void otworzMapyGoogle(){
        try {
            String uri = String.format(Locale.US,"geo:%f,%f?q=%f,%f(Tu jesteś)",aktualnaSzerokosc,aktualnaDlugosc,aktualnaSzerokosc,aktualnaDlugosc);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (Exception e) { Toast.makeText(this,"Nie znaleziono aplikacji map.",Toast.LENGTH_LONG).show(); }
    }
    private void przelaczLatarke(){
        Toast.makeText(this,"Latarka pominięta w tej wersji",Toast.LENGTH_SHORT).show();
    }
    @SuppressLint("MissingPermission")
    private void wibruj(int ms){
        Vibrator v=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        if (v!=null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        }
    }
    private void kopiujDoSchowka(String txt){
        ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb!=null) { cb.setPrimaryClip(ClipData.newPlainText("Dane",txt)); Toast.makeText(this,"Skopiowano",Toast.LENGTH_SHORT).show(); }
    }

// ==========================================
// OBSŁUGA SENSORÓW
// ==========================================

    @Override public void onSensorChanged(SensorEvent event){
        int type = event.sensor.getType();
        if (type==Sensor.TYPE_LIGHT) {
            aktualneSwiatloLx = event.values != null && event.values.length>0 ? event.values[0] : 0f;
            if (aktualnieWybranyEkran==EKRAN_SYSTEM) wyswietlInformacjeSystemowe();
        } else if (type==Sensor.TYPE_GYROSCOPE) {
            aktualnyZyroskop[0]=event.values[0]; aktualnyZyroskop[1]=event.values[1]; aktualnyZyroskop[2]=event.values[2];
            if (aktualnieWybranyEkran==EKRAN_ZYROSKOP) wyswietlZyroskop();
        } else if (type==Sensor.TYPE_ACCELEROMETER) {
            aktualnyAkcelerometr[0]=event.values[0]; aktualnyAkcelerometr[1]=event.values[1]; aktualnyAkcelerometr[2]=event.values[2];
            if (aktualnieWybranyEkran==EKRAN_ZYROSKOP) wyswietlZyroskop();
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy){}

// ==========================================
// LISTENERY FIREBASE
// ==========================================

    private void startFirebaseRamListener() {
        if (!firebasePolaczony || firebaseDeviceRef == null || ramFirebaseListener != null) return;
        ramFirebaseListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) { }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        firebaseDeviceRef.addValueEventListener(ramFirebaseListener);
    }
    private void stopFirebaseRamListener() {
        if (firebaseDeviceRef != null && ramFirebaseListener != null) {
            firebaseDeviceRef.removeEventListener(ramFirebaseListener);
            ramFirebaseListener = null;
        }
    }

// ==========================================
// KLASA USŁUGI MONITORINGU
// ==========================================

    public static class SensorMonitorService extends Service {
        private static final String CHANNEL_ID = "mobile_sensor_monitor_channel";
        private static final int NOTIFICATION_ID = 1001;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private Runnable loop;

        private boolean firebaseReady = false;
        private DatabaseReference firebaseDeviceRef;
        private String deviceId;

        private static final long PUBLISH_INTERVAL_MS = 60_000;
        private long lastPublishTs = 0;
        private double sumRam = 0, sumTemp = 0, sumCpu = 0;
        private int countSamples = 0;

        @Override public void onCreate(){
            super.onCreate();
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, buildNotification("Monitoring w toku..."));
            initFirebase();
            setupLoop();
            handler.post(loop);
            pushNow();
        }

        private void initFirebase(){
            try {
                FirebaseApp.initializeApp(this);
                FirebaseDatabase db = FirebaseDatabase.getInstance(FIREBASE_URL);
                deviceId = Build.MODEL.replace(" ", "_") + "_" + (System.currentTimeMillis() % 10000);
                firebaseDeviceRef = db.getReference("statystyki_urzadzen").child(deviceId);
                firebaseReady = true;
            } catch (Exception e){ firebaseReady = false; }
        }

        private void setupLoop(){
            loop = new Runnable() {
                @Override public void run() {
                    try {
                        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                        ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memInfo);
                        double freeRamMB  = memInfo.availMem / (1024.0 * 1024.0);
                        double totalRamMB = memInfo.totalMem / (1024.0 * 1024.0);
                        double usedRamMB  = totalRamMB - freeRamMB;  // Użycie RAM

                        float batteryPct = 0;
                        float batteryTemp = 0;
                        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                        if (bat != null) {
                            int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                            int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                            int tempInt = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                            if (level != -1 && scale != -1) batteryPct = (level / (float)scale) * 100;
                            batteryTemp = tempInt / 10.0f;
                        }
                        float cpuFreq = getCpuFreqFloat();

                        sumRam += usedRamMB; sumTemp += batteryTemp; sumCpu += cpuFreq; countSamples++;  // Użycie RAM

                        long now = System.currentTimeMillis();
                        if (firebaseReady && firebaseDeviceRef!=null && (now - lastPublishTs) >= PUBLISH_INTERVAL_MS) {
                            lastPublishTs = now;
                            double avgUsedRam = countSamples>0? sumRam/countSamples : usedRamMB;  // Średnie użycie
                            double avgTemp    = countSamples>0? sumTemp/countSamples : batteryTemp;
                            double avgCpu     = countSamples>0? sumCpu/countSamples  : cpuFreq;
                            sumRam=sumTemp=sumCpu=0; countSamples=0;

                            RamSample sample = new RamSample(now, round(avgUsedRam,0), (int)batteryPct, round(avgTemp,1), round(avgCpu,2));
                            firebaseDeviceRef.push().setValue(sample);
                        }

                        updateNotification(String.format(Locale.US,"RAM: %.0fMB • CPU: %.2fGHz • Bat: %.0f%% • Temp: %.1f°C",
                                usedRamMB, cpuFreq, batteryPct, batteryTemp));  // Użycie RAM w notyfikacji
                    } catch (Exception ignored) {
                    } finally {
                        handler.postDelayed(this, 1000);
                    }
                }
            };
        }

        private void pushNow(){
            if (!firebaseReady || firebaseDeviceRef==null) return;
            try {
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memInfo);
                double totalRamMB = memInfo.totalMem / (1024.0 * 1024.0);
                double freeRamMB = memInfo.availMem / (1024.0 * 1024.0);
                double usedRamMB = totalRamMB - freeRamMB;  // Użycie RAM
                float batteryPct = 0;
                float batteryTemp = 0;
                Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (bat != null) {
                    int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int tempInt = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                    if (level != -1 && scale != -1) batteryPct = (level / (float)scale) * 100;
                    batteryTemp = tempInt / 10.0f;
                }
                float cpuFreq = getCpuFreqFloat();
                RamSample sample = new RamSample(System.currentTimeMillis(), round(usedRamMB,0), (int)batteryPct, round(batteryTemp,1), round(cpuFreq,2));
                firebaseDeviceRef.push().setValue(sample);
            } catch (Exception ignored){}
        }

        private Notification buildNotification(String content){
            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("MobileSensor — Monitor")
                    .setContentText(content)
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    .build();
        }
        private void updateNotification(String content){
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm!=null) nm.notify(NOTIFICATION_ID, buildNotification(content));
        }
        private void createNotificationChannel(){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "MobileSensor Monitor", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Stały monitoring RAM/CPU/Bateria");
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm!=null) nm.createNotificationChannel(ch);
            }
        }
        private float getCpuFreqFloat(){
            try (RandomAccessFile reader = new RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq","r")){
                String line=reader.readLine();
                return line==null?0:Float.parseFloat(line)/1_000_000.0f;
            } catch (Exception e){ return 0; }
        }
        private double round(double v,int p){ long f=(long)Math.pow(10,p); return Math.round(v*f)/(double)f; }

        @Nullable @Override public IBinder onBind(Intent intent){ return null; }
        @Override public int onStartCommand(Intent intent, int flags, int startId){ return START_STICKY; }
        @Override public void onDestroy(){ handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    }
}