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
