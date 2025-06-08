# API Generate
- Repository
- API:
    - GET /items/changelog
## Curl API
curl -X GET "https://songtao.vnlook.com/items/changelog?limit=1&sort=-date_created" \
 -H "accept: application/json" \


## Sample Response
```json
{
  "data": [
    {
      "id": 22,
      "date_created": "2025-06-08T10:08:38.861Z",
      "date_updated": null,
      "log": "New Update from playlist and video"
    }
  ]
}

```
