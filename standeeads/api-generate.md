# API Generate
- Repository
- API: Create Play Device
    - POST /items/play_device
## Curl API
curl -X POST "https://ledgiaodich.vienthongtayninh.vn:3030/items/play_device" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '[{"device_id":"tvbox3","device_name":"tvbox02","location":"Tay Ninh","active":true,"mapLocation":"POINT (106.7906368997206 10.804248069799328)"}]' \



## Sample Response
```json
{
  "data": [
    {
      "id": 9,
      "device_id": "tvbox3",
      "device_name": "tvbox02",
      "location": "Tay Ninh",
      "active": true,
      "mapLocation": "POINT (106.7906368997206 10.804248069799328)"
    }
  ]
}

```

# API Generate
- Repository
- API: Update Play Device
    - PATCH /items/play_device
## Curl API
curl -X PATCH "https://ledgiaodich.vienthongtayninh.vn:3030/items/play_device/9" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"id":9,"device_name":"tvbox03","location":"Tay Ninh","active":true,"mapLocation":"POINT (106.7906368997206 10.804248069799329)"}' \




## Sample Response
```json
{
  "data": {
    "id": 9,
    "device_id": "tvbox3",
    "device_name": "tvbox03",
    "location": "Tay Ninh",
    "active": true,
    "mapLocation": "POINT (106.7906368997206 10.804248069799329)"
  }
}

```
