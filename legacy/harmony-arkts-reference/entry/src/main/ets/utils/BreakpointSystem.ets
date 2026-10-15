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

import { BreakpointTypeEnum } from './BreakpointPolicy';

export { BreakpointTypeEnum } from './BreakpointPolicy';

export interface BreakpointTypes<T> {
  xs: T;
  sm: T;
  md: T;
  lg: T;
  xl: T;
  xxl: T;
}

export class BreakpointType<T> {
  xs: T;
  sm: T;
  md: T;
  lg: T;
  xl: T;
  xxl: T;

  constructor(param: BreakpointTypes<T>) {
    this.xs = param.xs;
    this.sm = param.sm;
    this.md = param.md;
    this.lg = param.lg;
    this.xl = param.xl;
    this.xxl = param.xxl;
  }

  getValue(currentBreakpoint: string): T {
    if (currentBreakpoint === BreakpointTypeEnum.SM) {
      return this.sm;
    }
    if (currentBreakpoint === BreakpointTypeEnum.MD) {
      return this.md;
    }
    if (currentBreakpoint === BreakpointTypeEnum.LG) {
      return this.lg;
    }
    if (currentBreakpoint === BreakpointTypeEnum.XL) {
      return this.xl;
    }
    if (currentBreakpoint === BreakpointTypeEnum.XXL) {
      return this.xxl;
    }
    return this.xs;
  }
}
