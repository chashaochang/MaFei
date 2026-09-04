import { AxiosResponse } from '@ohos/axios';
import { PlaystateCommand, SessionInfoDto } from 'sdk/src/main/ets/generated-client/models';
import { getSessionApi } from 'sdk/src/main/ets/utils/api/session-api';
import { ApiClient } from '../../../network/ApiClient';

export class ManagementSessionApiService {
  async getSessions(): Promise<SessionInfoDto[]> {
    const response: AxiosResponse<SessionInfoDto[]> = await getSessionApi(ApiClient.Instance())
      .getSessions({ activeWithinSeconds: 960 })
    return response.data || []
  }

  async sendPlayPause(sessionId: string): Promise<void> {
    await getSessionApi(ApiClient.Instance()).sendPlaystateCommand({
      sessionId: sessionId,
      command: PlaystateCommand.PlayPause
    })
  }

  async sendStop(sessionId: string): Promise<void> {
    await getSessionApi(ApiClient.Instance()).sendPlaystateCommand({
      sessionId: sessionId,
      command: PlaystateCommand.Stop
    })
  }

  async sendMessage(sessionId: string, text: string): Promise<void> {
    await getSessionApi(ApiClient.Instance()).sendMessageCommand({
      sessionId: sessionId,
      messageCommand: { Text: text, TimeoutMs: 5000 }
    })
  }
}
