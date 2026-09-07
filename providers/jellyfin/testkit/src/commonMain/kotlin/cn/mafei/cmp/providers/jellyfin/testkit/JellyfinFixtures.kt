package cn.mafei.cmp.providers.jellyfin.testkit

/**
 * Sanitized Jellyfin JSON fixtures in the real PascalCase wire format
 * (Jellyfin 10.10.7 OpenAPI, official jellyfin-sdk-kotlin v1.6.8). The only
 * lowerCamel payload is ProblemDetails. All ids, names and tokens are
 * synthetic; never paste real server data in here.
 */
data object JellyfinFixtures {

    const val BASE_URL: String = "https://jellyfin.test"

    const val PUBLIC_SYSTEM_INFO_10_9: String = """{
        "LocalAddress": "http://192.0.2.10:8096",
        "ServerName": "TestServer",
        "Version": "10.9.11",
        "ProductName": "Jellyfin Server",
        "Id": "0123456789abcdef0123456789abcdef",
        "StartupWizardCompleted": true
    }"""

    const val PUBLIC_SYSTEM_INFO_TOO_OLD: String = """{
        "ServerName": "OldServer",
        "Version": "10.7.7",
        "Id": "fedcba9876543210fedcba9876543210",
        "StartupWizardCompleted": true
    }"""

    const val PUBLIC_USERS: String = """[
        {"Name": "demo", "Id": "a1b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8", "PrimaryImageTag": "1112131415161718", "HasPassword": true},
        {"Name": "kid", "Id": "b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8a1", "HasPassword": false},
        {"Name": "guest", "Id": "c3d4e5f6a7b8a1b2c3d4e5f6a7b8a1b2"}
    ]"""

    const val AUTHENTICATION_RESULT: String = """{
        "User": {
            "Name": "demo",
            "ServerId": "0123456789abcdef0123456789abcdef",
            "Id": "a1b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8",
            "HasPassword": true,
            "PrimaryImageTag": "1112131415161718"
        },
        "SessionInfo": {
            "Id": "session-0001",
            "UserId": "a1b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8",
            "UserName": "demo",
            "Client": "MaFei",
            "DeviceId": "device-1"
        },
        "AccessToken": "fixture-access-token-0123456789abcdef",
        "ServerId": "0123456789abcdef0123456789abcdef"
    }"""

    /** Views with an unknown future CollectionType that must be tolerated. */
    const val VIEWS: String = """{
        "Items": [
            {"Name": "Movies", "Id": "v0102030405060708", "Type": "CollectionFolder", "CollectionType": "movies", "ChildCount": 42},
            {"Name": "TV", "Id": "v0908070605040302", "Type": "CollectionFolder", "CollectionType": "tvshows"},
            {"Name": "Mixed", "Id": "v11011121314151617", "Type": "UserView", "CollectionType": "HolographicLibrary", "SomeFutureField": {"deep": true}}
        ],
        "TotalRecordCount": 3,
        "StartIndex": 0
    }"""

    /** Item page mixing known types, an unknown item type, and unknown fields. */
    const val ITEMS_PAGE: String = """{
        "Items": [
            {"Name": "Fixture Movie", "Id": "m0102030405060708", "Type": "Movie", "ProductionYear": 2024, "CommunityRating": 8.1, "RunTimeTicks": 72000000000, "UserData": {"IsFavorite": true, "PlaybackPositionTicks": 0}},
            {"Name": "Fixture Series", "Id": "s0102030405060708", "Type": "Series", "ProductionYear": 2023, "Genres": ["Drama"], "SomeVendorAddition": [1, 2, 3]},
            {"Name": "Hologram Show", "Id": "h0102030405060708", "Type": "Hologram"}
        ],
        "TotalRecordCount": 3,
        "StartIndex": 0
    }"""

    /** Series detail with people and a premiere date in ISO format with offset. */
    const val SERIES_DETAIL: String = """{
        "Name": "Fixture Series",
        "Id": "s0102030405060708",
        "Type": "Series",
        "Overview": "A fixture series used in protocol tests.",
        "PremiereDate": "2023-04-01T20:00:00-05:00",
        "ProductionYear": 2023,
        "Genres": ["Drama"],
        "People": [
            {"Name": "Ada Director", "Role": "Director", "Type": "Director"},
            {"Name": "Bob Actor", "Role": "Protagonist", "Type": "Actor", "PrimaryImageTag": "abcd000000000000"}
        ],
        "ImageTags": {"Primary": "1111111122222222"}
    }"""

    const val SEASONS: String = """{
        "Items": [
            {"Name": "Season 1", "Id": "se01020304050607", "Type": "Season", "IndexNumber": 1, "SeriesId": "s0102030405060708"},
            {"Name": "Season 2", "Id": "se02030405060708", "Type": "Season", "IndexNumber": 2, "SeriesId": "s0102030405060708"}
        ],
        "TotalRecordCount": 2,
        "StartIndex": 0
    }"""

    const val EPISODES: String = """{
        "Items": [
            {"Name": "Pilot", "Id": "e0102030405060708", "Type": "Episode", "SeasonId": "se01020304050607", "SeriesId": "s0102030405060708", "IndexNumber": 1, "ParentIndexNumber": 1, "RunTimeTicks": 15000000000},
            {"Name": "Episode Two", "Id": "e0203040506070809", "Type": "Episode", "SeasonId": "se01020304050607", "IndexNumber": 2, "ParentIndexNumber": 1}
        ],
        "TotalRecordCount": 2,
        "StartIndex": 0
    }"""

    /**
     * PlaybackInfo: one direct file source and one transcoding source, with
     * an external SRT subtitle carrying DeliveryUrl/DeliveryMethod, plus a
     * live stream id on the direct source.
     */
    const val PLAYBACK_INFO: String = """{
        "MediaSources": [
            {
                "Id": "ms0102030405060708",
                "Path": "/media/movies/fixture.mkv",
                "Protocol": "File",
                "Container": "mkv",
                "LiveStreamId": "live-0001",
                "RequiresOpening": false,
                "SupportsDirectPlay": true,
                "SupportsDirectStream": true,
                "SupportsTranscoding": true,
                "RunTimeTicks": 72000000000,
                "DefaultAudioStreamIndex": 1,
                "DefaultSubtitleStreamIndex": 3,
                "MediaStreams": [
                    {"Index": 0, "Type": "Video", "Codec": "hevc", "Width": 3840, "Height": 2160, "AverageFrameRate": 23.976, "DisplayTitle": "4K HEVC"},
                    {"Index": 1, "Type": "Audio", "Codec": "aac", "Language": "eng", "Channels": 6, "IsDefault": true, "DisplayTitle": "English AAC 5.1"},
                    {"Index": 2, "Type": "Audio", "Codec": "aac", "Language": "chi", "Channels": 2, "DisplayTitle": "Chinese AAC 2.0"},
                    {"Index": 3, "Type": "Subtitle", "Codec": "srt", "Language": "eng", "IsExternal": true, "IsTextSubtitleStream": true, "SupportsExternalStream": true, "IsDefault": false, "DisplayTitle": "English SRT", "DeliveryUrl": "/Videos/m0102030405060708/Subtitles/3/0/Stream.srt", "DeliveryMethod": "External"}
                ]
            },
            {
                "Id": "ms0203040506070809",
                "Protocol": "Http",
                "Container": "ts",
                "SupportsDirectPlay": false,
                "SupportsDirectStream": false,
                "SupportsTranscoding": true,
                "TranscodingUrl": "/videos/m0102030405060708/master.m3u8?VideoCodec=h264&AudioCodec=aac&PlaySessionId=session-0001",
                "TranscodingSubProtocol": "hls",
                "TranscodingContainer": "ts"
            }
        ],
        "PlaySessionId": "session-0001"
    }"""

    const val PLAYBACK_INFO_ERROR: String = """{"MediaSources": [], "PlaySessionId": "session-0001", "ErrorCode": "NoCompatibleStream"}"""

    const val PROBLEM_DETAILS_401: String = """{
        "type": "https://tools.ietf.org/html/rfc7235#section-3.1",
        "title": "Unauthorized",
        "status": 401,
        "detail": "Invalid token.",
        "instance": "/System/Info"
    }"""

    const val SYSTEM_INFO: String = """{
        "ServerName": "TestServer",
        "Version": "10.9.11",
        "Id": "0123456789abcdef0123456789abcdef",
        "OperatingSystem": "Linux"
    }"""
}
