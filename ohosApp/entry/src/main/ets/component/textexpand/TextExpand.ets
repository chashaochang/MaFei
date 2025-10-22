import { TextUtils } from "../../utils/TextUtils";
import { LastSpanAttribute, TextExpandModel, TextSectionAttribute } from "./Model";

@ComponentV2
export struct TextExpandView {
  //[StartExclude TextExpandView]
  @Param @Require text: string
  @Param @Require onMoreClick: () => void
  textSectionAttribute: TextSectionAttribute = new TextSectionAttribute(this.text)
  // Controls the text or image properties of text folding
  @Param @Require lastSpanAttribute: LastSpanAttribute
  // Whether to expand or not
  @Local expanded: boolean = true
  // Text expansion properties
  @Local textModifier: TextExpandModel = new TextExpandModel()
  uiContext = this.getUIContext()

  aboutToAppear(): void {
    this.getIsExpanded();
  }

  //[EndExclude TextExpandView]

  // [Start TextExpandView_textSectionAttributeChange]
  @Monitor('text')
  textSectionAttributeChange() {
    this.textSectionAttribute.title = this.text
    this.expanded = true
    this.getIsExpanded();
  }

  // [End TextExpandView_textSectionAttributeChange]

  // [Start TextExpandView_getIsExpanded]
  getIsExpanded() {
    let titleSize: SizeOptions = this.uiContext.getMeasureUtils().measureTextSize({
      textContent: this.textSectionAttribute.title, //The text content is calculated
      lineHeight: this.textSectionAttribute.lineHeight,
      constraintWidth: this.textSectionAttribute.constraintWidth, //The text layout width is calculated
      fontSize: this.textSectionAttribute.fontSize //The text font size is calculated
    });
    let height = this.getUIContext().px2vp(Number(titleSize.height));
    if (height <= this.textSectionAttribute.lineHeight * 2) {
      this.textModifier.needProcess = false;
      this.textModifier.title = this.textSectionAttribute.title;
      return;
    } else {
      this.textModifier.needProcess = true;
    }
    if (this.expanded) {
      this.collapseText();
    } else {
      this.expandText();
    }
  }

  // [End TextExpandView_getIsExpanded]

  build() {
    Column({ space: 6 }) {
      //[StartExclude TextExpandView]
      Text() {
        Span(this.textModifier.title)
        if (this.textModifier.needProcess && !this.textModifier.exceedOneLine) {
          Span(this.lastSpanAttribute.content[0])
            .fontColor(this.lastSpanAttribute.color)
            .onClick(()=>{
              this.onMoreClick()
            })
        } else if (this.textModifier.needProcess) {
          Span(this.lastSpanAttribute.content[1])
            .fontColor(this.lastSpanAttribute.color)
        }
      }
      .fontSize(this.lastSpanAttribute.size)
      .width(this.textSectionAttribute.constraintWidth)
      .lineHeight(this.textSectionAttribute.lineHeight)

      //[EndExclude TextExpandView]
    }
    // .onClick(() => {
    //   if (!this.textModifier.needProcess) {
    //     return;
    //   }
    //   this.process();
    // })

    // .margin({
    //   left: $r('app.integer.text_expand_item_main_body_column_margin'),
    //   bottom: $r('app.integer.text_expand_item_main_body_column_margin'),
    //   right: $r('app.integer.text_expand_item_main_body_column_margin')
    // })
  }

  // [Start TextExpandView_process]
  process(): void {
    if (this.expanded) {
      this.expandText();
      this.expanded = false;
      this.textModifier.exceedOneLine = true;
    } else {
      this.expanded = true;
      this.textModifier.exceedOneLine = false;
      this.collapseText();
    }
  }

  // [End TextExpandView_process]

  // [Start TextExpandView_expandText]
  // Expand text
  expandText(): void {
    if (this.textModifier.needProcess) {
      this.textModifier.title = this.textSectionAttribute.title;
    }
  }

  // [End TextExpandView_expandText]

  // [Start TextExpandView_collapseText]
  // Collapse text
  collapseText(): void {
    if (!this.textModifier.needProcess) {
      return;
    }
    this.textModifier.title =
      TextUtils.getShortText(
        this.textSectionAttribute,
        this.expanded ? `${this.lastSpanAttribute.content[0]}` : `${this.lastSpanAttribute.content[1]}`,
        this.getUIContext());
    // [End TextExpandView_collapseText]
  }
}

// [End TextExpandView]