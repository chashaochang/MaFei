import { HMLifecycle, HMLifecycleContext, IHMLifecycle } from '@hadss/hmrouter'
import { connectMusicHostUIState, MusicHostUIState } from './MusicHostUIState'

@HMLifecycle({ lifecycleName: 'musicHomeLifecycle' })
export class MusicHomeLifecycle implements IHMLifecycle {
  private state: MusicHostUIState = connectMusicHostUIState()

  onShown(_ctx: HMLifecycleContext): void {
    this.state.homeVisible = true
  }

  onHidden(_ctx: HMLifecycleContext): void {
    this.state.homeVisible = false
  }
}

@HMLifecycle({ lifecycleName: 'musicLoginLifecycle' })
export class MusicLoginLifecycle implements IHMLifecycle {
  private state: MusicHostUIState = connectMusicHostUIState()

  onShown(_ctx: HMLifecycleContext): void {
    this.state.loginVisible = true
  }

  onHidden(_ctx: HMLifecycleContext): void {
    this.state.loginVisible = false
  }
}

@HMLifecycle({ lifecycleName: 'musicPlayerLifecycle' })
export class MusicPlayerLifecycle implements IHMLifecycle {
  private state: MusicHostUIState = connectMusicHostUIState()

  onShown(_ctx: HMLifecycleContext): void {
    this.state.musicPlayerVisible = true
  }

  onHidden(_ctx: HMLifecycleContext): void {
    this.state.musicPlayerVisible = false
  }
}

@HMLifecycle({ lifecycleName: 'photoViewerLifecycle' })
export class PhotoViewerLifecycle implements IHMLifecycle {
  private state: MusicHostUIState = connectMusicHostUIState()

  onShown(_ctx: HMLifecycleContext): void {
    this.state.photoViewerVisible = true
  }

  onHidden(_ctx: HMLifecycleContext): void {
    this.state.photoViewerVisible = false
  }
}
