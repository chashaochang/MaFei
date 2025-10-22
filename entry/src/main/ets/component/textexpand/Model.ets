// [Start LastSpanAttribute]
/**
 * Text that controls text folding
 *
 * @param {number} lastSpanType - Type (0 for text)
 * @param {number} charactersNumber - The number of characters in the collapsed text or image
 * @param {ResourceStr[]} -Text or image content
 * @param {size} Text or image size
 * @param {color} color
 */
export class LastSpanAttribute {
  lastSpanType: number;
  charactersNumber: number;
  content: string[];
  size: ResourceStr | number;
  color: ResourceStr | Color;

  constructor(lastSpanType: number, charactersNumber: number = 1,
    content: string[], size: ResourceStr | number, color: ResourceStr | Color = Color.Orange) {
    this.lastSpanType = lastSpanType;
    this.charactersNumber = charactersNumber;
    this.content = content;
    this.size = size;
    this.color = color;
  }
}

// [End LastSpanAttribute]
// [Start TextExpandModel]
/**
 * Plain text expands the view model
 * @param {ResourceStr} title - Textual content
 * @param {boolean} needProcess - Whether to display the Expand and collapse button
 * @param {exceedOneLine} boolean - When adding the word 'collapse', more than one line needs to be wrapped
 */
@ObservedV2
export class TextExpandModel {
  @Trace title: ResourceStr = '';
  needProcess: boolean = true;
  exceedOneLine: boolean = false;
}

// [End TextExpandModel]

// [Start TextSectionAttribute]
/**
 * Plain Text Attribute
 *
 * @param {ResourceStr} title - Textual content
 * @param {number} {maxLines} - maxLines
 * @param {ResourceStr} {fontColor} - fontColor
 * @param { Resource | number | string} {fontSize} - fontSize
 * @param {number} lineHeight - lineHeight
 * @param {Resource | number | string} contraintWidth - Sets the maximum width of the text on the line
 */

@ObservedV2
export class TextSectionAttribute {
  @Trace title: string = '';
  maxLines: number;
  fontColor: ResourceStr;
  fontSize: Resource | number | string;
  lineHeight: number;
  constraintWidth: Resource | number | string;

  constructor(title: string = '', maxLines: number = 3, fontColor: ResourceStr = '#CBD5E0',
    fontSize: Resource | number | string = 14, lineHeight: number = 16,
    constraintWidth: Resource | number | string = 350) {
    this.title = title;
    this.maxLines = maxLines;
    this.fontColor = fontColor;
    this.fontSize = fontSize;
    this.lineHeight = lineHeight;
    this.constraintWidth = constraintWidth;
  }
}

// [End TextSectionAttribute]