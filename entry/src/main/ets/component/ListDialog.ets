import { SmartDialog } from "ohos_smart_dialog";
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver";

export class ListDialog {
  title: string = "";
  list: Array<string>;
  selectIndex: number
  onItemClick?: (index: number) => void;
  nativeSurfaceProvider: () => boolean
  tag: string

  constructor(title: string, list: Array<string>, selectIndex: number, onItemClick?: (index: number) => void,
    nativeSurfaceProvider: () => boolean = () => false) {
    this.title = title;
    this.list = list;
    this.selectIndex = selectIndex;
    this.onItemClick = onItemClick;
    this.nativeSurfaceProvider = nativeSurfaceProvider;
    this.tag = "ListDialog" + Date.now();
  }

  show() {
    SmartDialog.show({
      builder: listDialogBuilder,
      builderArgs: {
        title: this.title,
        list: this.list,
        selectIndex: this.selectIndex,
        onItemClick: this.onItemClick,
        nativeSurface: this.nativeSurfaceProvider()
      },
      alignment: Alignment.Bottom,
      tag: this.tag,
    })
  }

  dismiss() {
    SmartDialog.dismiss({
      tag: this.tag
    });
  }
}

@Builder
function listDialogContent(args: DialogArgs, nativeSurface: boolean) {
  Text(args.title)
    .fontSize(18)
    .fontWeight(FontWeight.Bold)
    .padding({
      top: 16,
      bottom: 0
    })
    .width('100%')
    .textAlign(TextAlign.Center)
    .fontColor(nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
  List() {
    ForEach(args.list, (item: string, index: number) => {
      ListItem() {
        Text(item)
          .fontSize(16)
          .padding({
            top: 10,
            bottom: 10,
            left: 16,
            right: 16
          })
          .width('100%')
          .textAlign(TextAlign.Start)
          .fontColor(args.selectIndex === index ?
            (nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')) :
            (nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2')))
          .onClick(() => {
            args.onItemClick && args.onItemClick(index);
          })
      }.width('100%')
    })
  }.listDirection(Axis.Vertical)
  .width('100%')
  .layoutWeight(1)
}

@Builder
function nativeListDialog(args: DialogArgs) {
  Column() {
    listDialogContent(args, true)
  }
  .padding({
    bottom: 20
  })
  .constraintSize({
    maxHeight: '40%'
  })
  .borderRadius({
    topLeft: 16,
    topRight: 16
  })
  .clip(true)
  .attributeModifier(AppThemeSurfaceResolver.modifier(
    AppThemeMaterialRole.Floating,
    true
  ))
}

@Builder
function feiniuListDialog(args: DialogArgs) {
  Column() {
    listDialogContent(args, false)
  }
  .padding({
    bottom: 20
  })
  .backgroundColor($r('app.color.component_bg'))
  .constraintSize({
    maxHeight: '40%'
  })
  .borderRadius({
    topLeft: 16,
    topRight: 16
  })
}

@Builder
export function listDialogBuilder(args: DialogArgs) {
  if (args.nativeSurface) {
    nativeListDialog(args)
  } else {
    feiniuListDialog(args)
  }
}

// 定义参数类型
interface DialogArgs {
  title: string,
  list: Array<string>,
  selectIndex: number,
  onItemClick?: (index: number) => void,
  nativeSurface: boolean
}
