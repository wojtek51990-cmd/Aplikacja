# My Scooty PL — Android 16 / ARM64

Nowa natywna wersja aplikacji My Scooty przygotowana na Android 16 (API 36), bez starej biblioteki 32-bitowej.

## Zawiera

- cały interfejs użytkownika po polsku;
- natywne BLE GATT AB00 / AB01 / AB02;
- dekodowanie C2, C5 i C9 zgodne z oryginalnym parserem My Scooty 1.0.13;
- obsługę trybów, biegów, świateł, tempomatu, blokady, wyłączenia i resetu;
- działanie BLE w tle jako usługa foreground typu connectedDevice;
- automatyczne ponowne łączenie z ostatnią hulajnogą;
- ekran diagnostyczny z pełną ostatnią ramką;
- testy jednostkowe protokołu;
- tylko ABI arm64-v8a oraz małą bibliotekę JNI ARM64.

## BLE

Usługa: 0000AB00-0000-1000-8000-00805F9B34FB

Zapis: 0000AB01-0000-1000-8000-00805F9B34FB

Powiadomienia: 0000AB02-0000-1000-8000-00805F9B34FB

Komenda ma 8 bajtów:

`CC CMD VALUE 00 00 00 CHECKSUM FE`

CHECKSUM = CC XOR CMD XOR VALUE

Oryginalna aplikacja wysyła każdą komendę 12 razy.

## Ramki odbiorcze

Dla 20-bajtowej ramki oryginalny parser bierze typ z bajtu 16 i payload z bajtów 2..15. Oznacza to 14-bajtowy payload.

Pola uint16 i uint32 są dekodowane big-endian (MSB first).

Dystans jest prezentowany tak jak w oryginalnych widgetach: raw / 1000 × 0,85, a przy MP/H dodatkowo × 0,621371192237.

Pola bez potwierdzonej przez oryginalny kod jednostki są pozostawione jako surowe.

## Budowanie

Workflow GitHub Actions wykonuje testy, buduje debug i release oraz sprawdza obecność biblioteki lib/arm64-v8a/libmyscooty_native.so w APK.
