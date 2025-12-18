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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

/**
 * @brief Główna aktywność aplikacji MobileSensor.
 *
 * Klasa MainActivity zarządza interfejsem użytkownika i funkcjonalnościami aplikacji,
 * takimi jak monitorowanie czujników, GPS, baterii, RAM i CPU. Obsługuje także
 * połączenie z Firebase dla przechowywania danych oraz wyświetlanie wykresów.
 *
 * Funkcje obejmują:
 * - Wyświetlanie informacji o urządzeniu.
 * - Monitorowanie czujników (żyroskop, akcelerometr, światło).
 * - Pobieranie danych pogodowych na podstawie GPS.
 * - Ładowanie i wyświetlanie danych historycznych z Firebase.
 * - Zarządzanie usługami w tle (SensorMonitorService).
 *
 * @author Janq20
 * @version 2.0
 * @date 2025-12-18
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // ==========================================
    // STAŁE I ZMIENNE GLOBALNE
    // ==========================================

    private static final String API_KEY = "73388daab4f30826e3f8cca01c2ddb04";  ///< Klucz API dla OpenWeatherMap.
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=pl";  ///< URL dla pobierania danych pogodowych.
    public static final String FIREBASE_URL = "https://mobilesensormonitor-default-rtdb.europe-west1.firebasedatabase.app";  ///< URL bazy danych Firebase.

    private static final int REQUEST_CODE_GPS_PERMISSION = 100;  ///< Kod żądania uprawnień GPS.
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 101;  ///< Kod żądania uprawnień aparatu.
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 102;  ///< Kod żądania uprawnień powiadomień.

    private static final int EKRAN_OGOLNE = 1;  ///< Stała dla ekranu ogólnych informacji.
    private static final int EKRAN_GPS = 2;  ///< Stała dla ekranu GPS.
    private static final int EKRAN_ZYROSKOP = 3;  ///< Stała dla ekranu żyroskopu.
    private static final int EKRAN_SYSTEM = 4;  ///< Stała dla ekranu systemowego.
    private static final int EKRAN_APLIKACJA = 5;  ///< Stała dla ekranu aplikacji.

    private static final int MAX_LOCAL_POINTS = 240;  ///< Maksymalna liczba punktów na lokalnych wykresach.
    private static final long RAM_PUBLISH_INTERVAL_MS = 60_000;  ///< Interwał publikowania danych RAM (60 sekund).

    private TextView opisParametrow;  ///< TextView do wyświetlania opisów parametrów.
    private LinearLayout layoutWykresy;  ///< Layout dla wykresów systemowych.
    private LinearLayout layoutAplikacjaWykres;  ///< Layout dla wykresów aplikacji.
    private LineChart chartRam, chartBattery, chartTemp, chartLight, chartCpu;  ///< Wykresy dla danych systemowych.
    private LineChart chartAppRam, chartAppBattery, chartAppTemp, chartAppCpu;  ///< Wykresy dla danych z Firebase.
    private Spinner spinnerDevices;  ///< Spinner do wyboru urządzeń.
    private EditText etLimit;  ///< EditText dla limitu próbek.
    private Button btnLoad;  ///< Przycisk ładowania danych.

    private int aktualnieWybranyEkran = EKRAN_OGOLNE;  ///< Aktualnie wybrany ekran.

    private SensorManager sensorManager;  ///< Menedżer czujników.
    private Sensor lightSensor, gyroscopeSensor, accelerometerSensor;  ///< Czujniki: światło, żyroskop, akcelerometr.
    private float aktualneSwiatloLx = 0.0f;  ///< Aktualna wartość światła w lx.
    private final float[] aktualnyZyroskop = {0,0,0};  ///< Aktualne wartości żyroskopu.
    private final float[] aktualnyAkcelerometr = {0,0,0};  ///< Aktualne wartości akcelerometru.

    private LocationManager locationManager;  ///< Menedżer lokalizacji.
    private LocationListener locationListener;  ///< Listener lokalizacji.
    private Geocoder geocoder;  ///< Geokoder do adresów.
    private double aktualnaSzerokosc = 0.0;  ///< Aktualna szerokość geograficzna.
    private double aktualnaDlugosc = 0.0;  ///< Aktualna długość geograficzna.
    private float aktualnaDokladnosc = 0.0f;  ///< Aktualna dokładność GPS.

    private CameraManager cameraManager;  ///< Menedżer aparatu.
    private String cameraId;  ///< ID aparatu z latarką.
    private boolean isFlashlightOn = false;  ///< Czy latarka jest włączona.

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();  ///< Executor dla zadań w tle.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());  ///< Handler dla głównego wątku.
    private final Handler ramHandler = new Handler(Looper.getMainLooper());  ///< Handler dla odświeżania RAM.
    private Runnable ramRunnable;  ///< Runnable dla odświeżania RAM.

    private long lastPublishTs = 0;  ///< Ostatni czas publikacji danych.
    private double sumRam = 0;  ///< Suma użycia RAM.
    private double sumTemp = 0;  ///< Suma temperatury baterii.
    private double sumCpu = 0;  ///< Suma częstotliwości CPU.
    private int countSamples = 0;  ///< Liczba próbek.

    private boolean firebasePolaczony = false;  ///< Czy Firebase jest połączone.
    private FirebaseDatabase firebaseDatabase;  ///< Instancja Firebase Database.
    private DatabaseReference firebaseRootRef;  ///< Referencja do korzenia Firebase.
    private DatabaseReference firebaseDeviceRef;  ///< Referencja do urządzenia w Firebase.
    private ValueEventListener ramFirebaseListener;  ///< Listener dla danych RAM z Firebase.
    private String deviceId;  ///< ID urządzenia.

    // ==========================================
    // KLASA WEWNĘTRZNA RAMSAMPLE
    // ==========================================

    /**
     * @brief Klasa reprezentująca próbkę danych RAM.
     *
     * Przechowuje informacje o użyciu RAM, poziomie baterii, temperaturze baterii
     * i częstotliwości CPU w danym czasie.
     */
    public static class RamSample {
        public long czas;  ///< Czas próbkowania w milisekundach.
        public double ram_uzycie;  ///< Użycie RAM w MB.
        public int bateria_poziom;  ///< Poziom baterii w procentach.
        public double bateria_temp;  ///< Temperatura baterii w °C.
        public double cpu_freq;  ///< Częstotliwość CPU w GHz.

        /**
         * @brief Konstruktor domyślny.
         */
        public RamSample() {}

        /**
         * @brief Konstruktor z parametrami.
         * @param czas Czas próbkowania.
         * @param ram_uzycie Użycie RAM.
         * @param bateria_poziom Poziom baterii.
         * @param bateria_temp Temperatura baterii.
         * @param cpu_freq Częstotliwość CPU.
         */
        public RamSample(long czas, double ram_uzycie, int bateria_poziom, double bateria_temp, double cpu_freq){
            this.czas=czas; this.ram_uzycie=ram_uzycie; this.bateria_poziom=bateria_poziom; this.bateria_temp=bateria_temp; this.cpu_freq=cpu_freq;
        }
    }

    // ==========================================
    // METODY ŻYCIA AKTYWNOŚCI
    // ==========================================

    /**
     * @brief Metoda wywoływana przy tworzeniu aktywności.
     * @param savedInstanceState Stan zapisany aktywności.
     */
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

    /**
     * @brief Metoda wywoływana przy wznawianiu aktywności.
     */
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

    /**
     * @brief Metoda wywoływana przy zatrzymaniu aktywności.
     */
    @Override protected void onPause(){
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        zatrzymajNasluchiwanieGPS();
        stopRamRefresher();
        if (isFlashlightOn) {
            try { cameraManager.setTorchMode(cameraId,false); isFlashlightOn=false; } catch (Exception ignored){}
        }
    }

    /**
     * @brief Metoda wywoływana przy zniszczeniu aktywności.
     */
    @Override protected void onDestroy(){
        stopRamRefresher();
        stopFirebaseRamListener();
        executorService.shutdownNow();
        super.onDestroy();
    }

    // ==========================================
    // INICJALIZACJA I USŁUGI
    // ==========================================

    /**
     * @brief Zapewnia uprawnienia do powiadomień i uruchamia usługę monitoringu.
     */
    private void ensureNotificationPermissionAndStartService(){
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
            return;
        }
        startMonitoringService();
    }

    /**
     * @brief Uruchamia usługę monitoringu w tle.
     */
    private void startMonitoringService(){
        Intent i = new Intent(this, SensorMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    /**
     * @brief Inicjalizuje połączenie z Firebase.
     */
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

    /**
     * @brief Wysyła bieżącą próbkę danych do Firebase.
     */
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

    /**
     * @brief Konfiguruje interfejs użytkownika.
     */
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
        setupAppChart(chartAppRam, Color.GREEN, 0, 0, " MB");
        setupAppChart(chartAppBattery, Color.YELLOW, 0, 100, " %");
        setupAppChart(chartAppTemp, Color.RED, 15, 50, " °C");
        setupAppChart(chartAppCpu, Color.MAGENTA, 0, 3.5f, " GHz");

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

    /**
     * @brief Konfiguruje pojedynczy wykres dla danych na żywo.
     * @param chart Wykres do skonfigurowania.
     * @param color Kolor linii.
     * @param min Minimalna wartość Y.
     * @param max Maksymalna wartość Y.
     * @param unit Jednostka na osi Y.
     */
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

    /**
     * @brief Konfiguruje wykres dla danych z aplikacji (z Firebase).
     * @param chart Wykres do skonfigurowania.
     * @param color Kolor linii.
     * @param min Minimalna wartość Y.
     * @param max Maksymalna wartość Y.
     * @param unit Jednostka na osi Y.
     */
    private void setupAppChart(LineChart chart, int color, float min, float max, String unit){
        if (chart == null) return;
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);  // Umożliwienie zoom i scroll dla lepszego przeglądania danych czasowych
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);
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
        x.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                long ts = (long) value;
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return sdf.format(new Date(ts));
            }
        });

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

    /**
     * @brief Konfiguruje przyciski nawigacji.
     */
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

    /**
     * @brief Konfiguruje odświeżanie danych RAM.
     */
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

    /**
     * @brief Ogranicza wartość do zakresu.
     * @param v Wartość do ograniczenia.
     * @param min Minimalna wartość.
     * @param max Maksymalna wartość.
     * @return Ograniczona wartość.
     */
    private float clamp(float v, float min, float max){ return Math.max(min, Math.min(max, v)); }

    /**
     * @brief Pobiera aktualną częstotliwość CPU.
     * @return Częstotliwość CPU w GHz.
     */
    private float getCpuFreqFloat(){
        try (RandomAccessFile reader = new RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq","r")){
            String line = reader.readLine();
            return line==null?0:Float.parseFloat(line)/1_000_000.0f;
        } catch (Exception e){ return 0; }
    }

    /**
     * @brief Zaokrągla liczbę do określonej liczby miejsc dziesiętnych.
     * @param v Wartość do zaokrąglenia.
     * @param places Liczba miejsc dziesiętnych.
     * @return Zaokrąglona wartość.
     */
    private double zaokraglij(double v, int places){
        long factor = (long)Math.pow(10, places);
        long tmp = Math.round(v*factor);
        return tmp/(double)factor;
    }

    /**
     * @brief Uruchamia benchmark CPU.
     */
    private void runCpuBenchmark() {
        // Proste obliczenia matematyczne dla testu wydajności CPU
        for (int i = 0; i < 100000; i++) {
            double result = Math.sqrt(i) * Math.sin(i) + Math.cos(i * 2);
            // Bez użycia wyniku, żeby optymalizator nie usunął kodu
        }
    }

    /**
     * @brief Aktualizuje pojedynczy wykres.
     * @param chart Wykres do aktualizacji.
     * @param val Nowa wartość.
     * @param color Kolor linii.
     */
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

    /**
     * @brief Ładuje listę urządzeń do Spinnera.
     */
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

    /**
     * @brief Ładuje i wyświetla dane z Firebase na wykresach.
     * @param path Ścieżka do danych w Firebase.
     * @param limit Limit próbek.
     */
    private void loadAndPlotIntoFourCharts(String path, int limit) {
        try {
            DatabaseReference ref = firebaseDatabase.getReference(path);

            clearChart(chartAppRam); clearChart(chartAppBattery); clearChart(chartAppTemp); clearChart(chartAppCpu);

            ref.orderByChild("czas").limitToLast(limit)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.getChildrenCount() == 0) {
                                Toast.makeText(MainActivity.this, "Brak danych do wyświetlenia.", Toast.LENGTH_SHORT).show();
                                return;
                            }

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

                            for (DataSnapshot child : snapshot.getChildren()) {
                                RamSample s = child.getValue(RamSample.class);
                                if (s == null) continue;
                                dataRam.addEntry(new Entry((float) s.czas, clamp((float) s.ram_uzycie, 0f, 65536f)), dataRam.getIndexOfDataSet(ramSet));  // Użycie RAM, czas jako X
                                dataBat.addEntry(new Entry((float) s.czas, clamp((float) s.bateria_poziom, 0f, 100f)), dataBat.getIndexOfDataSet(batSet));
                                dataTemp.addEntry(new Entry((float) s.czas, clamp((float) s.bateria_temp, -10f, 80f)), dataTemp.getIndexOfDataSet(tempSet));
                                dataCpu.addEntry(new Entry((float) s.czas, clamp((float) s.cpu_freq, 0f, 3.5f)), dataCpu.getIndexOfDataSet(cpuSet));
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

    /**
     * @brief Tworzy bazowy zestaw danych dla wykresu.
     * @param color Kolor linii.
     * @param label Etykieta.
     * @return LineDataSet.
     */
    private LineDataSet baseSet(int color, String label) {
        LineDataSet set = new LineDataSet(null, label);
        set.setColor(color);
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.LINEAR);  // Zmienione na LINEAR dla gładkich linii
        set.setDrawFilled(true);
        set.setFillColor(color);
        set.setFillAlpha(50);
        return set;
    }

    /**
     * @brief Czyści wykres.
     * @param chart Wykres do wyczyszczenia.
     */
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

    /**
     * @brief Konfiguruje czujniki.
     */
    private void setupSensors(){
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    /**
     * @brief Konfiguruje latarkę.
     */
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

    /**
     * @brief Konfiguruje lokalizację.
     */
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

    /**
     * @brief Wyświetla ogólne informacje o urządzeniu.
     */
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

    /**
     * @brief Wyświetla ogólne informacje o urządzeniu.
     */
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

    /**
     * @brief Wyświetla informacje GPS.
     */
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

    /**
     * @brief Wyświetla informacje systemowe.
     */
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

    /**
     * @brief Wyświetla dane żyroskopu.
     */
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

    /**
     * @brief Pobiera pojemność dysku.
     * @return Informacje o dysku.
     */
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

    /**
     * @brief Pobiera adres na podstawie współrzędnych.
     * @param lat Szerokość.
     * @param lon Długość.
     * @return Adres lub komunikat o błędzie.
     */
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

    /**
     * @brief Sprawdza i prosi o uprawnienia GPS.
     */
    private void sprawdzIpoprosOPermISjeGPS(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            uruchomNasluchiwanieGPS();
        } else {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_CODE_GPS_PERMISSION);
        }
    }

    /**
     * @brief Uruchamia nasłuchiwanie GPS.
     */
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

    /**
     * @brief Zatrzymuje nasłuchiwanie GPS.
     */
    private void zatrzymajNasluchiwanieGPS(){
        if(locationManager!=null && locationListener!=null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored){}
        }
    }

    /**
     * @brief Pobiera dane pogodowe.
     * @param lat Szerokość.
     * @param lon Długość.
     */
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

    /**
     * @brief Formatuje i wyświetla pogodę.
     * @param json Dane pogodowe w JSON.
     */
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

    /**
     * @brief Uruchamia odświeżanie RAM.
     */
    private void startRamRefresher(){ ramHandler.removeCallbacks(ramRunnable); ramHandler.post(ramRunnable); }

    /**
     * @brief Zatrzymuje odświeżanie RAM.
     */
    private void stopRamRefresher(){ ramHandler.removeCallbacks(ramRunnable); }

    /**
     * @brief Otwiera mapy Google.
     */
    private void otworzMapyGoogle(){
        try {
            String uri = String.format(Locale.US,"geo:%f,%f?q=%f,%f(Tu jesteś)",aktualnaSzerokosc,aktualnaDlugosc,aktualnaSzerokosc,aktualnaDlugosc);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (Exception e) { Toast.makeText(this,"Nie znaleziono aplikacji map.",Toast.LENGTH_LONG).show(); }
    }

    /**
     * @brief Przełącza latarkę (pominięta).
     */
    private void przelaczLatarke(){
        Toast.makeText(this,"Latarka pominięta w tej wersji",Toast.LENGTH_SHORT).show();
    }

    /**
     * @brief Wibruje urządzenie.
     * @param ms Czas wibracji w milisekundach.
     */
    @SuppressLint("MissingPermission")
    private void wibruj(int ms){
        Vibrator v=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        if (v!=null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        }
    }

    /**
     * @brief Kopiuje tekst do schowka.
     * @param txt Tekst do skopiowania.
     */
    private void kopiujDoSchowka(String txt){
        ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb!=null) { cb.setPrimaryClip(ClipData.newPlainText("Dane",txt)); Toast.makeText(this,"Skopiowano",Toast.LENGTH_SHORT).show(); }
    }

    // ==========================================
    // OBSŁUGA SENSORÓW
    // ==========================================

    /**
     * @brief Obsługuje zmiany sensorów.
     * @param event Zdarzenie sensora.
     */
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

    /**
     * @brief Obsługuje zmiany dokładności sensora.
     * @param sensor Sensor.
     * @param accuracy Dokładność.
     */
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy){}

    // ==========================================
    // LISTENERY FIREBASE
    // ==========================================

    /**
     * @brief Uruchamia listener dla danych RAM z Firebase.
     */
    private void startFirebaseRamListener() {
        if (!firebasePolaczony || firebaseDeviceRef == null || ramFirebaseListener != null) return;
        ramFirebaseListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) { }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        firebaseDeviceRef.addValueEventListener(ramFirebaseListener);
    }

    /**
     * @brief Zatrzymuje listener dla danych RAM z Firebase.
     */
    private void stopFirebaseRamListener() {
        if (firebaseDeviceRef != null && ramFirebaseListener != null) {
            firebaseDeviceRef.removeEventListener(ramFirebaseListener);
            ramFirebaseListener = null;
        }
    }

    /**
     * @brief Klasa usługi monitorowania sensorów w tle.
     *
     * Usługa działa w tle, zbierając dane o RAM, baterii, CPU i wysyłając je do Firebase.
     * Wyświetla także powiadomienia z aktualnymi wartościami.
     */
    public static class SensorMonitorService extends Service {
        private static final String CHANNEL_ID = "mobile_sensor_monitor_channel";  ///< ID kanału powiadomień.
        private static final int NOTIFICATION_ID = 1001;  ///< ID powiadomienia.

        private final Handler handler = new Handler(Looper.getMainLooper());  ///< Handler dla głównego wątku.
        private Runnable loop;  ///< Pętla monitorowania.

        private boolean firebaseReady = false;  ///< Czy Firebase jest gotowe.
        private DatabaseReference firebaseDeviceRef;  ///< Referencja do urządzenia w Firebase.
        private String deviceId;  ///< ID urządzenia.

        private static final long PUBLISH_INTERVAL_MS = 60_000;  ///< Interwał publikowania (60 sekund).
        private long lastPublishTs = 0;  ///< Ostatni czas publikacji.
        private double sumRam = 0, sumTemp = 0, sumCpu = 0;  ///< Sumy próbek.
        private int countSamples = 0;  ///< Liczba próbek.

        /**
         * @brief Metoda wywoływana przy tworzeniu usługi.
         */
        @Override public void onCreate(){
            super.onCreate();
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, buildNotification("Monitoring w toku..."));
            initFirebase();
            setupLoop();
            handler.post(loop);
            pushNow();
        }

        /**
         * @brief Inicjalizuje Firebase w usłudze.
         */
        private void initFirebase(){
            try {
                FirebaseApp.initializeApp(this);
                FirebaseDatabase db = FirebaseDatabase.getInstance(FIREBASE_URL);
                deviceId = Build.MODEL.replace(" ", "_") + "_" + (System.currentTimeMillis() % 10000);
                firebaseDeviceRef = db.getReference("statystyki_urzadzen").child(deviceId);
                firebaseReady = true;
            } catch (Exception e){ firebaseReady = false; }
        }

        /**
         * @brief Konfiguruje pętlę monitorowania.
         */
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

        /**
         * @brief Wysyła natychmiastową próbkę do Firebase.
         */
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

        /**
         * @brief Buduje powiadomienie.
         * @param content Treść powiadomienia.
         * @return Notification.
         */
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

        /**
         * @brief Aktualizuje powiadomienie.
         * @param content Nowa treść.
         */
        private void updateNotification(String content){
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm!=null) nm.notify(NOTIFICATION_ID, buildNotification(content));
        }

        /**
         * @brief Tworzy kanał powiadomień.
         */
        private void createNotificationChannel(){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "MobileSensor Monitor", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Stały monitoring RAM/CPU/Bateria");
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm!=null) nm.createNotificationChannel(ch);
            }
        }

        /**
         * @brief Pobiera częstotliwość CPU.
         * @return Częstotliwość w GHz.
         */
        private float getCpuFreqFloat(){
            try (RandomAccessFile reader = new RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq","r")){
                String line=reader.readLine();
                return line==null?0:Float.parseFloat(line)/1_000_000.0f;
            } catch (Exception e){ return 0; }
        }

        /**
         * @brief Zaokrągla liczbę.
         * @param v Wartość.
         * @param p Miejsca dziesiętne.
         * @return Zaokrąglona wartość.
         */
        private double round(double v,int p){ long f=(long)Math.pow(10,p); return Math.round(v*f)/(double)f; }

        /**
         * @brief Zwraca binder (nie używany).
         * @param intent Intent.
         * @return IBinder.
         */
        @Nullable @Override public IBinder onBind(Intent intent){ return null; }

        /**
         * @brief Uruchamia usługę.
         * @param intent Intent.
         * @param flags Flagi.
         * @param startId ID startu.
         * @return Tryb startu.
         */
        @Override public int onStartCommand(Intent intent, int flags, int startId){ return START_STICKY; }

        /**
         * @brief Niszczy usługę.
         */
        @Override public void onDestroy(){ handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    }
}