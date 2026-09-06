package cn.mafei.cmp.providers.jellyfin.testkit

/**
 * Sanitized Jellyfin JSON fixtures for protocol tests. All ids, names and
 * tokens are synthetic; never paste real server data in here.
 */
data object JellyfinFixtures {

    const val BASE_URL: String = "https://jellyfin.test"

    const val PUBLIC_SYSTEM_INFO_10_9: String = """{
        "localAddress": "http://192.0.2.10:8096",
        "serverName": "TestServer",
        "version": "10.9.11",
        "productName": "Jellyfin Server",
        "id": "0123456789abcdef0123456789abcdef",
        "startupWizardCompleted": true
    }"""

    const val PUBLIC_SYSTEM_INFO_TOO_OLD: String = """{
        "serverName": "OldServer",
        "version": "10.7.7",
        "id": "fedcba9876543210fedcba9876543210",
        "startupWizardCompleted": true
    }"""

    const val PUBLIC_USERS: String = """[
        {"name": "demo", "id": "a1b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8", "primaryImageTag": "1112131415161718", "hasPassword": true},
        {"name": "kid", "id": "b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8a1", "hasPassword": false},
        {"name": "guest", "id": "c3d4e5f6a7b8a1b2c3d4e5f6a7b8a1b2"}
    ]"""

    const val AUTHENTICATION_RESULT: String = """{
        "user": {
            "name": "demo",
            "serverId": "0123456789abcdef0123456789abcdef",
            "id": "a1b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8",
            "hasPassword": true,
            "primaryImageTag": "1112131415161718"
        },
        "accessToken": "fixture-access-token-0123456789abcdef",
        "serverId": "0123456789abcdef0123456789abcdef",
        "sessionId": "session-0001"
    }"""

    /** Views with an unknown future CollectionType that must be tolerated. */
    const val VIEWS: String = """{
        "items": [
            {"name": "Movies", "id": "v0102030405060708", "type": "CollectionFolder", "collectionType": "movies", "childCount": 42},
            {"name": "TV", "id": "v0908070605040302", "type": "CollectionFolder", "collectionType": "tvshows"},
            {"name": "Mixed", "id": "v11011121314151617", "type": "UserView", "collectionType": "HolographicLibrary", "someFutureField": {"deep": true}}
        ],
        "totalRecordCount": 3,
        "startIndex": 0
    }"""

    /** Item page mixing known types, an unknown item type, and unknown fields. */
    const val ITEMS_PAGE: String = """{
        "items": [
            {"name": "Fixture Movie", "id": "m0102030405060708", "type": "Movie", "productionYear": 2024, "communityRating": 8.1, "runTimeTicks": 72000000000, "userData": {"isFavorite": true, "playbackPositionTicks": 0}},
            {"name": "Fixture Series", "id": "s0102030405060708", "type": "Series", "productionYear": 2023, "genres": ["Drama"], "someVendorAddition": [1, 2, 3]},
            {"name": "Hologram Show", "id": "h0102030405060708", "type": "Hologram"}
        ],
        "totalRecordCount": 3,
        "startIndex": 0
    }"""

    /** Series detail with people and a premiere date in ISO format with offset. */
    const val SERIES_DETAIL: String = """{
        "name": "Fixture Series",
        "id": "s0102030405060708",
        "type": "Series",
        "overview": "A fixture series used in protocol tests.",
        "premiereDate": "2023-04-01T20:00:00-05:00",
        "productionYear": 2023,
        "genres": ["Drama"],
        "people": [
            {"name": "Ada Director", "role": "Director", "type": "Director"},
            {"name": "Bob Actor", "role": "Protagonist", "type": "Actor", "primaryImageTag": "abcd000000000000"}
        ],
        "imageTags": {"Primary": "1111111122222222"}
    }"""

    const val SEASONS: String = """{
        "items": [
            {"name": "Season 1", "id": "se01020304050607", "type": "Season", "indexNumber": 1, "seriesId": "s0102030405060708"},
            {"name": "Season 2", "id": "se02030405060708", "type": "Season", "indexNumber": 2, "seriesId": "s0102030405060708"}
        ],
        "totalRecordCount": 2,
        "startIndex": 0
    }"""

    const val EPISODES: String = """{
        "items": [
            {"name": "Pilot", "id": "e0102030405060708", "type": "Episode", "seasonId": "se01020304050607", "seriesId": "s0102030405060708", "indexNumber": 1, "parentIndexNumber": 1, "runTimeTicks": 15000000000},
            {"name": "Episode Two", "id": "e0203040506070809", "type": "Episode", "seasonId": "se01020304050607", "indexNumber": 2, "parentIndexNumber": 1}
        ],
        "totalRecordCount": 2,
        "startIndex": 0
    }"""

    /** PlaybackInfo: one direct file source and one transcoding source, with tracks. */
    const val PLAYBACK_INFO: String = """{
        "mediaSources": [
            {
                "id": "ms0102030405060708",
                "path": "/media/movies/fixture.mkv",
                "protocol": "File",
                "container": "mkv",
                "supportsDirectPlay": true,
                "supportsDirectStream": true,
                "supportsTranscoding": true,
                "runTimeTicks": 72000000000,
                "defaultAudioStreamIndex": 1,
                "defaultSubtitleStreamIndex": 3,
                "mediaStreams": [
                    {"index": 0, "type": "Video", "codec": "hevc", "width": 3840, "height": 2160, "averageFrameRate": 23.976, "displayTitle": "4K HEVC"},
                    {"index": 1, "type": "Audio", "codec": "aac", "language": "eng", "channels": 6, "isDefault": true, "displayTitle": "English AAC 5.1"},
                    {"index": 2, "type": "Audio", "codec": "aac", "language": "chi", "channels": 2, "displayTitle": "Chinese AAC 2.0"},
                    {"index": 3, "type": "Subtitle", "codec": "srt", "language": "eng", "isExternal": true, "isDefault": false, "displayTitle": "English SRT"}
                ]
            },
            {
                "id": "ms0203040506070809",
                "protocol": "Http",
                "container": "ts",
                "supportsDirectPlay": false,
                "supportsDirectStream": false,
                "supportsTranscoding": true,
                "transcodingUrl": "/videos/m0102030405060708/master.m3u8?VideoCodec=h264&AudioCodec=aac&PlaySessionId=session-0001",
                "transcodingSubProtocol": "hls",
                "transcodingContainer": "ts"
            }
        ],
        "playSessionId": "session-0001"
    }"""

    const val PLAYBACK_INFO_ERROR: String = """{"mediaSources": [], "playSessionId": "session-0001", "errorCode": "NoCompatibleStream"}"""

    const val PROBLEM_DETAILS_401: String = """{
        "type": "https://tools.ietf.org/html/rfc7235#section-3.1",
        "title": "Unauthorized",
        "status": 401,
        "detail": "Invalid token.",
        "instance": "/System/Info"
    }"""

    const val SYSTEM_INFO: String = """{
        "serverName": "TestServer",
        "version": "10.9.11",
        "id": "0123456789abcdef0123456789abcdef",
        "operatingSystem": "Linux"
    }"""
}
