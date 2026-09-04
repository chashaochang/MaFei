import { describe, expect, it } from '@ohos/hypium';
import {
  ManagementDeviceItem,
  ManagementDeviceQueryOptions
} from '../main/ets/features/management/devices/ManagementDeviceModels';
import {
  ManagementDeviceProtection
} from '../main/ets/features/management/devices/ManagementDeviceProtection';
import {
  ManagementDeviceQuery
} from '../main/ets/features/management/devices/ManagementDeviceQuery';

function device(id: string, current: boolean, name: string = id): ManagementDeviceItem {
  const item = new ManagementDeviceItem()
  item.id = id
  item.name = name
  item.displayName = name
  item.current = current
  return item
}

export default function managementDevicesTest() {
  describe('ManagementDevices', () => {
    it('excludesCurrentDeviceFromBatch', 0, () => {
      const ids = ManagementDeviceProtection.revocableIds([
        device('current', true),
        device('other', false)
      ], 'current')

      expect(ids.length).assertEqual(1)
      expect(ids[0]).assertEqual('other')
    })

    it('failsClosedWhenCurrentDeviceIdIsUnknown', 0, () => {
      const ids = ManagementDeviceProtection.revocableIds([
        device('one', false),
        device('two', false)
      ], '')
      expect(ids.length).assertEqual(0)
      let rejected = false
      try {
        ManagementDeviceProtection.assertNotCurrentDevice('one', '')
      } catch (_error) {
        rejected = true
      }
      expect(rejected).assertEqual(true)
    })

    it('clampsPageAfterFiltering', 0, () => {
      const devices: ManagementDeviceItem[] = []
      for (let index = 0; index < 26; index += 1) {
        devices.push(device('device-' + index, false, index === 0 ? 'only-one' : 'other'))
      }
      const options = new ManagementDeviceQueryOptions()
      options.pageIndex = 1
      options.pageSize = 25
      options.text = 'only-one'
      const result = ManagementDeviceQuery.apply(devices, options)

      expect(result.pageIndex).assertEqual(0)
      expect(result.items.length).assertEqual(1)
    })
  })
}
