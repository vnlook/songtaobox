# API Documentation: Loading Playlists from Files

## Overview

This API provides functionality to load playlists from JSON files in various locations. It follows the MVVM pattern with a repository layer and integrates with the existing data management system. The repository pattern provides better separation of concerns and makes the code more maintainable.

The API now supports parsing VNL-formatted API responses with the prefix 'VNL' in the data model classes. This allows for seamless integration with the VNL backend API.

## API Methods

### DataUseCase

```kotlin
// Load playlists from a file (supports both simple and VNL API formats)
fun loadPlaylistsFromFile(filePath: String): List<Playlist>

// Load playlists from a content URI (supports both simple and VNL API formats)
fun loadPlaylistsFromUri(uri: Uri): List<Playlist>

// Load playlists from default location in app's external files directory
fun loadPlaylistsFromDefaultLocation(fileName: String = "vnl_api_response.json"): List<Playlist>
```

### PlaylistRepository

```kotlin
// Get all playlists from SharedPreferences
fun getPlaylists(): List<Playlist>

// Save playlists to SharedPreferences
fun savePlaylists(playlists: List<Playlist>)

// Load playlists from a file and save to SharedPreferences
fun loadPlaylistsFromFile(filePath: String): List<Playlist>

// Load playlists from a content URI and save to SharedPreferences
fun loadPlaylistsFromUri(uri: Uri): List<Playlist>

// Load playlists from default location in app's external files directory
fun loadPlaylistsFromDefaultLocation(fileName: String = "vnl_api_response.json"): List<Playlist>

// Get playlists and videos from VNL API response file
fun getPlaylistsAndVideosFromFile(filePath: String): Pair<List<Playlist>, List<Video>>

// Get playlists and videos from VNL API response URI
fun getPlaylistsAndVideosFromUri(uri: Uri): Pair<List<Playlist>, List<Video>>
```

## File Format

The API now supports two JSON formats:

### 1. Simple Playlist Format

```json
[
  {
    "id": "playlist_01",
    "startTime": "08:00",
    "endTime": "22:00",
    "videoIds": ["video_01", "video_02"]
  },
  {
    "id": "playlist_02",
    "startTime": "22:00",
    "endTime": "08:00",
    "videoIds": ["video_03", "video_04"]
  }
]
```

### 2. VNL API Response Format

```json
{
  "data": [
    {
      "id": 1,
      "title": "Quảng cáo buổi sáng",
      "active": true,
      "order": 1,
      "beginTime": "06:00:00",
      "endTime": "12:00:00",
      "assets": [
        {
          "media_assets_id": {
            "title": "Video Quảng cáo số 1",
            "fileUrl": "https://songtao.vnlook.com/assets",
            "file": {
              "filename_disk": "0a352f85-0d29-410c-84eb-a3768d546380.mp4",
              "id": "video_01",
              "filename_download": "SampleVideo_1280x720_30mb.mp4"
            }
          }
        },
        {
          "media_assets_id": {
            "title": "Video Quảng cáo số 2",
            "fileUrl": "https://songtao.vnlook.com/assets",
            "file": {
              "filename_disk": "1b463f96-1e3a-521d-95fc-b4879e657491.mp4",
              "id": "video_02",
              "filename_download": "SampleVideo_720p_10mb.mp4"
            }
          }
        }
      ]
    }
  ]
}
```

## Curl API
curl --location --request GET 'https://songtao.vnlook.com/items/media_playlist?fields=id,title,active,order,beginTime,endTime,assets.media_assets_id.title,assets.media_assets_id.fileUrl,assets.media_assets_id.file.filename_disk,assets.media_assets_id.file.id,media_assets_id.type,assets.media_assets_id.file.filename_download' \
--header 'User-Agent: Apidog/1.0.0 (https://apidog.com)' \
--header 'Accept: */*' \
--header 'Host: songtao.vnlook.com' \
--header 'Connection: keep-alive'

## VNL API Response Parsing

The API now includes a `VNLApiResponseParser` class that handles parsing the VNL API response format. This parser maps the API response to the app's internal data models:

### Mapping Rules

1. **Playlist Mapping**:
   - `id` (integer) → Playlist.id (string)
   - `beginTime` (HH:MM:SS) → Playlist.startTime (HH:MM)
   - `endTime` (HH:MM:SS) → Playlist.endTime (HH:MM)
   - `assets[].media_assets_id.file.id` → Playlist.videoIds (list)

2. **Video Mapping**:
   - `assets[].media_assets_id.file.id` → Video.id
   - `assets[].media_assets_id.title` → Video.name
   - `assets[].media_assets_id.fileUrl` + `/` + `assets[].media_assets_id.file.filename_disk` → Video.url

### Example of Parsed Data

From the VNL API response:
```json
{
  "data": [{
    "id": 1,
    "beginTime": "06:00:00",
    "endTime": "12:00:00",
    "assets": [{
      "media_assets_id": {
        "title": "Video Quảng cáo số 1",
        "fileUrl": "https://songtao.vnlook.com/assets",
        "file": {
          "filename_disk": "0a352f85-0d29-410c-84eb-a3768d546380.mp4",
          "id": "video_01"
        }
      }
    }]
  }]
}
```

To the app's models:
```kotlin
// Playlist
Playlist(
    id = "1",
    startTime = "06:00",
    endTime = "12:00",
    videoIds = listOf("video_01")
)

// Video
Video(
    id = "video_01",
    name = "Video Quảng cáo số 1",
    url = "https://songtao.vnlook.com/assets/0a352f85-0d29-410c-84eb-a3768d546380.mp4",
    isDownloaded = false
)
```

## Usage Examples

### Loading Playlists from a VNL API Response File

```kotlin
val dataUseCase = DataUseCase(context)
dataUseCase.initialize()

try {
    val playlists = dataUseCase.loadPlaylistsFromFile("/path/to/vnl_api_response.json")
    // Use playlists
} catch (e: Exception) {
    Log.e("MyApp", "Error loading playlists: ${e.message}")
}
```

### Loading Playlists from a Content URI (File Picker)

```kotlin
// In your Activity or Fragment
private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let {
        try {
            val playlists = dataUseCase.loadPlaylistsFromUri(it)
            // Use playlists
        } catch (e: Exception) {
            Log.e("MyApp", "Error loading playlists: ${e.message}")
        }
    }
}

// Launch file picker
fun openFilePicker() {
    openFileLauncher.launch(arrayOf("application/json"))
}
```

### Loading Playlists from Default Location

```kotlin
try {
    val playlists = dataUseCase.loadPlaylistsFromDefaultLocation()
    // Use playlists
} catch (e: Exception) {
    Log.e("MyApp", "Error loading playlists: ${e.message}")
}
```

## Response Example
```json
{
    "data": [
        {
            "id": 2,
            "title": "Quảng cáo buổi sáng",
            "active": true,
            "order": 1,
            "beginTime": "06:00:00",
            "endTime": "12:00:00",
            "assets": [
                {
                    "media_assets_id": {
                        "title": "Video Quảng cáo số 1",
                        "fileUrl": "https://songtao.vnlook.com/assets",
                        "file": {
                            "filename_disk": "0a352f85-0d29-410c-84eb-a3768d546380.mp4",
                            "id": "0a352f85-0d29-410c-84eb-a3768d546380",
                            "filename_download": "SampleVideo_1280x720_30mb.mp4"
                        }
                    }
                },
                {
                    "media_assets_id": {
                        "title": "Video Quảng cáo số 2",
                        "fileUrl": "https://songtao.vnlook.com/assets",
                        "file": {
                            "filename_disk": "0a352f85-0d29-410c-84eb-a3768d546380.mp4",
                            "id": "0a352f85-0d29-410c-84eb-a3768d546380",
                            "filename_download": "SampleVideo_1280x720_30mb.mp4"
                        }
                    }
                },
                {
                    "media_assets_id": {
                        "title": "Video Quảng cáo số 3",
                        "fileUrl": "https://songtao.vnlook.com/assets",
                        "file": {
                            "filename_disk": "0a352f85-0d29-410c-84eb-a3768d546380.mp4",
                            "id": "0a352f85-0d29-410c-84eb-a3768d546380",
                            "filename_download": "SampleVideo_1280x720_30mb.mp4"
                        }
                    }
                }
            ]
        },
        {
            "id": 3,
            "title": "Quảng cáo buổi chiều",
            "active": true,
            "order": 1,
            "beginTime": "13:00:00",
            "endTime": "17:00:00",
            "assets": [
                {
                    "media_assets_id": {
                        "title": "Video Quảng cáo số 4",
                        "fileUrl": "https://songtao.vnlook.com/assets",
                        "file": {
                            "filename_disk": "0a352f85-0d29-410c-84eb-a3768d546380.mp4",
                            "id": "0a352f85-0d29-410c-84eb-a3768d546380",
                            "filename_download": "SampleVideo_1280x720_30mb.mp4"
                        }
                    }
                },
                {
                    "media_assets_id": {
                        "title": "Video Quảng cáo số 5",
                        "fileUrl": "https://songtao.vnlook.com/assets",
                        "file": {
                            "filename_disk": "0a352f85-0d29-410c-84eb-a3768d546380.mp4",
                            "id": "0a352f85-0d29-410c-84eb-a3768d546380",
                            "filename_download": "SampleVideo_1280x720_30mb.mp4"
                        }
                    }
                }
            ]
        },
        {
            "id": 4,
            "title": "Quảng cáo buổi tối",
            "active": true,
            "order": 1,
            "beginTime": "19:00:00",
            "endTime": "21:00:00",
            "assets": [
                {
                    "media_assets_id": {
                        "title": "Video Quảng cáo số 6",
                        "fileUrl": "https://songtao.vnlook.com/assets",
                        "file": {
                            "filename_disk": "0a352f85-0d29-410c-84eb-a3768d546380.mp4",
                            "id": "0a352f85-0d29-410c-84eb-a3768d546380",
                            "filename_download": "SampleVideo_1280x720_30mb.mp4"
                        }
                    }
                },
                {
                    "media_assets_id": {
                        "title": "Video Quảng cáo số 7",
                        "fileUrl": "https://songtao.vnlook.com/assets",
                        "file": {
                            "filename_disk": "0a352f85-0d29-410c-84eb-a3768d546380.mp4",
                            "id": "0a352f85-0d29-410c-84eb-a3768d546380",
                            "filename_download": "SampleVideo_1280x720_30mb.mp4"
                        }
                    }
                }
            ]
        }
    ]
}

```
