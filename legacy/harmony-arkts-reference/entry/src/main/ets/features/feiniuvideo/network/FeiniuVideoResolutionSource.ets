import {
  FeiniuVideoAddressResolution,
  FeiniuVideoHostCandidate,
  FeiniuVideoHostResolution
} from './FeiniuVideoApiModels'

export interface FeiniuVideoAddressResolutionSource {
  resolve(rawValue: string): Promise<FeiniuVideoAddressResolution>
}

export interface FeiniuVideoHostResolutionSource {
  resolve(candidates: FeiniuVideoHostCandidate[]): Promise<FeiniuVideoHostResolution>
}
