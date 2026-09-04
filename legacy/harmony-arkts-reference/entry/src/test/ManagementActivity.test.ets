import { describe, expect, it } from '@ohos/hypium';
import { ManagementErrorCode } from '../main/ets/features/management/ManagementModels';
import {
  ManagementActivityView
} from '../main/ets/features/management/activity/ManagementActivityModels';
import {
  ManagementActivityQuery
} from '../main/ets/features/management/activity/ManagementActivityQuery';
import { ManagementActivityRepository } from '../main/ets/features/management/activity/ManagementActivityRepository';

class ActivityHttpResponseStub {
  status: number = 0
}

class ActivityHttpErrorStub {
  response: ActivityHttpResponseStub = new ActivityHttpResponseStub()
}

export default function managementActivityTest() {
  describe('ManagementActivity', () => {
    it('mapsViewsToHasUserId', 0, () => {
      expect(ManagementActivityQuery.hasUserId(ManagementActivityView.All) === undefined).assertTrue()
      expect(ManagementActivityQuery.hasUserId(ManagementActivityView.User)).assertTrue()
      expect(ManagementActivityQuery.hasUserId(ManagementActivityView.System)).assertFalse()
    })

    it('usesServerTotalForPageCount', 0, () => {
      expect(ManagementActivityQuery.pageCount(51, 25)).assertEqual(3)
    })

    it('distinguishesMissingMediaFromOtherFailures', 0, () => {
      const repository = new ManagementActivityRepository()
      const notFoundError = new ActivityHttpErrorStub()
      notFoundError.response.status = 404
      expect(repository.classifyError(notFoundError as Object))
        .assertEqual(ManagementErrorCode.Unsupported)
      const serverError = new ActivityHttpErrorStub()
      serverError.response.status = 500
      expect(repository.classifyError(serverError as Object))
        .assertEqual(ManagementErrorCode.RequestFailed)
    })
  })
}
