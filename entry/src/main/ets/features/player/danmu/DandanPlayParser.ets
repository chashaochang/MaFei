import {
  BaseDanmakuParser, Danmakus,
  DanmakuUtils,
  IDanmakus, SpecialDanmaku, ST_BY_TIME } from "@ohos/danmakuflamemaster";
import {
  IDataSource
} from "@ohos/danmakuflamemaster/src/main/ets/components/common/master/flame/danmaku/danmaku/parser/IDataSource";
import { CommentData } from "./CommentResponseV2";

export class DanDanPlayParser extends BaseDanmakuParser{

  protected mDispScaleX: number =0;
  protected mDispScaleY: number = 0;

  protected parse(): IDanmakus | ESObject {
    class danmakus {
      sortType: number = 0
      duplicateMergingEnabled: boolean = false
      baseComparator: ESObject
    }
    if(this.mDataSource !== null){
      let args:danmakus = { sortType: ST_BY_TIME,
        duplicateMergingEnabled: false,
        baseComparator: this.mContext.getBaseComparator()}
      let result: Danmakus = new Danmakus((args) as ESObject)
      this.mDataSource.data().forEach((commentData:CommentData) => {
        let args = commentData.p.split(',')//弹幕参数（出现时间,模式,颜色,用户ID）"241.47,1,16777215,[BiliBili]e3193a8",
        let time = args[0]
        let mode = args[1]
        let color = args[2]
        let item:SpecialDanmaku = this.mContext.mDanmakuFactory.createDanmakuByContext(Number(mode), this.mContext)
        if (item != null) {
          item.setTime(Number.parseFloat(time) * 1000)
          item.textSize = 26 * (this.mDispDensity * 0.8)
          item.textColor = Number(color)
          //item.textShadowColor = Number(sourceItem.textShadowColor)


          item.text = commentData.m
          item.index = Number(commentData.cid)
          DanmakuUtils.fillText(item, item.text);
          // if (Number(sourceItem.DanmakuType) == BaseDanmaku.TYPE_SPECIAL) {
          //   item.duration = new Duration(Number.parseFloat(sourceItem.duration))
          //   item.rotationZ = Number.parseFloat(sourceItem.rotationZ)
          //   item.rotationY = Number.parseFloat(sourceItem.rotationY)
          //
          //   this.mContext.mDanmakuFactory.fillTranslationData(item,
          //     Number.parseFloat(sourceItem.beginX),
          //     Number.parseFloat(sourceItem.beginY),
          //     Number.parseFloat(sourceItem.endX),
          //     Number.parseFloat(sourceItem.endY),
          //     Number.parseFloat(sourceItem.translationDuration),
          //     Number.parseFloat(sourceItem.translationStartDelay),
          //     this.mDispScaleX, this.mDispScaleY)
          //   this.mContext.mDanmakuFactory.fillAlphaData(item,
          //     Number(sourceItem.beginAlpha),
          //     Number(sourceItem.endAlpha),
          //     Number.parseFloat(sourceItem.alphaDuration));
          //   (item as SpecialDanmaku).isQuadraticEaseOut = Boolean((sourceItem.isQuadraticEaseOut)as boolean)
          //
          // }

        }

        if (item.text != null) {
          if (item.duration != null) {
            item.setTimer(this.mTimer);
            item.flags = this.mContext.mGlobalFlagValues
            result.addItem(item)
          }
        }
      })
      return result
    }
  }

  public load(source: IDataSource<CommentData>): BaseDanmakuParser {
    this.mDataSource = source
		return this;
	}
}