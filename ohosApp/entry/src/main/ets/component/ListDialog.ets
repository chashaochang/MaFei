import { SmartDialog } from "ohos_smart_dialog";

export class ListDialog {
  title: string = "";
  list: Array<string>;
  selectIndex: number
  onItemClick?: (index: number) => void;
  tag: string

  constructor(title: string, list: Array<string>, selectIndex: number, onItemClick?: (index: number) => void) {
    this.title = title;
    this.list = list;
    this.selectIndex = selectIndex;
    this.onItemClick = onItemClick;
    this.tag = "ListDialog" + Date.now();
  }

  show() {
    SmartDialog.show({
      builder: listDialogBuilder,
      builderArgs: {
        title: this.title,
        list: this.list,
        selectIndex: this.selectIndex,
        onItemClick: this.onItemClick
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
export function listDialogBuilder(args: DialogArgs) {
  Column() {
    Text(args.title)
      .fontSize(18)
      .fontWeight(FontWeight.Bold)
      .padding({
        top: 16,
        bottom: 0
      })
      .width('100%')
      .textAlign(TextAlign.Center)
      .fontColor($r('app.color.text_primary'))
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
            .fontColor(args.selectIndex === index ? $r('app.color.text_primary') : $r('app.color.text_2'))
            .onClick(() => {
              args.onItemClick && args.onItemClick(index);
            })
        }.width('100%')
      })
    }.listDirection(Axis.Vertical)
    .width('100%')
    .layoutWeight(1)
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

// 定义参数类型
interface DialogArgs {
  title: string,
  list: Array<string>,
  selectIndex: number,
  onItemClick?: (index: number) => void
}