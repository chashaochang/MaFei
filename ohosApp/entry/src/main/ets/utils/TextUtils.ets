// [Start TextUtils]
import { TextSectionAttribute } from '../component/textexpand/Model';

const suffix: string = '... ';

export class TextUtils {
  /**
   * Gets the collapsed short paragraph string
   *
   * @param textSectionAttribute    Plain Text Attribute
   * @param lastSpan                Expand the collapse button text
   * @returns                       Short paragraph strings
   */
  // [Start TextUtils_getShortText]
  public static getShortText(textSectionAttribute: TextSectionAttribute, lastSpan: string,
    uiContext: UIContext): string {
    let text = textSectionAttribute.title
    const minLinesTextSize: SizeOptions | undefined = uiContext.getMeasureUtils().measureTextSize({
      textContent: text,
      fontSize: textSectionAttribute.fontSize,
      maxLines: textSectionAttribute.maxLines,
      wordBreak: WordBreak.BREAK_ALL,
      constraintWidth: textSectionAttribute.constraintWidth
    });
    const minHeight: Length | undefined = minLinesTextSize?.height;
    if (minHeight === undefined) {
      return '';
    }
    // Use the dichotomy to find strings that are exactly two lines in length
    let textStr: string[] = Array.from(text); //Split the string to avoid special characters and inconsistent sizes
    let leftCursor: number = 0;
    let rightCursor: number = textStr.length;
    let cursor: number = Math.floor(rightCursor / 2);
    let tempTitle: string = '';
    while (true) {
      tempTitle = text.substring(0, cursor) + suffix + lastSpan;
      const currentLinesTextSize: SizeOptions | undefined = uiContext?.getMeasureUtils().measureTextSize({
        textContent: tempTitle,
        fontSize: textSectionAttribute.fontSize,
        wordBreak: WordBreak.BREAK_ALL,
        constraintWidth: textSectionAttribute.constraintWidth
      });
      const currentLineHeight: Length | undefined = currentLinesTextSize?.height;
      if (currentLineHeight === undefined) {
        return '';
      }
      if (currentLineHeight > minHeight) {
        // The current character has exceeded two lines, continue to look to the left
        rightCursor = cursor;
        cursor = leftCursor + Math.floor((cursor - leftCursor) / 2);
      } else {
        // The current character is less than two lines, it may be OK, but you still need to look to the right
        leftCursor = cursor;
        cursor += Math.floor((rightCursor - cursor) / 2);
      }
      if (Math.abs(rightCursor - leftCursor) <= 1) {
        // The two pointers basically coincide, which means that they have been found
        break;
      }
    }
    return text.substring(0, cursor - 1) + suffix;//这块如果文字中有标点之类的计算可能不准确,会把更多挤一个字下去,所以-1容个错
  }

  // [End TextUtils_getShortText]

}

// [End TextUtils]