import { BaseViewModel } from '../../../common/base/BaseViewModel'
import { MineHistoryCandidate, MineHistoryMediaType, MineHistoryPlanner } from './MineHistoryPlanner'
import {
  MineHistoryLoadResult,
  MineHistoryRepository,
  MineHistoryRequestSession
} from './MineHistoryRepository'
import { MineHistoryPageState, MineHistoryPageUIState } from './MineHistoryPageUIState'

export class MineHistoryPageViewModel extends BaseViewModel<MineHistoryPageUIState> {
  private readonly repository: MineHistoryRepository = new MineHistoryRepository()
  private readonly planner: MineHistoryPlanner = new MineHistoryPlanner()
  private readonly pageSize: number = 10
  private resumeCandidates: MineHistoryCandidate[] = []
  private completedCandidates: MineHistoryCandidate[] = []
  private resumeStartIndex: number = 0
  private completedStartIndex: number = 0
  private resumeHasMore: boolean = true
  private completedHasMore: boolean = true
  private requestActive: boolean = false
  private requestEpoch: number = 0
  private contentGeneration: number = 0
  private activeScope: string = ''

  loadInitial(): void {
    const requestScope = this.repository.currentScope()
    if (this.requestActive && this.activeScope === requestScope) {
      return
    }
    this.resumeCandidates = []
    this.completedCandidates = []
    this.resumeStartIndex = 0
    this.completedStartIndex = 0
    this.resumeHasMore = true
    this.completedHasMore = true
    this.ui.items = []
    this.ui.historyState = MineHistoryPageState.Loading
    this.ui.loadingMore = false
    this.ui.loadMoreFailed = false
    this.ui.hasMore = true
    const contentGeneration = ++this.contentGeneration
    this.loadBatch(true, contentGeneration)
  }

  loadMore(): void {
    if (this.shouldRestartForScopeChange()) {
      this.loadInitial()
      return
    }
    if (this.requestActive || !this.ui.hasMore || this.ui.loadMoreFailed) {
      return
    }
    this.ui.loadingMore = true
    this.ui.loadMoreFailed = false
    this.loadBatch(false, this.contentGeneration)
  }

  retryLoadMore(): void {
    if (this.shouldRestartForScopeChange()) {
      this.loadInitial()
      return
    }
    if (this.requestActive || !this.ui.hasMore || !this.ui.loadMoreFailed) {
      return
    }
    this.ui.loadingMore = true
    this.ui.loadMoreFailed = false
    this.loadBatch(false, this.contentGeneration)
  }

  private async loadBatch(initial: boolean, contentGeneration: number): Promise<void> {
    const requestScope = this.repository.currentScope()
    const requestEpoch = ++this.requestEpoch
    this.requestActive = true
    this.activeScope = requestScope
    try {
      const result: MineHistoryLoadResult = await this.repository.load({
        resumeStartIndex: this.resumeStartIndex,
        completedStartIndex: this.completedStartIndex,
        limit: this.pageSize,
        loadResume: this.resumeHasMore,
        loadCompleted: this.completedHasMore
      })
      if (!this.isCurrentRequest(requestEpoch, contentGeneration, result.scope)) {
        return
      }
      const resumeFailed = result.resume.requested && result.resume.failed
      const completedFailed = result.completed.requested && result.completed.failed
      const anyFailed = resumeFailed || completedFailed
      const batchCandidates: MineHistoryCandidate[] = []

      if (result.resume.requested && !result.resume.failed) {
        result.resume.items.forEach((candidate: MineHistoryCandidate) => {
          batchCandidates.push(candidate)
        })
        this.resumeCandidates = this.resumeCandidates.concat(result.resume.items)
        this.resumeStartIndex = result.resume.nextStartIndex
        this.resumeHasMore = result.resume.hasMore
      }
      if (result.completed.requested && !result.completed.failed) {
        result.completed.items.forEach((candidate: MineHistoryCandidate) => {
          batchCandidates.push(candidate)
        })
        this.completedCandidates = this.completedCandidates.concat(result.completed.items)
        this.completedStartIndex = result.completed.nextStartIndex
        this.completedHasMore = result.completed.hasMore
      }

      const loadedCount = this.resumeCandidates.length + this.completedCandidates.length
      this.ui.items = this.planner.merge(
        this.resumeCandidates, this.completedCandidates, loadedCount)
      this.ui.hasMore = this.resumeHasMore || this.completedHasMore

      if (initial) {
        if ((resumeFailed && completedFailed) || (this.ui.items.length === 0 && anyFailed)) {
          this.ui.historyState = MineHistoryPageState.Error
        } else if (this.ui.items.length === 0) {
          this.ui.historyState = MineHistoryPageState.Empty
        } else {
          this.ui.historyState = MineHistoryPageState.Content
          this.ui.loadMoreFailed = anyFailed
        }
      } else {
        this.ui.historyState = MineHistoryPageState.Content
        this.ui.loadMoreFailed = anyFailed
      }
      if (result.session) {
        const visibleCandidates = this.candidatesForVisibleItems(this.ui.items, batchCandidates)
        if (visibleCandidates.length > 0) {
          this.enrichVisibleHistory(visibleCandidates, result.session, contentGeneration)
        }
      }
    } catch (error) {
      if (!this.isCurrentRequest(requestEpoch, contentGeneration, requestScope)) {
        return
      }
      console.error(`[MineHistoryPage] request failed: ${JSON.stringify(error)}`)
      if (initial) {
        this.ui.historyState = MineHistoryPageState.Error
      } else {
        this.ui.loadMoreFailed = true
      }
    } finally {
      if (requestEpoch === this.requestEpoch) {
        this.requestActive = false
        this.ui.loadingMore = false
      }
    }
  }

  private candidatesForVisibleItems(
    items: MineHistoryCandidate[],
    batchCandidates: MineHistoryCandidate[]
  ): MineHistoryCandidate[] {
    const visibleDates: Map<string, string> = new Map<string, string>()
    items.forEach((item: MineHistoryCandidate) => {
      visibleDates.set(item.id, item.lastPlayedDate)
    })
    const candidates: MineHistoryCandidate[] = []
    const seenIds: Map<string, boolean> = new Map<string, boolean>()
    batchCandidates.forEach((candidate: MineHistoryCandidate) => {
      if (!seenIds.has(candidate.id) && visibleDates.get(candidate.id) === candidate.lastPlayedDate &&
        candidate.mediaType === MineHistoryMediaType.Episode &&
        candidate.seriesId.length > 0 &&
        (candidate.latestSeasonNumber === undefined || candidate.latestEpisodeNumber === undefined)) {
        seenIds.set(candidate.id, true)
        candidates.push(candidate)
      }
    })
    return candidates
  }

  private async enrichVisibleHistory(
    candidates: MineHistoryCandidate[],
    session: MineHistoryRequestSession,
    contentGeneration: number
  ): Promise<void> {
    try {
      await this.repository.enrichSeriesPositions(candidates, session)
      if (!this.isCurrentContent(contentGeneration, session.scope)) {
        return
      }
      const loadedCount = this.resumeCandidates.length + this.completedCandidates.length
      this.ui.items = this.planner.merge(
        this.resumeCandidates, this.completedCandidates, loadedCount)
    } catch (error) {
      console.warn(`[MineHistoryPage] series enrichment failed: ${JSON.stringify(error)}`)
    }
  }

  private isCurrentRequest(requestEpoch: number, contentGeneration: number, scope: string): boolean {
    return requestEpoch === this.requestEpoch && contentGeneration === this.contentGeneration &&
      scope === this.activeScope &&
      scope === this.repository.currentScope()
  }

  private isCurrentContent(contentGeneration: number, scope: string): boolean {
    return contentGeneration === this.contentGeneration && scope === this.activeScope &&
      scope === this.repository.currentScope()
  }

  private shouldRestartForScopeChange(): boolean {
    return this.activeScope.length > 0 && this.activeScope !== this.repository.currentScope()
  }
}
