import { describe, expect, it } from '@ohos/hypium';
import { SessionInfoDto, TranscodingInfo } from 'sdk/src/main/ets/generated-client/models';
import {
  ManagementPlaybackKind,
  ManagementSessionPresentation
} from '../main/ets/features/management/sessions/ManagementSessionModels';
import {
  ManagementSessionMapper
} from '../main/ets/features/management/sessions/ManagementSessionMapper';

function fakeSession(id: string, lastActivityDate: string, hasMedia: boolean,
  userId: string): SessionInfoDto {
  const session: SessionInfoDto = {
    Id: id,
    LastActivityDate: lastActivityDate,
    UserId: userId
  }
  if (hasMedia) {
    session.NowPlayingItem = { Id: 'item-' + id, Name: id }
  }
  return session
}

export default function managementSessionsTest() {
  describe('ManagementSessions', () => {
    it('keepsRecentIdleUserSessions', 0, () => {
      const now = Date.parse('2026-07-30T10:00:00Z')
      const sessions = ManagementSessionMapper.filterRecent([
        fakeSession('idle', '2026-07-30T09:59:00Z', false, 'user-1'),
        fakeSession('old', '2026-07-30T09:57:00Z', true, 'user-1')
      ], now)

      expect(sessions.length).assertEqual(1)
      expect(sessions[0].Id).assertEqual('idle')
    })

    it('mapsRemuxBeforeDirectStream', 0, () => {
      const transcoding: TranscodingInfo = {
        IsVideoDirect: true,
        IsAudioDirect: true
      }
      expect(ManagementSessionMapper.playbackKind(transcoding, 'Transcode'))
        .assertEqual(ManagementPlaybackKind.Remux)
    })

    it('mapsSessionArtworkUrl', 0, () => {
      const mapped = ManagementSessionMapper.map(
        fakeSession('artwork', '2026-07-30T09:59:00Z', true, 'user-1'))

      expect(mapped.imageItemId).assertEqual('item-artwork')
      expect(mapped.imageUrl.indexOf('/Items/item-artwork/Images/Primary') >= 0).assertTrue()
    })

    it('localizesKnownAndUnknownTranscodeReasons', 0, () => {
      expect(ManagementSessionPresentation.transcodeReasonResourceName(
        'VideoCodecNotSupported'))
        .assertEqual('management_session_transcode_reason_video')
      expect(ManagementSessionPresentation.transcodeReasonResourceName(
        'FutureServerReason'))
        .assertEqual('management_session_transcode_reason_other')
    })

    it('keepsInvalidActivityDateAsSafeFallback', 0, () => {
      expect(ManagementSessionPresentation.formatActivityDate('not-a-date')).assertEqual('not-a-date')
    })
  })
}
