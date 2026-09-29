import { ArkUIViewController } from "compose/src/main/cpp/types/libcompose_arkui_utils";

export const MainArkUIViewController: (
  filesDirectory: string,
  bundleName: string,
  legacySnapshot: string,
  legacyCmpSettings: string
) => ArkUIViewController

export const UpdateOhosWindowInsets: (
  left: number,
  top: number,
  right: number,
  bottom: number
) => void

export const UpdateOhosWindowMetrics: (
  width: number,
  height: number,
  desktopHost: boolean,
  windowedHost: boolean
) => void

export const UpdateOhosWindowChrome: (
  windowControlEndInsetDp: number,
  windowControlTopInsetDp: number,
  windowFullscreen: boolean
) => void

export const UpdateOhosPlayerState: (payload: string) => boolean

export const IsOhosPlayerScreenVisible: () => boolean

export const IsOhosPlayerFullscreenRequested: () => boolean

export const GetOhosPlayerOrientationRevision: () => number

export const HandleOhosComposeBack: () => boolean

export const HandleOhosPlayerBack: () => boolean

export const HandleOhosPlayerKey: (keyCode: number, action: number) => boolean
