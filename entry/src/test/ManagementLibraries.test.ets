import { describe, expect, it } from '@ohos/hypium'
import { CollectionTypeOptions } from 'sdk/src/main/ets/generated-client/models'
import {
  ManagementLibraryDraft,
  ManagementLibraryValidationCode,
  ManagementMediaDeleteTarget
} from '../main/ets/features/management/library/ManagementLibraryModels'
import { ManagementLibraryChangePlanner } from
  '../main/ets/features/management/library/ManagementLibraryChangePlanner'
import { ManagementLibraryMapper } from
  '../main/ets/features/management/library/ManagementLibraryMapper'
import { ManagementLibraryPolicy } from
  '../main/ets/features/management/library/ManagementLibraryPolicy'
import { ManagementMediaDeletePolicy } from
  '../main/ets/features/management/library/ManagementMediaDeletePolicy'

function libraryDraft(name: string, paths: string[]): ManagementLibraryDraft {
  const draft = new ManagementLibraryDraft()
  draft.name = name
  draft.originalName = name
  draft.paths = paths
  draft.options = { Enabled: true }
  draft.originalOptionsJson = ManagementLibraryMapper.serializeOptions(draft.options)
  return draft
}

function deleteTarget(id: string, canDelete: boolean, path: string): ManagementMediaDeleteTarget {
  const target = new ManagementMediaDeleteTarget()
  target.id = id
  target.name = id
  target.canDelete = canDelete
  target.path = path
  return target
}

export default function managementLibrariesTest() {
  describe('ManagementLibraries', () => {
    it('rejectsBlankNameAndMissingPaths', 0, () => {
      const draft = libraryDraft('   ', [])
      expect(ManagementLibraryPolicy.validateDraft(draft))
        .assertEqual(ManagementLibraryValidationCode.NameRequired)
      draft.name = 'Movies'
      expect(ManagementLibraryPolicy.validateDraft(draft))
        .assertEqual(ManagementLibraryValidationCode.PathRequired)
    })

    it('deduplicatesTrimmedPathsWithoutChangingCase', 0, () => {
      const paths = ManagementLibraryPolicy.normalizePaths([
        ' /media/Movies ', '/media/Movies', '/MEDIA/Movies'
      ])
      expect(paths.length).assertEqual(2)
      expect(paths[0]).assertEqual('/media/Movies')
      expect(paths[1]).assertEqual('/MEDIA/Movies')
    })

    it('rejectsExactDuplicatePathsAfterTrimming', 0, () => {
      const draft = libraryDraft('Movies', ['/media/Movies', ' /media/Movies '])
      expect(ManagementLibraryPolicy.validateDraft(draft))
        .assertEqual(ManagementLibraryValidationCode.DuplicatePath)
    })

    it('omitsMixedTypeOnlyWhenCreatingLibrary', 0, () => {
      expect(ManagementLibraryPolicy.collectionTypeForAvailableOptions(CollectionTypeOptions.Mixed))
        .assertEqual('mixed')
      expect(ManagementLibraryPolicy.collectionTypeForCreate(CollectionTypeOptions.Mixed) === undefined)
        .assertTrue()
      expect(ManagementLibraryPolicy.collectionTypeForCreate(CollectionTypeOptions.Movies))
        .assertEqual('movies')
    })

    it('plansRenamePathsAndOptionsDeterministically', 0, () => {
      const original = libraryDraft('Movies', ['/old', '/keep'])
      original.id = 'library-id'
      original.originalName = 'Movies'
      original.originalOptionsJson = '{"Enabled":true}'
      const edited = libraryDraft('Films', ['/keep', '/new'])
      edited.id = 'library-id'
      edited.originalName = 'Movies'
      edited.originalOptionsJson = original.originalOptionsJson
      edited.options.Enabled = false
      const plan = ManagementLibraryChangePlanner.plan(original, edited)
      expect(plan.newName).assertEqual('Films')
      expect(plan.removedPaths[0]).assertEqual('/old')
      expect(plan.addedPaths[0]).assertEqual('/new')
      expect(plan.optionsChanged).assertTrue()
    })

    it('requiresAdministratorItemCapabilityAndIdForDelete', 0, () => {
      expect(ManagementMediaDeletePolicy.canDelete(true, true, 'item')).assertTrue()
      expect(ManagementMediaDeletePolicy.canDelete(false, true, 'item')).assertFalse()
      expect(ManagementMediaDeletePolicy.canDelete(true, false, 'item')).assertFalse()
      expect(ManagementMediaDeletePolicy.canDelete(true, true, '')).assertFalse()
    })

    it('separatesDeletableAndBlockedBatchItems', 0, () => {
      const summary = ManagementMediaDeletePolicy.summarize([
        deleteTarget('allowed', true, '/media/a.mkv'),
        deleteTarget('blocked', false, '/media/b.mkv')
      ], true)
      expect(summary.allowedIds.length).assertEqual(1)
      expect(summary.allowedIds[0]).assertEqual('allowed')
      expect(summary.allowedPaths[0]).assertEqual('/media/a.mkv')
      expect(summary.blockedNames.length).assertEqual(1)
      expect(summary.blockedNames[0]).assertEqual('blocked')
    })

    it('preservesCompatibilityFieldsWhenCloningOptions', 0, () => {
      const draft = libraryDraft('Movies', ['/media'])
      draft.options.EnableArchiveMediaFiles = true
      draft.options.MediaSegmentProviderOrder = ['Chapter Segments']
      draft.options.TypeOptions = [{
        Type: 'Movie',
        SimilarItemProviders: ['MovieDb'],
        SimilarItemProviderOrder: ['MovieDb']
      }]

      const clone = ManagementLibraryMapper.cloneDraft(draft)
      expect(clone.options.EnableArchiveMediaFiles).assertTrue()
      expect(clone.options.MediaSegmentProviderOrder?.[0]).assertEqual('Chapter Segments')
      expect(clone.options.TypeOptions?.[0].SimilarItemProviders?.[0]).assertEqual('MovieDb')
      expect(clone.options.TypeOptions?.[0].SimilarItemProviderOrder?.[0]).assertEqual('MovieDb')
      clone.options.MediaSegmentProviderOrder?.push('Other')
      expect(draft.options.MediaSegmentProviderOrder?.length).assertEqual(1)
    })
  })
}
