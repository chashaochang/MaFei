import { FnConnectApi } from "./FnConnectApi";
import { FnosHostResolver } from "./FnosHostResolver";
import { SessionStore } from "./SessionStore";

export class FnosRequestHostResolver {
  static async resolveHttpBaseUrls(host: string): Promise<string[]> {
    const candidates: string[] = [];
    FnosRequestHostResolver.appendHttpBaseUrl(candidates, host);

    const originalInput = SessionStore.currentHostInput().trim();
    if (!FnosHostResolver.isFnConnectLike(originalInput)) {
      return candidates;
    }

    try {
      const resolved = await FnConnectApi.resolveFnTargets(originalInput);
      const preferredCandidates: string[] = candidates.slice();
      FnosRequestHostResolver.appendHttpBaseUrl(preferredCandidates, resolved.preferredBaseUrl);
      resolved.fallbackBaseUrls.forEach((candidate: string) => {
        FnosRequestHostResolver.appendHttpBaseUrl(preferredCandidates, candidate);
      });
      return preferredCandidates;
    } catch (_) {
      return candidates;
    }
  }

  private static appendHttpBaseUrl(targets: string[], candidate: string): void {
    const normalized = FnosHostResolver.normalizeHttpBaseUrl(candidate, SessionStore.getSnapshot().httpsAccess).trim();
    if (normalized.length === 0) {
      return;
    }
    if (targets.indexOf(normalized) >= 0) {
      return;
    }
    targets.push(normalized);
  }
}
