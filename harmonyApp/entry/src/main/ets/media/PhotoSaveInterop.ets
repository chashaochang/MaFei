import { common } from '@kit.AbilityKit';
import { request } from '@kit.BasicServicesKit';
import { fileIo, fileUri } from '@kit.CoreFileKit';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import nativeApi from 'libentry.so';

interface PhotoSavePayload {
  id: number;
  url: string;
  fileName: string;
  headers: Record<string, string>;
}

let hostGeneration: number = 0;

async function saveOriginal(context: common.UIAbilityContext, payload: PhotoSavePayload, generation: number): Promise<number> {
  const extension = payload.fileName.includes('.') ?
    payload.fileName.split('.').pop()?.toLowerCase().replace(/[^a-z0-9]/g, '') || 'jpg' : 'jpg';
  const safeExtension = extension.length <= 16 ? extension : 'jpg';
  const path = `${context.cacheDir}/mafei-photo-${Date.now()}-${payload.id}.${safeExtension}`;
  let task: request.DownloadTask | undefined = undefined;
  let helper: photoAccessHelper.PhotoAccessHelper | undefined = undefined;
  try {
    task = await request.downloadFile(context, { url: payload.url, filePath: path, header: payload.headers });
    const downloadTask = task;
    await new Promise<void>((resolve, reject) => {
      let settled = false;
      const finish = (successful: boolean): void => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        downloadTask.off('complete', completed);
        downloadTask.off('fail', failed);
        if (successful) resolve(); else reject(new Error('Photo download failed'));
      };
      const completed = (): void => finish(true);
      const failed = (_error: number): void => finish(false);
      const timer = setTimeout(() => finish(false), 240000);
      downloadTask.on('complete', completed);
      downloadTask.on('fail', failed);
      // A small file can finish before these listeners are installed.
      downloadTask.getTaskInfo().then((info: request.DownloadInfo): void => {
        if (info.status === request.SESSION_SUCCESSFUL) finish(true);
        else if (info.status === request.SESSION_FAILED) finish(false);
      }).catch((): void => finish(false));
    });
    if (generation !== hostGeneration) return 1;
    if (fileIo.statSync(path).size <= 0) return 2;
    helper = photoAccessHelper.getPhotoAccessHelper(context);
    const creation: photoAccessHelper.PhotoCreationConfig = {
      fileNameExtension: safeExtension,
      photoType: photoAccessHelper.PhotoType.IMAGE
    };
    // CMP cannot use ArkUI's secure SaveButton. This documented system dialog
    // grants permission for the exact destination without broad photo access.
    const destinations = await helper.showAssetsCreationDialog([fileUri.getUriFromPath(path)], [creation]);
    if (generation !== hostGeneration || destinations.length === 0) return 1;
    const destination = fileIo.openSync(destinations[0], fileIo.OpenMode.WRITE_ONLY);
    try { fileIo.copyFileSync(path, destination.fd); } finally { fileIo.closeSync(destination); }
    return 0;
  } catch (_) { return 2; }
  finally {
    if (task) { try { await task.delete(); } catch (_) {} }
    if (helper) { try { await helper.release(); } catch (_) {} }
    try { fileIo.unlinkSync(path); } catch (_) {}
  }
}

export function registerPhotoSaveInterop(context: common.UIAbilityContext): void {
  const generation = ++hostGeneration;
  nativeApi.RegisterOhosPhotoSave((raw: string): void => {
    try {
      const payload = JSON.parse(raw) as PhotoSavePayload;
      saveOriginal(context, payload, generation).then((result: number) => nativeApi.CompleteOhosPhotoSave(payload.id, result));
    } catch (_) { /* Native save timeout handles a malformed host request. */ }
  });
}

export function releasePhotoSaveInterop(): void {
  hostGeneration++;
  nativeApi.ReleaseOhosPhotoSave();
}
