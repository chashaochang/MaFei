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

import { AppStorageV2, mediaquery } from '@kit.ArkUI';
import { AppUIState } from '../entity/AppUIState';
import { WindowUtil } from './WindowUtil';

export enum BreakpointTypeEnum {
  XS = 'xs',
  SM = 'sm',
  MD = 'md',
  LG = 'lg',
  XL = 'xl',
  XXL = 'xxl',
}

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

export class BreakpointSystem {
  private currentBreakpoint: string = BreakpointTypeEnum.MD;
  private xsListener: mediaquery.MediaQueryListener | undefined;
  private smListener: mediaquery.MediaQueryListener | undefined;
  private mdListener: mediaquery.MediaQueryListener | undefined;
  private lgListener: mediaquery.MediaQueryListener | undefined;
  private xlListener: mediaquery.MediaQueryListener | undefined;
  private xxlListener: mediaquery.MediaQueryListener | undefined;
  private appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private updateCurrentBreakpoint(breakpoint: BreakpointTypeEnum) {
    WindowUtil.getDeviceSize(getContext(this));
    if (this.currentBreakpoint !== breakpoint) {
      this.currentBreakpoint = breakpoint;
      this.appUIState.currentBreakpoint = this.currentBreakpoint;
    }
  }

  private isBreakpointXS = (mediaQueryResult: mediaquery.MediaQueryResult) => {
    if (mediaQueryResult.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.SM);
    }
  }
  private isBreakpointSM = (mediaQueryResult: mediaquery.MediaQueryResult) => {
    if (mediaQueryResult.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.SM);
    }
  }
  private isBreakpointMD = (mediaQueryResult: mediaquery.MediaQueryResult) => {
    if (mediaQueryResult.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.MD);
    }
  }
  private isBreakpointLG = (mediaQueryResult: mediaquery.MediaQueryResult) => {
    if (mediaQueryResult.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.LG);
    }
  }
  private isBreakpointXL = (mediaQueryResult: mediaquery.MediaQueryResult) => {
    if (mediaQueryResult.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.LG);
    }
  }
  private isBreakpointXXL = (mediaQueryResult: mediaquery.MediaQueryResult) => {
    if (mediaQueryResult.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.LG);
    }
  }

  public register(uiContext: UIContext) {
    this.xsListener = uiContext.getMediaQuery().matchMediaSync('(0vp<width<320vp)');
    if (this.xsListener?.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.XS);
    }
    this.xsListener?.on('change', this.isBreakpointXS);
    this.smListener = uiContext.getMediaQuery().matchMediaSync('(320vp<=width<600vp)');
    if (this.smListener?.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.SM);
    }
    this.smListener?.on('change', this.isBreakpointSM);
    this.mdListener = uiContext.getMediaQuery().matchMediaSync('(600vp<=width<840vp)');
    if (this.mdListener?.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.MD);
    }
    this.mdListener?.on('change', this.isBreakpointMD);
    this.lgListener = uiContext.getMediaQuery().matchMediaSync('(840vp<=width<1440vp)');
    if (this.lgListener?.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.LG);
    }
    this.lgListener?.on('change', this.isBreakpointLG);
    this.xlListener = uiContext.getMediaQuery().matchMediaSync('(1440vp<=width<1920vp)');
    if (this.xlListener?.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.XL);
    }
    this.xlListener?.on('change', this.isBreakpointXL);
    this.xxlListener = uiContext.getMediaQuery().matchMediaSync('(1920vp<=width)');
    if (this.xxlListener?.matches) {
      this.updateCurrentBreakpoint(BreakpointTypeEnum.XXL);
    }
    this.xxlListener?.on('change', this.isBreakpointXXL);
  }

  public unregister() {
    this.smListener?.off('change', this.isBreakpointSM);
    this.mdListener?.off('change', this.isBreakpointMD);
    this.lgListener?.off('change', this.isBreakpointLG);
  }
}