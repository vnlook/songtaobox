package com.vnlook.tvsongtao.data

import com.vnlook.tvsongtao.model.Playlist
import com.vnlook.tvsongtao.model.Video

object MockDataProvider {
    
    fun getMockVideos(): List<Video> {
        return listOf(
            Video(
                id = "video_01",
                name = "Quảng Cáo A",
                url = "https://www.sample-videos.com/video321/mp4/720/big_buck_bunny_720p_30mb.mp4",
                isDownloaded = false
            ),
            Video(
                id = "video_02",
                name = "Quảng Cáo B",
                url = "https://ledgiaodich.vienthongtayninh.vn:3030/assets/0a352f85-0d29-410c-84eb-a3768d546380.mp4",
                isDownloaded = false
            )
        )
    }

//    fun getMockPlaylists(): List<Playlist> {
//        return listOf(
//            Playlist(
//                id = "playlist_01",
//                startTime = "08:00",
//                endTime = "22:00",
//                videoIds = listOf("video_01", "video_02")
//            )
//        )
//    }
}
