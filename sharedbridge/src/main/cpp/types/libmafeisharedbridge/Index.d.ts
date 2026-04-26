declare module 'libmafeisharedbridge.so' {
// <mafei-kmp-export-generated>
  export const kmpGeneratedActiveHandleCount: () => number;
  export const kmpGeneratedActiveAsyncCount: () => number;
  export const kmpGeneratedChaseFollowStoreCreate: () => bigint;
  export const kmpGeneratedChaseFollowStoreDestroy: (handle: bigint) => void;
  export const kmpGeneratedChaseFollowStorePreferenceKey: (handle: bigint, serverSystemId: string, serverId: string, userId: string) => string;
  export const kmpGeneratedChaseFollowStoreNormalize: (handle: bigint, idsJson: string) => string;
  export const kmpGeneratedChaseFollowStoreContains: (handle: bigint, idsJson: string, seriesId: string) => boolean;
  export const kmpGeneratedChaseFollowStoreSetFollowing: (handle: bigint, idsJson: string, seriesId: string, following: boolean) => string;
  export const kmpGeneratedChaseFollowStoreToggleFollowing: (handle: bigint, idsJson: string, seriesId: string) => string;
  export const kmpGeneratedChasePlannerCreate: () => bigint;
  export const kmpGeneratedChasePlannerDestroy: (handle: bigint) => void;
  export const kmpGeneratedChasePlannerPlan: (handle: bigint, inputJson: string) => string;
// </mafei-kmp-export-generated>
}
