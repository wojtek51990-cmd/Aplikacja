# My Scooty 1.0.13 — pełna tabela odbiorcza C2 / C5 / C9

Źródło: zdekompilowany parser BTOTOParseImpl.smali.

## Ramka

Oryginalny parser wymaga minimum 20 bajtów.

Dla ramki 20-bajtowej:
- typ = bajt 16, czyli packet[length - 4];
- payload = packet[2 until 16], czyli oryginalne bajty 2..15;
- payload ma 14 bajtów.

## Kolejność

uint8 = data[n] & 0xFF

uint16 BE = ((data[n] & 0xFF) << 8) | (data[n+1] & 0xFF)

uint32 BE = (uint16BE[n] << 16) | uint16BE[n+2]

## C2

| Payload | Parametr | Odczyt | Przeliczenie |
|---:|---|---|---|
| 0–1 | Vlt | uint16 BE | /100 V |
| 2–3 | Speed | uint16 BE | /100 |
| 4–7 | MileageTotal | uint32 BE | /1000, następnie ×0,85 w UI |
| 8–9 | WorkingCurrent | uint16 BE | raw |
| 10–11 | SysTemp | uint16 BE | raw |
| 12 | — | — | nieużywany |
| 13 | WhellSize | uint8 | raw |

## C5

| Payload | Parametr | Odczyt | Przeliczenie |
|---:|---|---|---|
| 0–3 | MileageCurrent | uint32 BE | /1000, następnie ×0,85 w UI |
| 4 | GearSpeed | uint8 | raw |
| 5 | Gear | uint8 | raw |
| 6 | CellPer | uint8 | % baterii |
| 7 | MaxSpeed | uint8 | raw |
| 8–9 | Malfunction | uint16 BE | raw |
| 10 | CloseTime | uint8 | raw |
| 11 | MaxGears | uint8 | raw |
| 12 | Strength | uint8 | raw |
| 13 | Sensitivity | uint8 | raw; ten sam bajt jest źródłem Cruise |

## C9

| Payload | Parametr | Odczyt | Przeliczenie |
|---:|---|---|---|
| 0 | Mode | uint8 | raw |
| 1 | Gyro / Lock | uint8 | raw |
| 2 | Headlight / LightState | uint8 | raw |
| 3 | SpeedLimit | uint8 | raw |
| 4 | TurnLightState / Unit | uint8 | raw |
| 5 | BreathState | uint8 | raw |
| 6 | CyclingState | uint8 | raw |
| 7–8 | ColorLightR | uint16 BE | raw |
| 8–9 | ColorLightG | uint16 BE | raw |
| 9–10 | ColorLightB | uint16 BE | raw |
| 10–11 | CruiseState | uint16 BE | raw |
| 11–12 | LightLumince | uint16 BE | raw |
| 12–13 | LightModel | uint16 BE | raw |
| 13 | Shutdown | uint8 | raw |

Uwaga: pola RGB są celowo nakładającymi się odczytami uint16. Tak działa oryginalny parser.
