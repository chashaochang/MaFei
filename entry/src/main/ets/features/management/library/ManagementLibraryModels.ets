import {
  CollectionTypeOptions,
  LibraryOptionInfoDto,
  LibraryOptions,
  LibraryOptionsResultDto,
  LibraryTypeOptionsDto,
  TypeOptions
} from 'sdk/src/main/ets/generated-client/models'

export enum ManagementLibraryValidationCode {
  None = 'none',
  NameRequired = 'name_required',
  PathRequired = 'path_required',
  DuplicatePath = 'duplicate_path'
}

export enum ManagementLibraryOperationCode {
  None = 'none',
  Success = 'success',
  AuthenticationExpired = 'authentication_expired',
  PermissionDenied = 'permission_denied',
  Unsupported = 'unsupported',
  Conflict = 'conflict',
  NotFound = 'not_found',
  PartialSuccess = 'partial_success',
  Failed = 'failed'
}

export class ManagementLibraryOperationResult {
  ok: boolean = false
  code: ManagementLibraryOperationCode = ManagementLibraryOperationCode.None
  message: string = ''
  library?: ManagementLibraryItem

  static success(library?: ManagementLibraryItem): ManagementLibraryOperationResult {
    const result = new ManagementLibraryOperationResult()
    result.ok = true
    result.code = ManagementLibraryOperationCode.Success
    result.library = library
    return result
  }

  static failure(code: ManagementLibraryOperationCode, message: string = ''):
    ManagementLibraryOperationResult {
    const result = new ManagementLibraryOperationResult()
    result.code = code
    result.message = message
    return result
  }
}

export class ManagementLibraryItem {
  id: string = ''
  name: string = ''
  collectionType: string = ''
  paths: string[] = []
  options: ManagementLibraryOptions = {}
  refreshProgress?: number
  refreshStatus: string = ''
}

export interface ManagementLibraryTypeOptions extends TypeOptions {
  SimilarItemProviders?: string[]
  SimilarItemProviderOrder?: string[]
}

export interface ManagementLibraryOptions extends LibraryOptions {
  EnableArchiveMediaFiles?: boolean
  MediaSegmentProviderOrder?: string[]
  TypeOptions?: ManagementLibraryTypeOptions[]
}

export interface ManagementLibraryAvailableTypeOptions extends LibraryTypeOptionsDto {
  SimilarItemProviders?: LibraryOptionInfoDto[]
}

export interface ManagementLibraryAvailableOptions extends LibraryOptionsResultDto {
  MediaSegmentProviders?: LibraryOptionInfoDto[]
  TypeOptions?: ManagementLibraryAvailableTypeOptions[]
}

export class ManagementLibraryDraft {
  id: string = ''
  originalName: string = ''
  name: string = ''
  collectionType: CollectionTypeOptions = CollectionTypeOptions.Mixed
  paths: string[] = []
  options: ManagementLibraryOptions = {}
  originalOptionsJson: string = '{}'
}

export class ManagementLibraryChangeSet {
  newName: string = ''
  addedPaths: string[] = []
  removedPaths: string[] = []
  optionsChanged: boolean = false
}

export class ManagementLibraryPathEntry {
  name: string = ''
  path: string = ''
}

export class ManagementMediaDeleteTarget {
  id: string = ''
  name: string = ''
  path: string = ''
  canDelete: boolean = false
}

export class ManagementMediaDeleteSummary {
  allowedIds: string[] = []
  allowedPaths: string[] = []
  blockedNames: string[] = []
}
