

MobileSensorMonitor jest to aplikacja do kompleksowego monitorowania urządzeń mobilnych, zbierania danych systemowych, sensorowych i lokalizacyjnych w czasie rzeczywistym. Wykorzystuje Firebase do przechowywania danych oraz bibliotekę MPAndroidChart do wizualizacji wykresów.

**📱 Opis**
MobileSensorMonitor to zaawansowana aplikacja mobilna przeznaczona do monitorowania kluczowych parametrów urządzenia Android. Aplikacja umożliwia zbieranie danych o zużyciu pamięci RAM, poziomie baterii, temperaturze, częstotliwości CPU, odczytach sensorów (żyroskop, akcelerometr, czujnik światła), lokalizacji GPS oraz danych pogodowych. Dane są wizualizowane na żywo za pomocą wykresów liniowych oraz przechowywane w chmurze Firebase dla dalszej analizy.
Aplikacja działa w tle jako usługa systemowa, zapewniając ciągłe monitorowanie nawet przy zamkniętej aplikacji głównej.

**Główne cechy:**
Monitorowanie systemu: RAM, CPU, bateria, dysk.
Czujniki: Żyroskop, akcelerometr, światło.
Lokalizacja: GPS z mapami Google i pogodą (OpenWeatherMap).
Wykresy w czasie rzeczywistym: Wizualizacja danych za pomocą MPAndroidChart.
Firebase Integration: Przechowywanie i ładowanie danych historycznych.
Profesjonalny interfejs: Ciemny motyw, intuicyjne nawigacja.
Usługa w tle: Notyfikacje i ciągłe zbieranie danych.
**🚀 Funkcje**
Ekran Ogólny: Podsumowanie urządzenia (model, pamięć, dysk, bateria).
Ekran GPS: Współrzędne, adres, pogoda w czasie rzeczywistym.
Ekran Żyroskop: Odczyty żyroskopu i akcelerometru.
Ekran System: Wykresy RAM, baterii, CPU, światła na żywo.
Ekran Aplikacja: Ładowanie i wizualizacja danych z Firebase dla innych urządzeń.
Usługa Monitor: Ciągłe zbieranie danych w tle z notyfikacjami.
Eksport danych: Kopiowanie informacji do schowka.
Latarka: Kontrola latarki (symulowana w wersji demo).
**📋 Wymagania**
Android API: Minimum 21 (Android 5.0), zalecane 30+ (Android 11+).
**Uprawnienia:**
Lokalizacja (ACCESS_FINE_LOCATION).
Aparat (CAMERA) dla latarki.
Notyfikacje (POST_NOTIFICATIONS) dla API 33+.
Biblioteki zewnętrzne:
Firebase (Realtime Database).
MPAndroidChart dla wykresów.
OpenWeatherMap API (wymaga klucza API).
Zależności: Patrz build.gradle.

**🛠 Instalacja**
Sklonuj repozytorium:
bash
git clone https://github.com/yourusername/MobileSensorMonitor.git
cd MobileSensorMonitor
Otwórz w Android Studio: Zaimportuj projekt jako projekt Gradle.

Podłącz urządzenie lub emulator.
Uruchom Run > Run 'app' w Android Studio.
**📖 Użycie**
Uruchom aplikację: Przy pierwszym uruchomieniu przyznaj uprawnienia.
Nawigacja: Użyj przycisków na dole ekranu do przełączania między trybami.
Monitorowanie: W trybie "System" zobacz wykresy na żywo.
Ładowanie danych: W trybie "Aplikacja" wybierz urządzenie z listy i załaduj dane historyczne.
Kopiowanie danych: Przytrzymaj tekst, aby skopiować informacje.
GPS i Pogoda: Kliknij w trybie GPS, aby otworzyć mapy.
Przykład działania:
Aplikacja automatycznie uruchamia usługę w tle.
Dane są publikowane co 60 sekund do Firebase.
Wykresy aktualizują się co sekundę.
**🏗 Architektura**
MainActivity.java: Główna aktywność, zarządzanie UI, sensorami, lokalizacją.
SensorMonitorService.java: Usługa w tle dla ciągłego monitorowania.
Firebase: Realtime Database dla przechowywania danych.
MPAndroidChart: Biblioteka do rysowania wykresów.
Wątki: ExecutorService dla zadań asynchronicznych, Handler dla aktualizacji UI.
Struktura projektu:
Code
app/
├── src/main/java/com/example/mobilesensor/
│   ├── MainActivity.java
│   └── (inne klasy)
├── src/main/res/
│   ├── layout/
│   │   └── activity_main.xml
│   └── values/
├── build.gradle
└── google-services.json
**📊 Wykresy i Dane**
Aplikacja generuje cztery osobne wykresy PNG poprzez kod Python:

wykres_bateria_poziom.png: Poziom baterii w czasie.
wykres_bateria_temp.png: Temperatura baterii.
wykres_cpu.png: Częstotliwość CPU.
wykres_ram.png: Użycie RAM.
Dane są zbierane co sekundę, uśredniane co minutę i wysyłane do Firebase.

**🔒 Bezpieczeństwo i Uprawnienia**
Wszystkie wrażliwe dane (lokalizacja, kamera) wymagają zgody użytkownika.
Dane są przechowywane w Firebase z domyślnymi ustawieniami bezpieczeństwa.
Nie przechowujemy danych osobowych poza współrzędnymi GPS.
**🐛 Znane problemy**
Latarka: Obecnie symulowana (Toast), nie działa fizycznie.
Firebase: Wymaga stabilnego połączenia internetowego.
Wykresy: Mogą być wolne na starszych urządzeniach.
**🤝 Współpraca**
Pull requests mile widziane! Zgłoś błędy lub sugestie przez Issues.

**📄 Licencja**
Ten projekt jest na licencji MIT - zobacz plik LICENSE dla szczegółów.

**👤 Autor**
- Janq20
- Zahinisu
