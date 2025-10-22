import { CommentData } from "./CommentResponseV2";
import { util, xml } from "@kit.ArkTS";

// 全局变量用于存储解析状态
let commentDataList: CommentData[] = [];
let currentTagName: string = '';

// 正确写法：定义接口
interface ParseResult {
  formattedP: string;
  cid: number;
}

function parseP(pValue: string): ParseResult {
  let parts = pValue.split(',');
  if (parts.length >= 8) {
    let time = parts[0];
    let mode = parts[1];
    let color = parts[3];
    let userId = parts[7];

    let formattedP = `${time},${mode},${color},${userId}`;
    let cidMatch = userId.match(/\d+/);
    let cid = cidMatch ? parseInt(cidMatch[0]) : 0;

    return { formattedP, cid };
  }

  return { formattedP: pValue, cid: 0 };
}

// 标签值回调函数
function tagFunc(name: string, _value: string): boolean {
  currentTagName = name;
  return true;
}

// 属性回调函数
function attFunc(name: string, value: string): boolean {
  if (currentTagName === 'd' && name === 'p') {
    // 解析p属性
    let res = parseP(value);

    // 创建CommentData对象
    let commentData: CommentData = {
      cid: res.cid,
      p: res.formattedP,
      m: '' // 文本内容会在后续处理中填入
    };
    commentDataList.push(commentData);
  }
  return true;
}

// 令牌回调函数
function tokenFunc(name: xml.EventType, value: xml.ParseInfo): boolean {
  // 处理文本内容
  if (name === xml.EventType.TEXT) {
    let text = value.getText().trim();
    if (text && commentDataList.length > 0) {
      // 将文本内容赋值给最后一个CommentData的m属性
      let lastComment = commentDataList[commentDataList.length - 1];
      if (!lastComment.m) { // 确保不重复赋值
        lastComment.m = text;
      }
    }
  }
  return true;
}

// 解析函数
export function parseXmlToCommentData(xmlString: string): CommentData[] {
  // 重置全局变量
  commentDataList = [];
  currentTagName = '';

  let textEncoder: util.TextEncoder = new util.TextEncoder();
  let arrBuffer: Uint8Array = textEncoder.encodeInto(xmlString);
  let xmlParser: xml.XmlPullParser = new xml.XmlPullParser(arrBuffer.buffer as object as ArrayBuffer, 'UTF-8');

  let options: xml.ParseOptions = {
    supportDoctype: true,
    ignoreNameSpace: true,
    tagValueCallbackFunction: tagFunc,
    attributeValueCallbackFunction: attFunc,
    tokenValueCallbackFunction: tokenFunc
  };

  xmlParser.parseXml(options);
  return [...commentDataList]; // 返回副本
}