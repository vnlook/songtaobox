# Repository Pattern Implementation

## Overview

This folder contains the repository layer implementation for the Standee Ads application. The repository pattern provides a clean separation between data sources and the rest of the application, making the codebase more maintainable and testable.

## Structure

- `PlaylistRepository.kt` - Interface defining the contract for playlist operations
- `PlaylistRepositoryImpl.kt` - Implementation of the PlaylistRepository interface

## Usage

The repository layer is used by the UseCase layer, which in turn is used by the UI layer (Activities, Fragments, ViewModels). This follows the MVVM architecture pattern.

### Example Usage

```kotlin
// In a ViewModel or UseCase
private val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(context)

// Get playlists
val playlists = playlistRepository.getPlaylists()

// Load playlists from a file
val playlists = playlistRepository.loadPlaylistsFromFile("/path/to/file.json")

// Load playlists from a URI
val playlists = playlistRepository.loadPlaylistsFromUri(uri)

// Load playlists from default location
val playlists = playlistRepository.loadPlaylistsFromDefaultLocation()
```

## Benefits of Repository Pattern

1. **Separation of Concerns**: The repository pattern separates data access logic from business logic
2. **Testability**: Makes it easier to write unit tests by allowing mock implementations
3. **Flexibility**: Allows changing the data source without affecting the rest of the application
4. **Centralized Data Access**: Provides a single point for data access policies
5. **Maintainability**: Makes the codebase more maintainable by isolating data access code
