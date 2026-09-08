# VariFlight-Aviation 工具列表

MCP 地址: https://ai.variflight.com/servers/aviation/mcp?api_key=***

工具数量: 9

## searchFlightsByDepArr

描述: Search direct flights by departure and arrival location plus date. Use depcity and arrcity when the user specifies cities such as BJS or SHA. Use dep and arr when the user specifies exact airports such as PEK or PVG. Provide one departure field and one arrival field, and do not mix city and airport codes for the same side. All codes must be valid IATA 3-letter codes. Date must be in YYYY-MM-DD format. For today's date, use getTodayDate instead of hardcoding.

输入 Schema:
```json
{
    "properties": {
        "date": {
            "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If the user input contains only month and day, use getTodayDate to determine the year. For today's date, use getTodayDate instead of hardcoding.",
            "title": "Date",
            "type": "string"
        },
        "dep": {
            "anyOf": [
                {
                    "description": "Departure airport IATA 3-letter code (e.g. PEK for Beijing, CAN for Guangzhou)",
                    "type": "string"
                },
                {
                    "type": "null"
                }
            ],
            "default": null,
            "title": "Dep"
        },
        "depcity": {
            "anyOf": [
                {
                    "description": "Departure city IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
                    "type": "string"
                },
                {
                    "type": "null"
                }
            ],
            "default": null,
            "title": "Depcity"
        },
        "arr": {
            "anyOf": [
                {
                    "description": "Arrival airport IATA 3-letter code (e.g. SHA for Shanghai, HFE for Hefei)",
                    "type": "string"
                },
                {
                    "type": "null"
                }
            ],
            "default": null,
            "title": "Arr"
        },
        "arrcity": {
            "anyOf": [
                {
                    "description": "Arrival city IATA 3-letter code (e.g. SHA for Shanghai, BJS for Beijing)",
                    "type": "string"
                },
                {
                    "type": "null"
                }
            ],
            "default": null,
            "title": "Arrcity"
        }
    },
    "required": [
        "date"
    ],
    "type": "object"
}
```

## searchFlightsByNumber

描述: Search a flight by flight number and date. The flight number must include the airline code, for example MU2157 or CZ3969. dep and arr are optional and should only be provided when the exact airports are known. Date must be in YYYY-MM-DD format. For today's date, use getTodayDate instead of hardcoding.

输入 Schema:
```json
{
    "properties": {
        "fnum": {
            "description": "Flight number including airline code (e.g. MU2157, CZ3969)",
            "title": "Fnum",
            "type": "string"
        },
        "date": {
            "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If the user input contains only month and day, use getTodayDate to determine the year. For today's date, use getTodayDate instead of hardcoding.",
            "title": "Date",
            "type": "string"
        },
        "dep": {
            "anyOf": [
                {
                    "description": "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
                    "type": "string"
                },
                {
                    "type": "null"
                }
            ],
            "default": null,
            "title": "Dep"
        },
        "arr": {
            "anyOf": [
                {
                    "description": "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
                    "type": "string"
                },
                {
                    "type": "null"
                }
            ],
            "default": null,
            "title": "Arr"
        }
    },
    "required": [
        "fnum",
        "date"
    ],
    "type": "object"
}
```

## getFlightTransferInfo

描述: Search connecting flight options between a departure city and an arrival city on a specific date. Use city IATA 3-letter codes such as BJS, SHA, or LAX. This tool is for transfer or connection itineraries between cities, not for airport weather, airport facilities, or realtime tracking. Date must be in YYYY-MM-DD format. For today's date, use getTodayDate instead of hardcoding.

输入 Schema:
```json
{
    "properties": {
        "depdate": {
            "description": "Departure date in YYYY-MM-DD format. IMPORTANT: If the user input contains only month and day, use getTodayDate to determine the year. For today's date, use getTodayDate instead of hardcoding.",
            "title": "Depdate",
            "type": "string"
        },
        "depcity": {
            "description": "Departure city IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
            "title": "Depcity",
            "type": "string"
        },
        "arrcity": {
            "description": "Arrival city IATA 3-letter code (e.g. SHA for Shanghai, LAX for Los Angeles)",
            "title": "Arrcity",
            "type": "string"
        }
    },
    "required": [
        "depdate",
        "depcity",
        "arrcity"
    ],
    "type": "object"
}
```

## flightHappinessIndex

描述: Use this tool when the user wants comfort-focused details for a known flight, such as punctuality, aircraft type, cabin configuration, seat comfort, meals, entertainment, or other onboard experience details. This tool works best when a specific flight number and date are already known. Do not use it for fare search, itinerary recommendation, or raw price comparison.

输入 Schema:
```json
{
    "properties": {
        "fnum": {
            "description": "Flight number including airline code (e.g. MU2157, CZ3969)",
            "title": "Fnum",
            "type": "string"
        },
        "date": {
            "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If the user input contains only month and day, use getTodayDate to determine the year. For today's date, use getTodayDate instead of hardcoding.",
            "title": "Date",
            "type": "string"
        },
        "dep": {
            "anyOf": [
                {
                    "description": "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
                    "type": "string"
                },
                {
                    "type": "null"
                }
            ],
            "default": null,
            "title": "Dep"
        },
        "arr": {
            "anyOf": [
                {
                    "description": "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
                    "type": "string"
                },
                {
                    "type": "null"
                }
            ],
            "default": null,
            "title": "Arr"
        }
    },
    "required": [
        "fnum",
        "date"
    ],
    "type": "object"
}
```

## getRealtimeLocationByAnum

描述: Get realtime flight location by aircraft registration number, also called tail number, such as B2021 or B2022. Use this only when the aircraft registration number is known. If the registration number is unknown, first try to find the flight through searchFlightsByNumber and then use the aircraft registration number from that result.

输入 Schema:
```json
{
    "properties": {
        "anum": {
            "description": "Aircraft registration number, also called tail number, such as B2021, B2022, or B2023.",
            "title": "Anum",
            "type": "string"
        }
    },
    "required": [
        "anum"
    ],
    "type": "object"
}
```

## getTodayDate

描述: Get today's date in local timezone (YYYY-MM-DD format). Use this tool whenever you need today's date - NEVER hardcode dates.

输入 Schema:
```json
{
    "properties": {
        "random_string": {
            "anyOf": [
                {
                    "description": "Optional placeholder parameter. Normally omit it.",
                    "type": "string"
                },
                {
                    "type": "null"
                }
            ],
            "default": null,
            "title": "Random String"
        }
    },
    "type": "object"
}
```

## getFutureWeatherByAirport

描述: Get the 3-day airport weather forecast for today, tomorrow, and the day after tomorrow by airport IATA 3-letter code, such as PEK, SHA, CAN, or HFE.

输入 Schema:
```json
{
    "properties": {
        "airport": {
            "description": "Airport IATA 3-letter code (e.g. PEK for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
            "title": "Airport",
            "type": "string"
        }
    },
    "required": [
        "airport"
    ],
    "type": "object"
}
```

## searchFlightItineraries

描述: Use this tool when the user wants a concise recommended itinerary summary in natural language. It returns a text-style result that summarizes how many sale flights match, the overall lowest price, the shortest duration, and several recommended options. Use city IATA 3-letter codes only. If the user instead needs structured raw flight pricing data with each flight and cabin price, use getFlightPriceByCities.

输入 Schema:
```json
{
    "properties": {
        "depCityCode": {
            "description": "Departure city 3-letter code (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
            "title": "Depcitycode",
            "type": "string"
        },
        "depDate": {
            "description": "Departure date in YYYY-MM-DD format, for example 2025-07-04. IMPORTANT: If the user input contains only month and day, use getTodayDate to determine the year. For today's date, use getTodayDate instead of hardcoding.",
            "title": "Depdate",
            "type": "string"
        },
        "arrCityCode": {
            "description": "Arrival city 3-letter code (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
            "title": "Arrcitycode",
            "type": "string"
        }
    },
    "required": [
        "depCityCode",
        "depDate",
        "arrCityCode"
    ],
    "type": "object"
}
```

## getFlightPriceByCities

描述: Use this tool when the user needs structured raw pricing data. It returns sale flights between two cities as a list of flights, and each flight contains cabin entries with prices. This is better than searchFlightItineraries when the caller needs per-flight, per-cabin price details instead of a natural-language recommendation summary. All city codes must be valid IATA 3-letter codes (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei).

输入 Schema:
```json
{
    "properties": {
        "dep_city": {
            "description": "Departure city 3-letter code (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
            "title": "Dep City",
            "type": "string"
        },
        "arr_city": {
            "description": "Arrival city 3-letter code (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
            "title": "Arr City",
            "type": "string"
        },
        "dep_date": {
            "description": "Departure date in YYYY-MM-DD format, for example 2026-04-20. IMPORTANT: If the user input contains only month and day, use getTodayDate to determine the year. For today's date, use getTodayDate instead of hardcoding.",
            "title": "Dep Date",
            "type": "string"
        }
    },
    "required": [
        "dep_city",
        "arr_city",
        "dep_date"
    ],
    "type": "object"
}
```

