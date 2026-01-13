import { TextUtils } from "../../utils/TextUtils";
import { LastSpanAttribute, TextExpandModel, TextSectionAttribute } from "./Model";

@ComponentV2
export struct TextExpandView {
  //[StartExclude TextExpandView]
  @Param @Require text: string
  @Param @Require onMoreClick: () => void
  @Param textSectionAttribute: TextSectionAttribute = new TextSectionAttribute(this.text)
  // Controls the text or image properties of text folding
  @Param @Require lastSpanAttribute: LastSpanAttribute
  // Whether to collapse or not (true = collapsed/收起, false = expanded/展开)
  @Local collapsed: boolean = true
  // Text expansion properties
  @Local textModifier: TextExpandModel = new TextExpandModel()
  uiContext = this.getUIContext()

  // 用于文本测量的宽度（必须是 number）；不参与布局，避免把组件宽度锁死
  @Local measureWidth?: number

  aboutToAppear(): void {
    this.getIsExpanded();
  }

  //[EndExclude TextExpandView]

  // [Start TextExpandView_textSectionAttributeChange]
  @Monitor('text')
  textSectionAttributeChange() {
    this.textSectionAttribute.title = this.text
    this.collapsed = true
    this.textModifier.exceedOneLine = false
    this.getIsExpanded();
  }

  // [End TextExpandView_textSectionAttributeChange]

  // [Start TextExpandView_getIsExpanded]
  getIsExpanded() {
    const w = this.measureWidth
    if (!w || w <= 0) {
      // 宽度未知时不要测量；收起/展开由 maxLines 控制，按钮显示与否等拿到宽度后再确定
      return
    }

    // 用 maxLines 来判断是否需要“更多”按钮：若全文在 maxLines 内显示完，就不需要处理
    const fullSize: SizeOptions | undefined = this.uiContext.getMeasureUtils().measureTextSize({
      textContent: this.textSectionAttribute.title,
      lineHeight: this.textSectionAttribute.lineHeight,
      constraintWidth: w,
      fontSize: this.textSectionAttribute.fontSize,
      wordBreak: WordBreak.BREAK_ALL
    });

    const clampSize: SizeOptions | undefined = this.uiContext.getMeasureUtils().measureTextSize({
      textContent: this.textSectionAttribute.title,
      lineHeight: this.textSectionAttribute.lineHeight,
      constraintWidth: w,
      fontSize: this.textSectionAttribute.fontSize,
      maxLines: this.textSectionAttribute.maxLines,
      wordBreak: WordBreak.BREAK_ALL
    });

    if (!fullSize?.height || !clampSize?.height) {
      return
    }

    const fullH = Number(fullSize.height)
    const clampH = Number(clampSize.height)

    // 高度相同 => 全文不超过 maxLines (增加容差到2.0，避免测量误差)
    if (fullH <= clampH + 2.0) {
      this.textModifier.needProcess = false
      this.textModifier.exceedOneLine = false
      this.textModifier.title = this.textSectionAttribute.title
      return
    }

    this.textModifier.needProcess = true

    // 根据当前展开状态更新展示文本（折叠时用截断文案 + Span"更多"）
    if (this.collapsed) {
      this.collapseText();
      // Ensure collapsed text was properly generated
      if (!this.textModifier.title || this.textModifier.title === '') {
        this.textModifier.title = this.textSectionAttribute.title;
      }
      this.textModifier.exceedOneLine = true;
    } else {
      this.expandText();
      this.textModifier.exceedOneLine = false;
    }
  }

  // [End TextExpandView_getIsExpanded]

  build() {
    Column({ space: 6 }) {
      //[StartExclude TextExpandView]
      Text() {
        Span(this.textModifier.title)
        if (this.textModifier.needProcess && this.textModifier.exceedOneLine) {
          // collapsed state: show "more" button (content[1])
          Span(this.lastSpanAttribute.content[1])
            .fontColor(this.lastSpanAttribute.color)
            .onClick(()=>{
              this.onMoreClick()
            })
        } else if (this.textModifier.needProcess && !this.textModifier.exceedOneLine) {
          // expanded state: show "collapse" button (content[0])
          Span(this.lastSpanAttribute.content[0])
            .fontColor(this.lastSpanAttribute.color)
            .onClick(()=>{
              this.onMoreClick()
            })
        }
      }
      .fontSize(this.lastSpanAttribute.size)
      .lineHeight(this.textSectionAttribute.lineHeight)
      // 关键：收起态严格限制 maxLines，保证不会显示超过 3 行（或配置的 maxLines）
      .maxLines(this.collapsed ? this.textSectionAttribute.maxLines : 0)
      .textOverflow({ overflow: TextOverflow.Ellipsis })

      //[EndExclude TextExpandView]
    }.onAreaChange((_old,newVal)=>{
      // onAreaChange 给到的宽度在一些版本里是 Length；只有当它是 number 时才用于测量
      const nextMeasureW = this.toNumberWidth(newVal.width)
      if (!nextMeasureW || nextMeasureW === this.measureWidth) return

      this.measureWidth = nextMeasureW
      // 同步给 TextUtils 的二分测量（它依赖 textSectionAttribute.constraintWidth）
      this.textSectionAttribute.constraintWidth = nextMeasureW

      this.collapsed = true
      this.textModifier.exceedOneLine = false
      this.getIsExpanded()
    })
  }

  // [Start TextExpandView_process]
  process(): void {
    if (this.collapsed) {
      this.expandText();
      this.collapsed = false;
      this.textModifier.exceedOneLine = false;
    } else {
      this.collapsed = true;
      this.textModifier.exceedOneLine = true;
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
    // Always use content[1] (the "more" button text) when calculating truncated text
    this.textModifier.title =
      TextUtils.getShortText(
        this.textSectionAttribute,
        `${this.lastSpanAttribute.content[1]}`,
        this.getUIContext());
    // [End TextExpandView_collapseText]
  }

  private toNumberWidth(w: Length): number | undefined {
    // 只在 w 本身就是 number 时才用于测量；避免把 string/resource 当成宽度传给 measureTextSize
    return (typeof w === 'number' && w > 0) ? w : undefined
  }
}

// [End TextExpandView]

