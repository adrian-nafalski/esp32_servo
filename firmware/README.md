# Firmware ESP32 — Servo BLE

Firmware ESP32 do sterowania jednym serwomechanizmem pozycyjnym przez Bluetooth Low Energy. Współpracuje z aplikacją Android [Servo](../README.md).

ESP32 działa jako BLE Peripheral i GATT Server. Odbiera zadany kąt, sprawdza poprawność komendy i generuje sygnał PWM dla serwa.

Projekt wykorzystuje własny protokół demonstracyjny, a nie standardowy profil BLE do sterowania serwomechanizmami.

## Status

Firmware został skompilowany, wgrany na ESP32 i uruchomiony na fizycznym sprzęcie. Sprawdzono połączenie z aplikacją Android oraz sterowanie podłączonym serwomechanizmem.

Nie oznacza to potwierdzenia zgodności ze wszystkimi wariantami ESP32, wersjami Arduino-ESP32 ani modelami serw.

## Pliki

```text
firmware/
├── README.md
└── esp32_servo_ble/
    └── esp32_servo_ble.ino
```

## Wymagania

- Płytka ESP32 obsługująca BLE i zgodna z używanym pakietem Arduino-ESP32.
- Serwomechanizm pozycyjny.
- Zasilanie dobrane do wymagań serwa.
- Przewody połączeniowe.
- Arduino IDE z pakietem `esp32 by Espressif Systems`.

Szkic korzysta z biblioteki BLE dostarczanej z pakietem Arduino-ESP32.

Przed użyciem innego wariantu płytki lub innej wersji pakietu sprawdź zgodność API BLE i generowania PWM. Samo oznaczenie „ESP32” nie gwarantuje obsługi BLE przez każdy układ z tej rodziny.

## Konfiguracja

Ustawienia znajdują się na początku pliku:

```text
esp32_servo_ble/esp32_servo_ble.ino
```

| Ustawienie | Znaczenie |
|---|---|
| `SERVO_PIN` | GPIO podłączone do przewodu sygnałowego serwa |
| `ANGLE_MIN` / `ANGLE_MAX` | Dopuszczalny zakres kąta zadanego |
| `PULSE_MIN_US` / `PULSE_MAX_US` | Szerokości impulsów odpowiadające krańcom zakresu |
| `RELEASE_ON_DISCONNECT` | Określa, czy wyłączyć sygnał sterujący po rozłączeniu BLE |

### Pin sygnałowy

Przed kompilacją sprawdź wartość `SERVO_PIN`.

Wybierz GPIO, które zgodnie z dokumentacją konkretnej płytki:

- może pracować jako wyjście,
- jest dostępne do użycia,
- nie koliduje z pamięcią flash, PSRAM ani innymi używanymi peryferiami,
- nie powoduje problemów z uruchamianiem płytki.

Nie przenoś numeru GPIO między różnymi modelami płytek bez sprawdzenia ich dokumentacji.

### Zakres i PWM

Domyślny zakres logiczny projektu to 0–180°, a parametry impulsów to 1000–2000 µs przy 50 Hz.

Są to ustawienia demonstracyjne, nie gwarancja zgodności z konkretnym serwem ani uzyskania fizycznego zakresu 180°.

Przed użyciem:

1. Sprawdź wymagania użytego serwa.
2. Dopasuj zakres kąta i szerokości impulsów.
3. Wykonaj pierwsze próby bez obciążenia.
4. Nie wymuszaj ruchu poza ograniczenia mechaniczne.

Zakres `ANGLE_MIN` / `ANGLE_MAX` musi odpowiadać konfiguracji `ServoConfig.angleRange` w aplikacji Android.

## Podłączenie

Zidentyfikuj przewody na podstawie dokumentacji swojego serwa. Nie opieraj się wyłącznie na kolorach.

| Przewód serwa | Połączenie |
|---|---|
| Masa — GND | Masa zasilacza serwa i GND ESP32 |
| Zasilanie | Wyjście zasilacza o napięciu odpowiednim dla serwa |
| Sygnał | GPIO określone przez `SERVO_PIN` |

### Zasilanie

- Nie zasilaj serwa z wyjścia `3V3` ESP32.
- Dobierz napięcie i wydajność prądową zasilacza do konkretnego modelu serwa.
- Przy osobnym zasilaniu serwa połącz masę jego zasilacza z GND ESP32.
- Uwzględnij zwiększony pobór prądu podczas rozruchu, obciążenia i zablokowania wału.
- Przed podłączeniem sprawdź polaryzację i połączenia.

Zgodność wejścia sygnałowego serwa z poziomem 3,3 V należy sprawdzić w dokumentacji lub zweryfikować dla użytego egzemplarza.

## Kompilacja i wgrywanie

1. Zainstaluj pakiet `esp32 by Espressif Systems` w Arduino IDE.
2. Otwórz:
   ```text
   esp32_servo_ble/esp32_servo_ble.ino
   ```
3. Wybierz właściwą płytkę i port.
4. Sprawdź `SERVO_PIN`, zakres kąta i parametry PWM.
5. Skompiluj szkic.
6. Wgraj firmware na ESP32.
7. Otwórz Serial Monitor z prędkością `115200`.
8. Sprawdź komunikaty uruchomienia i rozpoczęcia ogłaszania BLE.

Domyślna nazwa urządzenia:

```text
Servo-ESP32
```

## Protokół BLE

Telefon działa jako GATT Client, a ESP32 jako GATT Server.

| Element | UUID | Dostęp |
|---|---|---|
| Usługa serwa | `f6330001-4862-4597-8d0d-4b9db8a3b9c7` | — |
| Kąt zadany | `f6330002-4862-4597-8d0d-4b9db8a3b9c7` | Write |
| Ostatni przyjęty kąt | `f6330003-4862-4597-8d0d-4b9db8a3b9c7` | Read |

### Komenda kąta

Komenda zawiera dokładnie 2 bajty: liczbę `uint16` w kolejności little-endian, wyrażoną w stopniach.

Przykład:

```text
Kąt:       90°
Wartość:   0x005A
Bajty:     5A 00
```

Firmware odrzuca:

- komendy o nieprawidłowej długości,
- wartości spoza skonfigurowanego zakresu.

Odrzucona komenda nie zmienia wartości PWM ani ostatniego przyjętego kąta.

### Odczyt statusu

Charakterystyka statusu zwraca ostatni kąt przyjęty przez firmware, również jako dwubajtowy `uint16` little-endian.

Wartość specjalna:

```text
FF FF → brak poprawnej komendy od uruchomienia ESP32
```

Status nie jest pomiarem położenia wału. Informuje wyłącznie o ostatnim zaakceptowanym kącie zadanym.

Firmware nie udostępnia notyfikacji. Aplikacja Android odczytuje status po połączeniu, nie jako ciągłą telemetrię.

### Znaczenie potwierdzenia zapisu

Potwierdzenie operacji zapisu GATT nie jest potwierdzeniem fizycznego ruchu serwa ani osiągnięcia zadanej pozycji.

Nie należy go również traktować jako niezależnego potwierdzenia walidacji komendy przez logikę firmware. Ostatnią przyjętą wartość można sprawdzić przez odczyt charakterystyki statusu.

## Zachowanie

### Po uruchomieniu

Firmware oczekuje na pierwszą poprawną komendę. Do tego czasu nie generuje sygnału PWM ustawiającego serwo, a status ma wartość `0xFFFF`.

Brak komendy z firmware nie jest gwarancją całkowitego braku ruchu podczas włączania zasilania serwa.

### Po otrzymaniu komendy

Firmware:

1. Sprawdza długość danych.
2. Dekoduje kąt.
3. Sprawdza dopuszczalny zakres.
4. Aktualizuje sygnał PWM.
5. Zapisuje ostatni przyjęty kąt do odczytu.

### Po rozłączeniu

Zachowanie zależy od `RELEASE_ON_DISCONNECT`:

| Wartość | Zachowanie |
|---|---|
| `false` | Sygnał PWM pozostaje ustawiony na ostatnią przyjętą wartość |
| `true` | Generowanie sygnału sterującego zostaje wyłączone |

Wyłączenie PWM nie oznacza odłączenia zasilania serwa. Nie należy też traktować go jako gwarancji określonego zachowania mechanicznego.

Po rozłączeniu ESP32 ponownie rozpoczyna ogłaszanie BLE. Firmware jest przeznaczony do sterowania przez jeden telefon jednocześnie.

## Test bez aplikacji Android

Do sprawdzenia protokołu można użyć narzędzia do obsługi BLE, np. nRF Connect.

1. Uruchom ESP32.
2. Połącz się z `Servo-ESP32`.
3. Odszukaj usługę:
   ```text
   f6330001-4862-4597-8d0d-4b9db8a3b9c7
   ```
4. Do charakterystyki kąta:
   ```text
   f6330002-4862-4597-8d0d-4b9db8a3b9c7
   ```
   zapisz dwa bajty w formacie szesnastkowym:
   ```text
   5A 00
   ```
5. Odczytaj charakterystykę:
   ```text
   f6330003-4862-4597-8d0d-4b9db8a3b9c7
   ```
6. Po przyjęciu komendy oczekiwana wartość to:
   ```text
   5A 00
   ```

Wysyłaj dane binarne w trybie HEX, a nie tekst ASCII `"5A00"`.

Pierwsze próby wykonuj bez obciążenia mechanicznego.

## Rozwiązywanie problemów

### ESP32 nie pojawia się na liście

- Sprawdź zasilanie i komunikaty Serial Monitora.
- Sprawdź, czy wybrany układ obsługuje BLE.
- Upewnij się, że urządzenie nie jest już połączone z innym klientem.
- Sprawdź zgodność UUID firmware i aplikacji.

### Połączenie działa, ale serwo nie reaguje

- Sprawdź `SERVO_PIN` i rzeczywiste połączenie sygnału.
- Sprawdź zasilanie oraz wspólną masę.
- Sprawdź komunikaty walidacji komend w Serial Monitorze.
- Sprawdź zakres kąta, parametry PWM i wymagania serwa.
- Zweryfikuj zgodność poziomu sygnału sterującego.

### ESP32 resetuje się podczas ruchu

Sprawdź wydajność zasilacza, spadki napięcia, połączenia masy i przewody. Nie kontynuuj prób z mechanicznie zablokowanym serwem.

## Ograniczenia

- Brak pomiaru fizycznego położenia wału.
- Brak wykrywania przeciążenia lub zablokowania serwa.
- Brak potwierdzenia osiągnięcia zadanej pozycji.
- Zgodność z innymi płytkami i wersjami bibliotek wymaga sprawdzenia.
- Projekt ma charakter demonstracyjny i nie jest przeznaczony do zastosowań wymagających gwarantowanego bezpieczeństwa ruchu.