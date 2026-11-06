/*
 * Servo — firmware ESP32 do sterowania serwomechanizmem przez BLE
 *
 * ESP32 działa jako BLE Peripheral / GATT Server.
 * Telefon wysyła kąt zadany, a ESP32 generuje sygnał PWM dla serwa.
 *
 * Status projektu:
 * Połączenie BLE i sterowanie serwem sprawdzono na fizycznym zestawie.
 * Zgodność z innymi płytkami i wersjami Arduino-ESP32 wymaga weryfikacji.
 *
 * Protokół:
 * Własny protokół demonstracyjny, nie standardowy profil BLE dla serw.
 * UUID i format danych muszą odpowiadać konfiguracji aplikacji Android.
 *
 * Usługa:
 *   f6330001-4862-4597-8d0d-4b9db8a3b9c7
 *
 * Kąt zadany — Write:
 *   f6330002-4862-4597-8d0d-4b9db8a3b9c7
 *   Dokładnie 2 bajty, uint16 little-endian, wartość w stopniach.
 *
 * Ostatni przyjęty kąt — Read:
 *   f6330003-4862-4597-8d0d-4b9db8a3b9c7
 *   2 bajty, uint16 little-endian.
 *   0xFFFF oznacza brak poprawnej komendy od uruchomienia ESP32.
 *
 * Przyjęcie komendy oznacza:
 *   - poprawną długość danych,
 *   - kąt w skonfigurowanym zakresie,
 *   - przekazanie wartości do obsługi PWM.
 *
 * Firmware nie otrzymuje informacji zwrotnej o położeniu wału.
 * Status nie jest pomiarem kąta ani potwierdzeniem zakończenia ruchu.
 *
 * Potwierdzenie zapisu GATT nie jest aplikacyjnym potwierdzeniem
 * walidacji komendy ani osiągnięcia pozycji przez serwo.
 *
 * Zachowanie:
 *   - PWM jest włączany dopiero po pierwszej poprawnej komendzie,
 *   - błędne komendy nie zmieniają PWM ani statusu,
 *   - zachowanie PWM po rozłączeniu określa RELEASE_ON_DISCONNECT,
 *   - po rozłączeniu urządzenie ponownie rozpoczyna ogłaszanie BLE.
 *
 * Kontrola dostępu:
 * Szkic nie konfiguruje uwierzytelniania klienta ani aplikacyjnej
 * autoryzacji komend. Nazwa urządzenia i UUID nie są hasłem dostępu.
 *
 * Konfiguracja połączeń i zasilania: patrz README.md w katalogu firmware.
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>

// -----------------------------------------------------------------------------
// Konfiguracja sprzętu
// -----------------------------------------------------------------------------

// Odkomentuj i wpisz GPIO podłączone do przewodu sygnałowego serwa.
// Dobierz pin zgodnie z dokumentacją konkretnej płytki.
// #define SERVO_PIN TWOJ_NUMER_GPIO

#ifndef SERVO_PIN
#error "Ustaw SERVO_PIN: odkomentuj definicje i wpisz numer GPIO sygnalu serwa."
#endif

// Zakres logiczny musi odpowiadać ServoConfig.angleRange w aplikacji.
// Nie jest to gwarancja fizycznego zakresu ruchu konkretnego serwa.
constexpr uint16_t ANGLE_MIN = 0;
constexpr uint16_t ANGLE_MAX = 180;

// Parametry demonstracyjne — dopasuj do użytego serwa.
// Pierwsze próby wykonuj bez obciążenia mechanicznego.
constexpr uint32_t PULSE_MIN_US = 1000;
constexpr uint32_t PULSE_MAX_US = 2000;

constexpr uint32_t SERVO_FREQUENCY_HZ = 50;
constexpr uint8_t PWM_RESOLUTION_BITS = 14;

// false: pozostaw generowanie PWM z ostatnią zadaną wartością.
// true: wyłącz PWM po rozłączeniu.
//
// Wyłączenie PWM nie odłącza zasilania serwa i nie gwarantuje
// określonego zachowania mechanicznego.
constexpr bool RELEASE_ON_DISCONNECT = false;

// -----------------------------------------------------------------------------
// Konfiguracja protokołu BLE
// -----------------------------------------------------------------------------

constexpr const char *DEVICE_NAME = "Servo-ESP32";

constexpr const char *SERVICE_UUID =
    "f6330001-4862-4597-8d0d-4b9db8a3b9c7";

constexpr const char *TARGET_ANGLE_UUID =
    "f6330002-4862-4597-8d0d-4b9db8a3b9c7";

constexpr const char *LAST_ACCEPTED_UUID =
    "f6330003-4862-4597-8d0d-4b9db8a3b9c7";

constexpr uint16_t NO_COMMAND_SINCE_BOOT = 0xFFFF;

// -----------------------------------------------------------------------------
// Konfiguracja wewnętrzna
// -----------------------------------------------------------------------------

constexpr uint32_t PWM_PERIOD_US =
    1000000UL / SERVO_FREQUENCY_HZ;

static_assert(
    ANGLE_MIN < ANGLE_MAX,
    "ANGLE_MIN musi byc mniejsze od ANGLE_MAX"
);

static_assert(
    ANGLE_MAX < NO_COMMAND_SINCE_BOOT,
    "Wartosc 0xFFFF jest zarezerwowana"
);

static_assert(
    PULSE_MIN_US < PULSE_MAX_US,
    "PULSE_MIN_US musi byc mniejsze od PULSE_MAX_US"
);

static_assert(
    PULSE_MAX_US < PWM_PERIOD_US,
    "Impuls musi byc krotszy niz okres PWM"
);

#if ESP_ARDUINO_VERSION_MAJOR < 3
constexpr uint8_t PWM_CHANNEL = 0;
#endif

static BLECharacteristic *lastAcceptedCharacteristic = nullptr;

static volatile bool pwmAttached = false;
static volatile bool advertisingRestartRequested = false;

// -----------------------------------------------------------------------------
// PWM
// -----------------------------------------------------------------------------

static uint32_t pulseForAngle(uint16_t angle) {
  return PULSE_MIN_US +
      static_cast<uint32_t>(angle - ANGLE_MIN) *
      (PULSE_MAX_US - PULSE_MIN_US) /
      (ANGLE_MAX - ANGLE_MIN);
}

static uint32_t dutyForPulse(uint32_t pulseUs) {
  return static_cast<uint32_t>(
      (static_cast<uint64_t>(pulseUs) << PWM_RESOLUTION_BITS) /
      PWM_PERIOD_US
  );
}

static bool attachPwm() {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  return ledcAttach(
      SERVO_PIN,
      SERVO_FREQUENCY_HZ,
      PWM_RESOLUTION_BITS
  );
#else
  if (ledcSetup(
          PWM_CHANNEL,
          SERVO_FREQUENCY_HZ,
          PWM_RESOLUTION_BITS
      ) == 0) {
    return false;
  }

  ledcAttachPin(SERVO_PIN, PWM_CHANNEL);
  return true;
#endif
}

static void writeDuty(uint32_t duty) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(SERVO_PIN, duty);
#else
  ledcWrite(PWM_CHANNEL, duty);
#endif
}

static void releasePwm() {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcDetach(SERVO_PIN);
#else
  ledcDetachPin(SERVO_PIN);
#endif

  pinMode(SERVO_PIN, OUTPUT);
  digitalWrite(SERVO_PIN, LOW);
  pwmAttached = false;
}

static bool applyAngle(uint16_t angle) {
  if (!pwmAttached) {
    if (!attachPwm()) {
      Serial.println(
          "Błąd: nie udało się skonfigurować PWM. "
          "Sprawdź GPIO i konfigurację LEDC."
      );
      return false;
    }

    pwmAttached = true;
  }

  const uint32_t pulseUs = pulseForAngle(angle);
  writeDuty(dutyForPulse(pulseUs));

  Serial.printf(
      "Kąt zadany: %u°; impuls: %lu µs\n",
      static_cast<unsigned>(angle),
      static_cast<unsigned long>(pulseUs)
  );

  return true;
}

// -----------------------------------------------------------------------------
// Status protokołu
// -----------------------------------------------------------------------------

static void publishLastAccepted(uint16_t angle) {
  uint8_t payload[2] = {
      static_cast<uint8_t>(angle & 0xFF),
      static_cast<uint8_t>(angle >> 8)
  };

  lastAcceptedCharacteristic->setValue(payload, sizeof(payload));
}

// -----------------------------------------------------------------------------
// Callbacki BLE
// -----------------------------------------------------------------------------

class AngleCallbacks : public BLECharacteristicCallbacks {
public:
  void onWrite(BLECharacteristic *characteristic) override {
    const size_t length = characteristic->getLength();
    const uint8_t *data = characteristic->getData();

    if (length != 2 || data == nullptr) {
      Serial.printf(
          "Odrzucono komendę: oczekiwano 2 bajtów, otrzymano %u.\n",
          static_cast<unsigned>(length)
      );
      return;
    }

    const uint16_t angle =
        static_cast<uint16_t>(data[0]) |
        (static_cast<uint16_t>(data[1]) << 8);

    if (angle < ANGLE_MIN || angle > ANGLE_MAX) {
      Serial.printf(
          "Odrzucono komendę: kąt %u poza zakresem %u–%u.\n",
          static_cast<unsigned>(angle),
          static_cast<unsigned>(ANGLE_MIN),
          static_cast<unsigned>(ANGLE_MAX)
      );
      return;
    }

    if (applyAngle(angle)) {
      publishLastAccepted(angle);
    }
  }
};

class ServerCallbacks : public BLEServerCallbacks {
public:
  void onConnect(BLEServer *server) override {
    Serial.println("Klient BLE połączony.");
  }

  void onDisconnect(BLEServer *server) override {
    Serial.println("Klient BLE rozłączony.");

    if (RELEASE_ON_DISCONNECT && pwmAttached) {
      releasePwm();

      // Zachowujemy ostatni przyjęty kąt.
      // Wyłączenie PWM nie oznacza restartu urządzenia ani braku
      // wcześniejszych komend. Status nie opisuje aktywności PWM.
    }

    advertisingRestartRequested = true;
  }
};

// -----------------------------------------------------------------------------
// Uruchomienie
// -----------------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  Serial.println();
  Serial.println(
      "Servo BLE — start. PWM zostanie włączony "
      "po pierwszej poprawnej komendzie."
  );

  BLEDevice::init(DEVICE_NAME);

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);

  BLECharacteristic *targetAngleCharacteristic =
      service->createCharacteristic(
          TARGET_ANGLE_UUID,
          BLECharacteristic::PROPERTY_WRITE
      );

  targetAngleCharacteristic->setCallbacks(new AngleCallbacks());

  lastAcceptedCharacteristic = service->createCharacteristic(
      LAST_ACCEPTED_UUID,
      BLECharacteristic::PROPERTY_READ
  );

  publishLastAccepted(NO_COMMAND_SINCE_BOOT);

  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);

  BLEDevice::startAdvertising();

  Serial.printf(
      "Ogłaszanie jako \"%s\".\n",
      DEVICE_NAME
  );
}

// -----------------------------------------------------------------------------
// Pętla główna
// -----------------------------------------------------------------------------

void loop() {
  if (advertisingRestartRequested) {
    advertisingRestartRequested = false;

    // Ponowne ogłaszanie poza callbackiem rozłączenia.
    delay(200);
    BLEDevice::startAdvertising();

    Serial.println("Ponowne ogłaszanie BLE.");
  }

  delay(20);
}
