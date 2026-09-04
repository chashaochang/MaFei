import { AxiosInstance, AxiosResponse } from '@ohos/axios';
import { DeviceInfoDto } from 'sdk/src/main/ets/generated-client/models';
import { ApiClient } from '../../../network/ApiClient';
import { ManagementDeviceProtection } from './ManagementDeviceProtection';

export class ManagementDeviceApiService {
  async getDevices(): Promise<DeviceInfoDto[]> {
    const api = ApiClient.Instance()
    const response: AxiosResponse<DeviceInfoDto[]> = await this.axios().get<DeviceInfoDto[]>(
      `${api.basePath}/Devices`, { headers: { Authorization: api.authorizationHeader } })
    return response.data || []
  }

  async updateName(deviceId: string, customName: string): Promise<void> {
    ManagementDeviceProtection.assertDeviceId(deviceId)
    const api = ApiClient.Instance()
    await this.axios().post<void>(`${api.basePath}/Devices/Options`,
      { CustomName: customName }, {
        params: { id: deviceId },
        headers: { Authorization: api.authorizationHeader }
      })
  }

  async revoke(deviceId: string, currentDeviceId: string): Promise<void> {
    ManagementDeviceProtection.assertNotCurrentDevice(deviceId, currentDeviceId)
    const api = ApiClient.Instance()
    await this.axios().delete<void>(`${api.basePath}/Devices`, {
      params: { id: deviceId },
      headers: { Authorization: api.authorizationHeader }
    })
  }

  private axios(): AxiosInstance {
    return ApiClient.Instance().axiosInstance as AxiosInstance
  }
}
