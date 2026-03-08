/*
 * Copyright (c) 2023 Huawei Device Co., Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export enum LoadingStatus {
  OFF = 'off',
  LOADING = 'loading',
  SUCCESS = 'success',
  FAILED = 'failed'
}

export enum WebpageStatus {
  INIT = 'init',
  ERROR = 'error',
  FINISHED = 'finished'
}

export enum ResourcesType {
  FEED = 'feed',
  ARTICLE = 'article',
  VIDEO = 'video'
}

export enum TagType {
  NORMAL = 'normal',
  RECOMMEND = 'recommend',
  HOT = 'hot'
}

/**
 * GridRow column.
 */
export enum ColumnEnum {
  XS = 2,
  SM = 4,
  MD = 8,
  LG = 12,
  XL = 16,
  XXL = 24
}

/**
 * GridCol span.
 */
export enum SpanEnum {
  XS = 2,
  SM = 4,
  MD = 6,
  LG = 8,
  XL = 10,
  XXL = 12
}

export enum ColSpanEnum {
  XS = 1,
  SM = 2,
  MD = 3,
  LG = 4,
  XL = 6,
  XXL = 8
}

/**
 * GridCol offset.
 */
export enum OffsetEnum {
  XS = 0,
  SM = 1,
  MD = 1,
  LG = 2,
  XL = 2,
  XXL = 2
}

export enum TemplateEnum {
  XS = 1,
  SM = 2,
  MD = 3,
  LG = 6,
  XL = 8,
  XXL = 12
}

export enum EventTypeEnum  {
  COLLECTED = 'collected_event',
  LIKED = 'liked_event',
  LEARNING = 'learning_event',
  VIEW = 'view_event',
  MINE_REFRESH= 'mine_refresh_event'
}
