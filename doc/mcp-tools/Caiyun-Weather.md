# Caiyun-Weather 工具列表

MCP 地址: https://mcp-weather.caiyunapp.com/mcp

工具数量: 5

## get_realtime_weather

描述: Get the realtime weather for a location.

输入 Schema:
```json
{
    "properties": {
        "lat": {
            "description": "The latitude of the location to get the weather for",
            "title": "Lat",
            "type": "number"
        },
        "lng": {
            "description": "The longitude of the location to get the weather for",
            "title": "Lng",
            "type": "number"
        }
    },
    "required": [
        "lng",
        "lat"
    ],
    "type": "object"
}
```

## get_hourly_forecast

描述: Get hourly weather forecast for the requested number of hours.

输入 Schema:
```json
{
    "properties": {
        "hours": {
            "default": 72,
            "description": "The number of hourly forecast steps to return",
            "maximum": 360,
            "minimum": 1,
            "title": "Hours",
            "type": "integer"
        },
        "lat": {
            "description": "The latitude of the location to get the weather for",
            "title": "Lat",
            "type": "number"
        },
        "lng": {
            "description": "The longitude of the location to get the weather for",
            "title": "Lng",
            "type": "number"
        }
    },
    "required": [
        "lng",
        "lat"
    ],
    "type": "object"
}
```

## get_weekly_forecast

描述: Get daily weather forecast for up to 7 days (free tier returns 3 days).

输入 Schema:
```json
{
    "properties": {
        "lat": {
            "description": "The latitude of the location to get the weather for",
            "title": "Lat",
            "type": "number"
        },
        "lng": {
            "description": "The longitude of the location to get the weather for",
            "title": "Lng",
            "type": "number"
        }
    },
    "required": [
        "lng",
        "lat"
    ],
    "type": "object"
}
```

## get_historical_weather

描述: Get historical weather data for the past 24 hours.

输入 Schema:
```json
{
    "properties": {
        "lat": {
            "description": "The latitude of the location to get the weather for",
            "title": "Lat",
            "type": "number"
        },
        "lng": {
            "description": "The longitude of the location to get the weather for",
            "title": "Lng",
            "type": "number"
        }
    },
    "required": [
        "lng",
        "lat"
    ],
    "type": "object"
}
```

## get_weather_alerts

描述: Get weather alerts for the location.

输入 Schema:
```json
{
    "properties": {
        "lat": {
            "description": "The latitude of the location to get the weather for",
            "title": "Lat",
            "type": "number"
        },
        "lng": {
            "description": "The longitude of the location to get the weather for",
            "title": "Lng",
            "type": "number"
        }
    },
    "required": [
        "lng",
        "lat"
    ],
    "type": "object"
}
```

