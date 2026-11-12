import { deviceInfo } from '@kit.BasicServicesKit';
import nativeApi from 'libentry.so';

const foregroundWindows: Set<string> = new Set<string>();
let backgroundTimer: number | undefined;

/** Switching between the browser and player must not background the shared runtime. */
export function setApplicationWindowForeground(name: string, foreground: boolean): void {
  if (foreground) foregroundWindows.add(name);
  else foregroundWindows.delete(name);
  if (backgroundTimer !== undefined) {
    clearTimeout(backgroundTimer);
    backgroundTimer = undefined;
  }
  if (foregroundWindows.size > 0) {
    nativeApi.SetOhosApplicationForeground(true);
  } else if (deviceInfo.deviceType !== '2in1') {
    nativeApi.SetOhosApplicationForeground(false);
  } else {
    backgroundTimer = setTimeout(() => {
      backgroundTimer = undefined;
      if (foregroundWindows.size === 0) nativeApi.SetOhosApplicationForeground(false);
    }, 100);
  }
}
