import { SmartDialog } from "ohos_smart_dialog";
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver";

interface DialogArgs {
  title: string,
  list: Array<string>,
  selectIndex: number,
  onItemClick?: (index: number) => void,
  nativeSurface: boolean
}

// CustomBuilderAdapter: smart-list-dialog-content
@Builder
function listDialogBuilder(args: DialogArgs) {
  ListDialogSurface({ args })
}

@ComponentV2
struct ListDialogSurface {
  @Require @Param args: DialogArgs

  build() {
    Column() {
      Text(this.args.title)
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .padding({ top: 16, bottom: 0 })
        .width('100%')
        .textAlign(TextAlign.Center)
        .fontColor(this.args.nativeSurface ?
          $r('sys.color.font_primary') : $r('app.color.text_primary'))
      List() {
        ForEach(this.args.list, (item: string, index: number) => {
          ListItem() {
            Text(item)
              .fontSize(16)
              .padding({ top: 10, bottom: 10, left: 16, right: 16 })
              .width('100%')
              .textAlign(TextAlign.Start)
              .fontColor(this.args.selectIndex === index ?
                (this.args.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')) :
                (this.args.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2')))
              .onClick(() => {
                this.args.onItemClick?.(index)
              })
          }
          .width('100%')
        })
      }
      .listDirection(Axis.Vertical)
      .width('100%')
      .layoutWeight(1)
    }
    .padding({ bottom: 20 })
    .constraintSize({ maxHeight: '40%' })
    .borderRadius({ topLeft: 16, topRight: 16 })
    .clip(this.args.nativeSurface)
    .backgroundColor(this.args.nativeSurface ? Color.Transparent : $r('app.color.component_bg'))
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating,
      this.args.nativeSurface
    ))
  }
}

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
