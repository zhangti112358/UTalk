# DiDi-Ride 工具列表

MCP 地址: https://mcp.didichuxing.com/mcp-servers-sandbox?key=***

工具数量: 13

## maps_direction_bicycling

描述: 根据用户输入的起点终点坐标，规划骑行通勤方案

输入 Schema:
```json
{
    "properties": {
        "destination": {
            "description": "终点坐标，格式为：经度,纬度",
            "type": "string"
        },
        "need_geo": {
            "description": "是否需要返回途经的点序列，默认值为true",
            "type": "boolean"
        },
        "origin": {
            "description": "起点坐标，格式为：经度,纬度",
            "type": "string"
        }
    },
    "required": [
        "origin",
        "destination"
    ],
    "type": "object"
}
```

## maps_direction_driving

描述: 根据用户起终点经纬度坐标规划以小客车、轿车通勤出行的方案

输入 Schema:
```json
{
    "properties": {
        "destination": {
            "description": "终点坐标，格式为：经度,纬度",
            "type": "string"
        },
        "need_geo": {
            "description": "是否需要返回途经的点序列，默认值为true",
            "type": "boolean"
        },
        "origin": {
            "description": "起点坐标，格式为：经度,纬度",
            "type": "string"
        }
    },
    "required": [
        "origin",
        "destination"
    ],
    "type": "object"
}
```

## maps_direction_transit

描述: 根据用户起终点坐标，规划综合公交、地铁的通勤方案

输入 Schema:
```json
{
    "properties": {
        "city": {
            "description": "查询城市",
            "type": "string"
        },
        "destination": {
            "description": "终点坐标，格式为：经度,纬度",
            "type": "string"
        },
        "origin": {
            "description": "起点坐标，格式为：经度,纬度",
            "type": "string"
        }
    },
    "required": [
        "origin",
        "destination",
        "city"
    ],
    "type": "object"
}
```

## maps_direction_walking

描述: 根据用户输入的起点终点坐标，规划步行通勤方案

输入 Schema:
```json
{
    "properties": {
        "destination": {
            "description": "终点坐标，格式为：经度,纬度",
            "type": "string"
        },
        "need_geo": {
            "description": "是否需要返回途经的点序列，默认值为true",
            "type": "boolean"
        },
        "origin": {
            "description": "起点坐标，格式为：经度,纬度",
            "type": "string"
        }
    },
    "required": [
        "origin",
        "destination"
    ],
    "type": "object"
}
```

## maps_place_around

描述: 根据用户传入关键词和位置坐标，搜索出周边的POI地点信息

输入 Schema:
```json
{
    "properties": {
        "keywords": {
            "description": "搜索关键词",
            "type": "string"
        },
        "location": {
            "description": "位置坐标，格式为：经度,纬度",
            "type": "string"
        },
        "max_distance": {
            "description": "搜索半径，单位：米",
            "type": "string"
        },
        "show_fields": {
            "description": "返回结果的扩展信息，传sub_poi_list:返回子点数据, 不传则不返回子点数据",
            "type": "string"
        }
    },
    "required": [
        "keywords",
        "location"
    ],
    "type": "object"
}
```

## maps_regeocode

描述: 将经纬度坐标转换为地址信息

输入 Schema:
```json
{
    "properties": {
        "location": {
            "description": "位置坐标，格式为：经度,纬度",
            "type": "string"
        }
    },
    "required": [
        "location"
    ],
    "type": "object"
}
```

## maps_textsearch

描述: 根据用户传入关键词和城市，搜索出相关的POI地点信息

输入 Schema:
```json
{
    "properties": {
        "city": {
            "description": "查询城市",
            "type": "string"
        },
        "keywords": {
            "description": "搜索关键词",
            "type": "string"
        },
        "location": {
            "description": "位置坐标，格式为：经度,纬度",
            "type": "string"
        },
        "show_fields": {
            "description": "返回结果的扩展信息，传sub_poi_list:返回子点数据, 不传则不返回子点数据",
            "type": "string"
        }
    },
    "required": [
        "keywords",
        "city"
    ],
    "type": "object"
}
```

## taxi_cancel_order

描述: 取消打车订单

输入 Schema:
```json
{
    "properties": {
        "order_id": {
            "description": "订单ID，从订单创建或查询结果中获取",
            "type": "string"
        },
        "reason": {
            "description": "取消原因，可选参数。例如：不需要了、等待时间太长、临时有事等",
            "type": "string"
        }
    },
    "required": [
        "order_id"
    ],
    "type": "object"
}
```

## taxi_create_order

描述: 直接通过API创建打车订单，无需打开任何应用程序界面，系统自动完成整个发单流程

输入 Schema:
```json
{
    "properties": {
        "caller_car_phone": {
            "description": "叫车人手机号，如果有就要传递，没有就不传",
            "type": "string"
        },
        "device_context": {
            "description": "设备上下文，包含brand和device_id字段，可选参数",
            "properties": {
                "brand": {
                    "description": "设备品牌，如 honor",
                    "type": "string"
                },
                "device_id": {
                    "description": "设备唯一标识",
                    "type": "string"
                }
            },
            "type": "object"
        },
        "estimate_trace_id": {
            "description": "预估流程ID，从预估结果中获取",
            "type": "string"
        },
        "product_category": {
            "description": "车型品类标识，从预估结果中获取，传入多个车型时，用英文逗号分割，不要带空格",
            "type": "string"
        },
        "tenant_extensions": {
            "description": "租户私有扩展字段集合，仅当前租户的注册字段会被解析，非注册字段静默丢弃。具体字段定义请参考租户对接文档。",
            "properties": {},
            "type": "object"
        }
    },
    "required": [
        "product_category",
        "estimate_trace_id"
    ],
    "type": "object"
}
```

## taxi_estimate

描述: 查看当前可用的网约车车型，请先获取对应地点的经纬度信息，如果有maps_textsearch的tool，优先使用maps_textsearch进行经纬度的获取。

输入 Schema:
```json
{
    "properties": {
        "from_lat": {
            "description": "出发纬度，必须从地图相关的工具获取，不能假设",
            "type": "string"
        },
        "from_lng": {
            "description": "出发经度，必须从地图相关的工具获取，不能假设",
            "type": "string"
        },
        "from_name": {
            "description": "出发地名称",
            "type": "string"
        },
        "to_lat": {
            "description": "目的纬度，必须从地图相关的工具获取，不能假设",
            "type": "string"
        },
        "to_lng": {
            "description": "目的经度，必须从地图相关的工具获取，不能假设",
            "type": "string"
        },
        "to_name": {
            "description": "目的地名称",
            "type": "string"
        }
    },
    "required": [
        "from_lng",
        "from_lat",
        "from_name",
        "to_lng",
        "to_lat",
        "to_name"
    ],
    "type": "object"
}
```

## taxi_generate_ride_app_link

描述: 根据起点、终点和车型生成打开移动应用或小程序的深度链接，用户点击后将跳转到相应的打车应用完成发单操作

输入 Schema:
```json
{
    "properties": {
        "from_lat": {
            "description": "出发纬度，必须从地图相关的工具获取，不能假设",
            "type": "string"
        },
        "from_lng": {
            "description": "出发经度，必须从地图相关的工具获取，不能假设",
            "type": "string"
        },
        "product_category": {
            "description": "车型品类标识列表，从预估结果中获取，支持多个车型，仅当用户明确指定某个或某些品类时才传递此参数，格式为英文逗号,分割",
            "type": "string"
        },
        "to_lat": {
            "description": "目的地纬度，必须从地图相关的工具获取，不能假设",
            "type": "string"
        },
        "to_lng": {
            "description": "目的经度，必须从地图相关的工具获取，不能假设",
            "type": "string"
        }
    },
    "required": [
        "from_lng",
        "from_lat",
        "to_lng",
        "to_lat"
    ],
    "type": "object"
}
```

## taxi_get_driver_location

描述: 获取打车订单对应司机的实时位置经纬度

输入 Schema:
```json
{
    "properties": {
        "order_id": {
            "description": "打车订单ID",
            "type": "string"
        }
    },
    "required": [
        "order_id"
    ],
    "type": "object"
}
```

## taxi_query_order

描述: 查询打车订单的状态和信息，如司机联系方式、车牌号、预估到达时间

输入 Schema:
```json
{
    "properties": {
        "order_id": {
            "description": "订单ID，从创建订单结果中获取，如果有就要传递，如果没有，会查询当前账号下未完成的订单",
            "type": "string"
        }
    },
    "type": "object"
}
```

