export class FnosHostResolver {
  static normalizeHttpBaseUrl(input: string, preferHttps: boolean = false): string {
    const trimmed = input.trim().replace(/\/$/, "");
    if (trimmed.length === 0) {
      return "";
    }

    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      return FnosHostResolver.normalizePrefixedHttpBaseUrl(trimmed, preferHttps);
    }

    if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) {
      const httpScheme = trimmed.startsWith("wss://") ? "https://" : "http://";
      return FnosHostResolver.normalizePrefixedHttpBaseUrl(
        `${httpScheme}${trimmed.replace(/^wss?:\/\//, "")}`,
        preferHttps
      );
    }

    if (FnosHostResolver.isFnId(trimmed)) {
      return `https://5ddd.com/${trimmed}`;
    }

    if (FnosHostResolver.isRelayPath(trimmed)) {
      return `https://${trimmed}`;
    }

    if (FnosHostResolver.isRelayDomain(trimmed)) {
      return `https://${trimmed}`;
    }

    const scheme = preferHttps ? "https://" : "http://";
    if (FnosHostResolver.hasExplicitPort(trimmed)) {
      return `${scheme}${trimmed}`;
    }
    return `${scheme}${trimmed}:${preferHttps ? "5667" : "5666"}`;
  }

  static normalizeWebSocketUrl(input: string, preferHttps: boolean = false): string {
    const trimmed = input.trim().replace(/\/$/, "");
    if (trimmed.length === 0) {
      return "";
    }

    if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) {
      return FnosHostResolver.appendWebSocketPath(trimmed);
    }

    const httpBaseUrl = FnosHostResolver.normalizeHttpBaseUrl(trimmed, preferHttps);
    if (httpBaseUrl.length === 0) {
      return "";
    }
    const wsScheme = httpBaseUrl.startsWith("https://") ? "wss://" : "ws://";
    return FnosHostResolver.appendWebSocketPath(
      `${wsScheme}${httpBaseUrl.replace(/^https?:\/\//, "")}`
    );
  }

  static isFnConnectLike(input: string): boolean {
    const trimmed = input.trim();
    if (trimmed.length === 0) {
      return false;
    }
    if (FnosHostResolver.isFnId(trimmed)) {
      return true;
    }
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")
      || trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) {
      const withoutScheme = trimmed.replace(/^[a-z]+:\/\//i, "");
      return FnosHostResolver.isRelayPath(withoutScheme) || FnosHostResolver.isRelayDomain(withoutScheme);
    }
    return FnosHostResolver.isRelayPath(trimmed) || FnosHostResolver.isRelayDomain(trimmed);
  }

  static applyRelayCookie(headers: Record<string, string>, input: string): void {
    if (!FnosHostResolver.requiresRelayCookie(input)) {
      return;
    }
    headers["Cookie"] = FnosHostResolver.mergeCookieHeader(headers["Cookie"] || "", "mode=relay");
  }

  static mergeCookieHeader(existing: string, cookiePair: string): string {
    const normalizedExisting = existing.trim();
    if (normalizedExisting.length === 0) {
      return cookiePair;
    }
    const cookieMap: Map<string, string> = new Map<string, string>();
    normalizedExisting.split(";").forEach((segment: string) => {
      const trimmed = segment.trim();
      if (trimmed.length === 0) {
        return;
      }
      const separatorIndex = trimmed.indexOf("=");
      if (separatorIndex <= 0) {
        return;
      }
      cookieMap.set(trimmed.substring(0, separatorIndex).trim(), trimmed.substring(separatorIndex + 1).trim());
    });
    const relaySeparator = cookiePair.indexOf("=");
    if (relaySeparator > 0) {
      cookieMap.set(cookiePair.substring(0, relaySeparator), cookiePair.substring(relaySeparator + 1));
    }
    let merged = "";
    cookieMap.forEach((value: string, key: string) => {
      merged = merged.length === 0 ? `${key}=${value}` : `${merged}; ${key}=${value}`;
    });
    return merged;
  }

  private static normalizePrefixedHttpBaseUrl(input: string, preferHttps: boolean): string {
    const scheme = input.startsWith("https://") || (preferHttps && !input.startsWith("http://"))
      ? "https://"
      : "http://";
    const withoutScheme = input.replace(/^https?:\/\//, "").replace(/\/$/, "");
    if (FnosHostResolver.isRelayPath(withoutScheme) || FnosHostResolver.isRelayDomain(withoutScheme)) {
      return `${scheme === "http://" ? "https://" : scheme}${withoutScheme}`;
    }
    if (FnosHostResolver.hasExplicitPort(withoutScheme)) {
      return `${scheme}${withoutScheme}`;
    }
    return `${scheme}${withoutScheme}:${scheme === "https://" ? "5667" : "5666"}`;
  }

  private static requiresRelayCookie(input: string): boolean {
    const trimmed = input.trim();
    if (trimmed.length === 0) {
      return false;
    }
    const withoutScheme = trimmed.replace(/^[a-z]+:\/\//i, "");
    return FnosHostResolver.isRelayPath(withoutScheme) || FnosHostResolver.isRelayDomain(withoutScheme);
  }

  private static appendWebSocketPath(base: string): string {
    if (base.indexOf("/websocket") >= 0) {
      return base.indexOf("?type=") >= 0 ? base : `${base}${base.indexOf("?") >= 0 ? "&" : "?"}type=main`;
    }
    return `${base}/websocket?type=main`;
  }

  private static isFnId(input: string): boolean {
    return /^[A-Za-z0-9_-]+$/.test(input);
  }

  private static isRelayDomain(input: string): boolean {
    return /(^|\/)[^/]*\.fnos\.net(?::\d+)?$/i.test(input)
      || /\.fnos\.net(?::\d+)?(\/|$)/i.test(input)
      || /(^|\/)[^/]*\.5ddd\.com(?::\d+)?$/i.test(input)
      || /\.5ddd\.com(?::\d+)?(\/|$)/i.test(input);
  }

  private static isRelayPath(input: string): boolean {
    return /^5ddd\.com\/[^/]+$/i.test(input);
  }

  private static hasExplicitPort(input: string): boolean {
    const authority = input.split("/")[0];
    const colonIndex = authority.lastIndexOf(":");
    if (colonIndex <= 0) {
      return false;
    }
    return /^\d+$/.test(authority.substring(colonIndex + 1));
  }
}
