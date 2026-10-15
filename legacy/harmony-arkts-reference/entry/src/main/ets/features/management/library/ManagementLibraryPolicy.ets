import { CollectionTypeOptions } from 'sdk/src/main/ets/generated-client/models'
import {
  ManagementLibraryDraft,
  ManagementLibraryValidationCode
} from './ManagementLibraryModels'

export class ManagementLibraryPolicy {
  static normalizePaths(paths: string[]): string[] {
    const normalized: string[] = []
    paths.forEach((path: string) => {
      const value = path.trim()
      if (value.length > 0 && !normalized.includes(value)) {
        normalized.push(value)
      }
    })
    return normalized
  }

  static validateDraft(draft: ManagementLibraryDraft): ManagementLibraryValidationCode {
    if (draft.name.trim().length === 0) {
      return ManagementLibraryValidationCode.NameRequired
    }

    const nonEmptyPaths = draft.paths
      .map((path: string) => path.trim())
      .filter((path: string) => path.length > 0)
    if (nonEmptyPaths.length === 0) {
      return ManagementLibraryValidationCode.PathRequired
    }
    if (ManagementLibraryPolicy.normalizePaths(nonEmptyPaths).length !== nonEmptyPaths.length) {
      return ManagementLibraryValidationCode.DuplicatePath
    }
    return ManagementLibraryValidationCode.None
  }

  static collectionTypeForAvailableOptions(type: CollectionTypeOptions): string {
    return type
  }

  static collectionTypeForCreate(type: CollectionTypeOptions): string | undefined {
    return type === CollectionTypeOptions.Mixed ? undefined : type
  }

  static collectionTypeFromValue(value: string): CollectionTypeOptions {
    if (value === CollectionTypeOptions.Movies) return CollectionTypeOptions.Movies
    if (value === CollectionTypeOptions.Tvshows) return CollectionTypeOptions.Tvshows
    if (value === CollectionTypeOptions.Music) return CollectionTypeOptions.Music
    if (value === CollectionTypeOptions.Musicvideos) return CollectionTypeOptions.Musicvideos
    if (value === CollectionTypeOptions.Homevideos) return CollectionTypeOptions.Homevideos
    if (value === CollectionTypeOptions.Boxsets) return CollectionTypeOptions.Boxsets
    if (value === CollectionTypeOptions.Books) return CollectionTypeOptions.Books
    return CollectionTypeOptions.Mixed
  }
}
