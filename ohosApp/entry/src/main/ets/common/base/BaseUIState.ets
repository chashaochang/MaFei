@ObservedV2
export class BaseUIState {
  context:UIContext
  @Trace pageState: PageState = PageState.Loading
  constructor(context:UIContext) {
    this.context = context
  }
}

export enum PageState {
  Loading,
  Content,
  Error
}