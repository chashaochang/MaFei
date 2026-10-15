import { AppStorageV2 } from '@kit.ArkUI'

@ObservedV2
export class MusicHostUIState {
  @Trace homeVisible: boolean = false
  @Trace loginVisible: boolean = false
  @Trace videoPlayerVisible: boolean = false
  @Trace livePlayerVisible: boolean = false
  @Trace musicPlayerVisible: boolean = false
  @Trace photoViewerVisible: boolean = false
}

export function connectMusicHostUIState(): MusicHostUIState {
  return AppStorageV2.connect(MusicHostUIState, () => new MusicHostUIState())!
}
