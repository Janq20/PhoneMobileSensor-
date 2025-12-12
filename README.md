# 📱 MobileSensorMonitor

MobileSensorMonitor to aplikacja do kompleksowego monitorowania urządzeń mobilnych, zbierania danych systemowych, sensorowych i lokalizacyjnych w czasie rzeczywistym. Wykorzystuje Firebase do przechowywania danych oraz bibliotekę MPAndroidChart do wizualizacji wykresów.

---

## 💡 Opis Projektu

MobileSensorMonitor to zaawansowana aplikacja mobilna przeznaczona do monitorowania kluczowych parametrów urządzenia Android. Umożliwia zbieranie danych o zużyciu pamięci RAM, poziomie baterii, temperaturze, częstotliwości CPU, odczytach sensorów (żyroskop, akcelerometr, czujnik światła), lokalizacji GPS oraz danych pogodowych.

Dane są wizualizowane na żywo za pomocą wykresów liniowych oraz przechowywane w chmurze Firebase dla dalszej analizy. Aplikacja działa w tle jako usługa systemowa, zapewniając ciągłe monitorowanie nawet przy zamkniętej aplikacji głównej.

---

## ✨ Główne Cechy

* **Monitorowanie Systemu:** RAM, CPU, bateria, dysk.
* **Czujniki:** Żyroskop, akcelerometr, światło.
* **Lokalizacja:** GPS z mapami Google i pogoda (OpenWeatherMap).
* **Wykresy w Czasie Rzeczywistym:** Wizualizacja danych za pomocą MPAndroidChart.
* **Firebase Integration:** Przechowywanie i ładowanie danych historycznych.
* **Usługa w Tle:** Ciągłe zbieranie danych z notyfikacjami.
* **Profesjonalny Interfejs:** Ciemny motyw, intuicyjna nawigacja.

---

## 🚀 Funkcjonalności Ekranów

| Ekran | Zawartość |
| :--- | :--- |
| **Ekran Ogólny** | Podsumowanie urządzenia (model, pamięć, dysk, bateria). |
| **Ekran GPS** | Współrzędne, adres, pogoda w czasie rzeczywistym. |
| **Ekran Żyroskop** | Odczyty żyroskopu i akcelerometru. |
| **Ekran System** | Wykresy RAM, baterii, CPU, światła na żywo. |
| **Ekran Aplikacja** | Ładowanie i wizualizacja danych z Firebase dla innych urządzeń. |
| **Usługa Monitor** | Ciągłe zbieranie danych w tle z notyfikacjami. |
| **Eksport Danych** | Kopiowanie informacji do schowka. |
| **Latarka** | Kontrola latarki (symulowana w wersji demo). |

---

## 📋 Wymagania i Uprawnienia

* **Android API:** Minimum 21 (Android 5.0), zalecane 30+ (Android 11+).

| Uprawnienie | Użycie | Wymagane API |
| :--- | :--- | :--- |
| `ACCESS_FINE_LOCATION` | Lokalizacja GPS. | Wymagane |
| `CAMERA` | Latarka. | Wymagane |
| `POST_NOTIFICATIONS` | Notyfikacje. | API 33+ |

**Biblioteki zewnętrzne:** Firebase (Realtime Database), MPAndroidChart, OpenWeatherMap API (wymaga klucza API).

---

## 🛠 Instalacja

1.  **Sklonuj repozytorium:**
    ```bash
    bash git clone [https://github.com/Janq20/MobileSensorMonitor.git](https://github.com/Janq20/MobileSensorMonitor.git)
    cd MobileSensorMonitor
    ```
2.  **Otwórz w Android Studio:** Zaimportuj projekt jako projekt Gradle.
3.  **Uruchom:** Podłącz urządzenie lub emulator. Uruchom `Run > Run 'app'` w Android Studio.

---

## 📖 Użycie

1.  **Uruchom aplikację:** Przy pierwszym uruchomieniu przyznaj wymagane uprawnienia.
2.  **Monitorowanie:** W trybie "System" zobacz wykresy na żywo.
3.  **Ładowanie Danych:** W trybie "Aplikacja" wybierz urządzenie z listy i załaduj dane historyczne.
4.  **GPS i Pogoda:** Kliknij w trybie GPS, aby otworzyć mapy.

> **Przykład działania:** Aplikacja automatycznie uruchamia usługę w tle. Dane są publikowane co 60 sekund do Firebase. Wykresy aktualizują się co sekundę.

---

## 🏗 Architektura

* **MainActivity.java:** Główna aktywność, zarządzanie UI, sensorami, lokalizacją.
* **SensorMonitorService.java:** Usługa w tle dla ciągłego monitorowania.
* **Firebase:** Realtime Database dla przechowywania danych.
* **MPAndroidChart:** Biblioteka do rysowania wykresów.
* **Wątki:** ExecutorService dla zadań asynchronicznych, Handler dla aktualizacji UI.

### 📊 Generowane Wykresy (PNG)

Aplikacja generuje cztery osobne wykresy PNG poprzez kod Python:

* `wykres_bateria_poziom.png`: Poziom baterii w czasie.
* `wykres_bateria_temp.png`: Temperatura baterii.
* `wykres_cpu.png`: Częstotliwość CPU.
* `wykres_ram.png`: Użycie RAM.

---

## 🔒 Bezpieczeństwo

Wszystkie wrażliwe dane (lokalizacja, kamera) wymagają zgody użytkownika. Dane są przechowywane w Firebase z domyślnymi ustawieniami bezpieczeństwa. Nie przechowujemy danych osobowych poza współrzędnymi GPS.

---

## 🐛 Znane Problemy

* **Latarka:** Obecnie symulowana (Toast), nie działa fizycznie.
* **Firebase:** Wymaga stabilnego połączenia internetowego.
* **Wykresy:** Mogą być wolne na starszych urządzeniach.

---

## 🤝 Współpraca

* Pull requests mile widziane!
* Zgłoś błędy lub sugestie przez Issues.

---

## 📄 Licencja

Ten projekt jest na licencji MIT - zobacz plik `LICENSE` dla szczegółów.

---

## 👤 Autorzy

* Janq20
* Zahinisu
