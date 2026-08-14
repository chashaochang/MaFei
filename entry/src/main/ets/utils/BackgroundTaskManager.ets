/*
 * Copyright (c) 2024 Huawei Device Co., Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { common, wantAgent } from '@kit.AbilityKit';
import { backgroundTaskManager } from '@kit.BackgroundTasksKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';

const TAG = '[BackgroundTaskManager]';

export enum BackgroundTaskOwner {
  Video = 'video',
  Music = 'music'
}

export class BackgroundTaskOwnerPolicy {
  private owners: Set<string> = new Set<string>()

  acquire(owner: string): boolean {
    const normalizedOwner = owner.trim()
    if (normalizedOwner.length === 0 || this.owners.has(normalizedOwner)) {
      return false
    }
    const shouldStart = this.owners.size === 0
    this.owners.add(normalizedOwner)
    return shouldStart
  }

  release(owner: string): boolean {
    const normalizedOwner = owner.trim()
    if (normalizedOwner.length === 0 || !this.owners.delete(normalizedOwner)) {
      return false
    }
    return this.owners.size === 0
  }

  hasOwners(): boolean {
    return this.owners.size > 0
  }
}

/**
 * Background task tool class.
 */
export class BackgroundTaskManager {
  private static ownerPolicy: BackgroundTaskOwnerPolicy = new BackgroundTaskOwnerPolicy()
  private static operationChain: Promise<void> = Promise.resolve()
  private static systemRunning: boolean = false
  private static systemContext?: common.UIAbilityContext

  public static startContinuousTask(context?: common.UIAbilityContext,
    owner: string = BackgroundTaskOwner.Video): void {
    BackgroundTaskManager.acquireContinuousTask(context, owner).catch((): void => {})
  }

  /**
   * Cancel continuous task
   */
  public static stopContinuousTask(context?: common.UIAbilityContext,
    owner: string = BackgroundTaskOwner.Video): void {
    BackgroundTaskManager.releaseContinuousTask(context, owner).catch((): void => {})
  }

  public static acquireContinuousTask(context?: common.UIAbilityContext,
    owner: string = BackgroundTaskOwner.Music): Promise<void> {
    if (!context || owner.trim().length === 0) {
      return Promise.resolve()
    }
    BackgroundTaskManager.ownerPolicy.acquire(owner)
    BackgroundTaskManager.systemContext = context
    return BackgroundTaskManager.enqueueReconcile()
  }

  public static releaseContinuousTask(context?: common.UIAbilityContext,
    owner: string = BackgroundTaskOwner.Music): Promise<void> {
    if (!context || owner.trim().length === 0) {
      return Promise.resolve()
    }
    BackgroundTaskManager.systemContext = context
    BackgroundTaskManager.ownerPolicy.release(owner)
    return BackgroundTaskManager.enqueueReconcile()
  }

  private static enqueueReconcile(): Promise<void> {
    const operation = BackgroundTaskManager.operationChain.then(async (): Promise<void> => {
      await BackgroundTaskManager.reconcile()
    })
    BackgroundTaskManager.operationChain = operation.catch((err: BusinessError): void => {
      hilog.error(0x0000, TAG, `continuous task reconciliation failed, cause: ${JSON.stringify(err)}`)
    })
    return operation
  }

  private static async reconcile(): Promise<void> {
    if (BackgroundTaskManager.ownerPolicy.hasOwners()) {
      if (!BackgroundTaskManager.systemRunning && BackgroundTaskManager.systemContext) {
        await BackgroundTaskManager.startSystemTask(BackgroundTaskManager.systemContext)
        BackgroundTaskManager.systemRunning = true
      }
      return
    }

    if (BackgroundTaskManager.systemRunning && BackgroundTaskManager.systemContext) {
      await BackgroundTaskManager.stopSystemTask(BackgroundTaskManager.systemContext)
      BackgroundTaskManager.systemRunning = false
    }
    if (!BackgroundTaskManager.systemRunning) {
      BackgroundTaskManager.systemContext = undefined
    }
  }

  private static async startSystemTask(context: common.UIAbilityContext): Promise<void> {
    const wantAgentInfo: wantAgent.WantAgentInfo = {
      wants: [
        {
          bundleName: context.abilityInfo.bundleName,
          abilityName: context.abilityInfo.name
        }
      ],
      operationType: wantAgent.OperationType.START_ABILITY,
      requestCode: 0,
      wantAgentFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
    };
    const wantAgentObj = await wantAgent.getWantAgent(wantAgentInfo)
    await backgroundTaskManager.startBackgroundRunning(context,
      backgroundTaskManager.BackgroundMode.AUDIO_PLAYBACK, wantAgentObj)
    hilog.info(0x0000, TAG, 'startBackgroundRunning succeeded')
  }

  private static async stopSystemTask(context: common.UIAbilityContext): Promise<void> {
    await backgroundTaskManager.stopBackgroundRunning(context)
    hilog.info(0x0000, TAG, 'stopBackgroundRunning succeeded')
  }
}
