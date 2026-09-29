# My Scooty 1.0.13 — pełna tabela odbiorcza C2 / C5 / C9

Źródło: zdekompilowany `com.li.scooty.apk` z `My+Scooty_1.0.13_APKPure.zip`.

Źródłowy parser:
`com/wx/bluetooth/parse/BTOTOParseImpl.smali`

## Ważna uwaga o długości ramki

Parser najpierw rozpoznaje pierwszy bajt ramki:
- `0xC2` (w smali: `-0x3e`) → C2
- `0xC5` (w smali: `-0x3b`) → C5
- `0xC9` (w smali: `-0x37`) → C9

Następnie wykonuje:

`copyOfRange(packet, 2, 0x10)`

czyli przekazuje do parsera **16 bajtów: indeksy 2..17 oryginalnej ramki**. Dlatego poniższe pozycje 0..15 są pozycjami **payloadu parsera**, a nie indeksami całej ramki BLE. Pozycje 16..19 nie istnieją w tym parserze i nie są odczytywane.

## Sposób liczenia liczb

`ByteUtils.getUnsignedByte(data, n)`:
`data[n] & 0xFF`

`ByteUtils.getUnsignedShort(data, n)`:
`((data[n] & 0xFF) << 8) | (data[n+1] & 0xFF)`

Czyli liczby 16-bitowe są **big-endian / MSB first**.

Dla pól 32-bitowych parser składa:
`(unsignedShort[offset] << 16) | unsignedShort[offset+2]`

---

# C2

| Bajt payload | Parametr / BTType | Odczyt | Przeliczenie | Jednostka / znaczenie |
|---:|---|---|---|---|
| 0–1 | Vlt | uint16 BE | raw / 100 | **V** — aplikacja dzieli przez 100 |
| 2–3 | Speed | uint16 BE | raw / 100 | **km/h lub mph** zależnie od ustawienia jednostki; aplikacja dzieli przez 100 i zaokrągla do ekranu |
| 4–5 | MileageTotal | uint32 BE | raw / 1000 | **km**; ekran KM dzieli przez 1000 |
| 6–7 | MileageTotal | uint32 BE | raw / 1000 | druga połowa tego samego pola |
| 8–9 | WorkingCurrent | uint16 BE | brak skalowania w parserze | surowa wartość; nazwa BTType sugeruje prąd, ale widget powiązany z tym BTType prezentuje wartość jako czas pracy — jednostkę protokołu trzeba traktować jako **niepotwierdzoną** |
| 10–11 | SysTemp | uint16 BE | brak skalowania w parserze | surowa temperatura systemu; brak potwierdzonego przeliczenia w kodzie |
| 12 | — | nieużywany | — | brak odczytu |
| 13 | WhellSize | uint8 | raw | rozmiar koła / wartość konfiguracyjna |
| 14 | — | nieużywany | — | brak odczytu |
| 15 | — | nieużywany | — | brak odczytu |
| 16–19 | — | brak w payloadzie parsera | — | **nie występują** |

### C2 — pola kluczowe

- Napięcie: `U = uint16(B0,B1) / 100`
- Prędkość: `V = uint16(B2,B3) / 100`
- Przebieg całkowity: `ODO = uint32(B4..B7) / 1000`
- Temperatura: parser zwraca dokładnie uint16(B10,B11), bez offsetu i bez dzielenia.

---

# C5

| Bajt payload | Parametr / BTType | Odczyt | Przeliczenie | Jednostka / znaczenie |
|---:|---|---|---|---|
| 0–1 | MileageCurrent | uint32 BE | raw / 1000 | **km**; przebieg bieżący/trip |
| 2–3 | MileageCurrent | uint32 BE | raw / 1000 | druga połowa tego samego pola |
| 4 | GearSpeed | uint8 | raw | prędkość przypisana do biegu / limit biegu; brak skalowania w parserze |
| 5 | Gear | uint8 | raw | numer biegu |
| 6 | CellPer | uint8 | raw | **% baterii** |
| 7 | MaxSpeed | uint8 | raw | maksymalna prędkość; aplikacja traktuje jako wartość prędkości bez skalowania |
| 8–9 | Malfunction | uint16 BE | raw | kod/bitmask błędów |
| 10 | CloseTime | uint8 | raw | czas automatycznego wyłączenia / wartość konfiguracji |
| 11 | MaxGears | uint8 | raw | maksymalna liczba biegów |
| 12 | Strength | uint8 | raw | siła / parametr sterownika |
| 13 | Sensitivity | uint8 | raw | czułość / parametr sterownika |
| 14 | — | nieużywany | — | brak odczytu |
| 15 | — | nieużywany | — | brak odczytu |
| 16–19 | — | brak w payloadzie parsera | — | **nie występują** |

Dodatkowo `Cruise` jest pobierany z **tego samego bajtu 13**, więc bajt 13 ma dwa logiczne odczyty:
- `Sensitivity = B13`
- `Cruise = B13`

---

# C9

| Bajt payload | Parametr / BTType | Odczyt | Przeliczenie | Jednostka / znaczenie |
|---:|---|---|---|---|
| 0 | Mode | uint8 | raw | tryb jazdy |
| 1 | Gyro | uint8 | raw | stan/parametr żyroskopu |
| 1 | Lock | uint8 | raw | blokada |
| 2 | Headlight | uint8 | raw | stan przedniego światła |
| 2 | LightState | uint8 | raw | stan oświetlenia |
| 3 | SpeedLimit | uint8 | raw | limit prędkości |
| 4 | TurnLightState | uint8 | raw | kierunkowskazy |
| 4 | Unit | uint8 | raw | ustawienie jednostek |
| 5 | BreathState | uint8 | raw | stan efektu „breath” |
| 6 | CyclingState | uint8 | raw | stan jazdy/cykliczny |
| 7–8 | ColorLightR | uint16 BE | raw | kanał R; **parser odczytuje 16 bitów od B7** |
| 8–9 | ColorLightG | uint16 BE | raw | kanał G; **parser odczytuje 16 bitów od B8** |
| 9–10 | ColorLightB | uint16 BE | raw | kanał B; **parser odczytuje 16 bitów od B9** |
| 10–11 | CruiseState | uint16 BE | raw | stan tempomatu |
| 11–12 | LightLumince | uint16 BE | raw | jasność oświetlenia |
| 12–13 | LightModel | uint16 BE | raw | model/tryb oświetlenia |
| 13 | Shutdown | uint8 | raw | stan/parametr wyłączenia |
| 14 | — | nieużywany | — | brak odczytu |
| 15 | — | nieużywany | — | brak odczytu |
| 16–19 | — | brak w payloadzie parsera | — | **nie występują** |

## Istotne: nakładanie pól C9

Parser oryginalnej aplikacji **celowo lub przez konstrukcję protokołu** wykonuje trzy 16-bitowe odczyty z przesunięciem o jeden bajt:

- R = `uint16(B7,B8)`
- G = `uint16(B8,B9)`
- B = `uint16(B9,B10)`

Nie wolno tego „poprawiać” do klasycznego RGB 3 × uint8 bez dodatkowego potwierdzenia z rzeczywistych ramek.

---

# Tabela funkcjonalna dla nowej aplikacji

| Funkcja aplikacji | Charakterystyka | Bajty payload | Wartość |
|---|---|---|---|
| Napięcie baterii | C2 | 0–1 | uint16 BE / 100 V |
| Prędkość | C2 | 2–3 | uint16 BE / 100 |
| Przebieg całkowity | C2 | 4–7 | uint32 BE / 1000 km |
| Prąd/czas pracy | C2 | 8–9 | uint16 BE raw |
| Temperatura systemu | C2 | 10–11 | uint16 BE raw |
| Rozmiar koła | C2 | 13 | uint8 |
| Trip/przebieg bieżący | C5 | 0–3 | uint32 BE / 1000 km |
| Prędkość biegu | C5 | 4 | uint8 |
| Bieg | C5 | 5 | uint8 |
| Bateria | C5 | 6 | uint8 % |
| Maks. prędkość | C5 | 7 | uint8 |
| Błąd | C5 | 8–9 | uint16 BE |
| Czas wyłączenia | C5 | 10 | uint8 |
| Maks. biegi | C5 | 11 | uint8 |
| Siła | C5 | 12 | uint8 |
| Czułość | C5 | 13 | uint8 |
| Tempomat | C5 | 13 | uint8 |
| Tryb | C9 | 0 | uint8 |
| Żyroskop | C9 | 1 | uint8 |
| Blokada | C9 | 1 | uint8 |
| Reflektor | C9 | 2 | uint8 |
| Stan świateł | C9 | 2 | uint8 |
| Limit prędkości | C9 | 3 | uint8 |
| Kierunkowskazy | C9 | 4 | uint8 |
| Jednostki | C9 | 4 | uint8 |
| Efekt oddechu | C9 | 5 | uint8 |
| Stan jazdy | C9 | 6 | uint8 |
| RGB R | C9 | 7–8 | uint16 BE |
| RGB G | C9 | 8–9 | uint16 BE |
| RGB B | C9 | 9–10 | uint16 BE |
| Stan tempomatu | C9 | 10–11 | uint16 BE |
| Jasność | C9 | 11–12 | uint16 BE |
| Tryb oświetlenia | C9 | 12–13 | uint16 BE |
| Shutdown | C9 | 13 | uint8 |

# Źródła kodowe potwierdzające tabelę

1. `BTOTOParseImpl.smali` — funkcje `parseC2()`, `parseC5()`, `parseC9()`.
2. `ByteUtils.smali` — dokładna implementacja uint8/uint16 i kolejność big-endian.
3. `BTMeterWidget.smali` — prędkość /100.
4. `BTVltWidget.smali` — napięcie /100.
5. `BTOdoWidget.smali` i `BTTripWidget.smali` — przebieg /1000.
6. `BTBatteryWidget.smali` — `CellPer` jako procent baterii.

## Status

**POTWIERDZONE Z KODU:** rozmieszczenie bajtów, typy danych, big-endian, składanie pól 32-bitowych oraz przeliczenia prędkości/napięcia/przebiegu.

**NIEPOTWIERDZONE Z SAMEGO PARSERA:** fizyczne jednostki części pól konfiguracyjnych i stanowych, szczególnie `SysTemp`, `WorkingCurrent`, RGB oraz część parametrów C5/C9. Nowa aplikacja powinna zachować ich wartości surowe, dopóki nie zostaną potwierdzone przez rzeczywiste ramki i/lub kod ustawiający te parametry.

**WAŻNE:** nie wolno zmieniać kolejności bajtów ani „naprawiać” nakładających się pól C9 — tabela odwzorowuje dokładnie zachowanie oryginalnego parsera.
