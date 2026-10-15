package cn.mafei.cmp.feature.photo

import kotlin.test.*

class PhotoExifPresentationTest {
    @Test fun infoShowsLocalCaptureTimeWhileCatalogKeepsItsOriginalGroupingDay() {
        val photo = cn.mafei.cmp.core.domain.PhotoInfo(fileName = "photo.jpg", dateKey = "2026-10-09",
            fullImage = cn.mafei.cmp.core.domain.MediaImage(url = "https://nas/photo"),
            dateTaken = "2026-10-09T00:30:00+08:00", dateTakenEpochMs = 12345L)
        assertEquals("2026-10-08 17:30", photoDateText(photo) { epoch ->
            assertEquals(12345L, epoch); "2026-10-08 17:30"
        })
        assertEquals("2026-10-09", photo.dateKey)
        assertEquals(photo.dateTaken, photoDateText(photo) { "" })
        assertNull(photoDateText(photo.copy(dateTaken = null, dateTakenEpochMs = null, dateKey = "unknown")))
    }
    @Test fun legacyPrecisionAndOrientationAreRetained() {
        assertEquals("1.20", exifFixed(1.2, 2)); assertEquals("35.0", exifFixed(35.0, 1))
        assertEquals("-1.23457", exifFixed(-1.234567, 5))
        assertEquals("90° · 镜像", photoOrientationLabel("Transpose"))
        assertEquals("270°", photoOrientationLabel("LeftBottom"))
        assertEquals("正常", photoOrientationLabel("TopLeft")); assertNull(photoOrientationLabel(null))
    }
}
