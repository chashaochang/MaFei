import { ImageKnife, ImageKnifeData } from '@ohos/imageknifepro';
import Logger from './Logger';

export class ColorUtils {
  /**
   * 从图片URL提取左边缘的平均颜色
   * @param imageUrl 图片URL
   * @returns Promise<string> 返回十六进制颜色字符串 #RRGGBB
   */
  static async extractLeftEdgeColor(imageUrl: string): Promise<string> {

    try {
      // 下载图片
      let imageData: ImageKnifeData | undefined = await ImageKnife.getInstance().getCacheImage(imageUrl)
      if (!imageData) {
        Logger.error('ColorUtils', `Failed to find cache image: ${imageUrl}`);
        return '#0000FF'; // 默认蓝色
      }
      let pixelMap = imageData.pixelmapList?.[0]
      if (!pixelMap) {
        Logger.error('ColorUtils', `Failed to find cache image: ${imageUrl}`);
        return '#0000FF'; // 默认蓝色
      }
      const imageInfo = await pixelMap.getImageInfo();
      const width = imageInfo.size.width;
      const height = imageInfo.size.height;

      // 采样左边缘的像素（取左边10%的宽度）
      const sampleWidth = Math.max(1, Math.floor(width * 0.1));

      // 读取所有像素数据到缓冲区
      const buffer = new ArrayBuffer(width * height * 4); // RGBA格式，每像素4字节
      await pixelMap.readPixelsToBuffer(buffer);
      const pixels = new Uint8Array(buffer);

      let totalR = 0, totalG = 0, totalB = 0;
      let validPixels = 0;

      // 遍历左边缘的像素
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < sampleWidth; x++) {
          const index = (y * width + x) * 4;
          const r = pixels[index];
          const g = pixels[index + 1];
          const b = pixels[index + 2];
          const a = pixels[index + 3];

          // 只统计非透明像素
          if (a > 0) {
            totalR += r;
            totalG += g;
            totalB += b;
            validPixels++;
          }
        }
      }

      // 释放资源
      pixelMap.release();

      if (validPixels === 0) {
        return '#FFFFFF'; // 默认蓝色
      }

      // 计算平均颜色
      const avgR = Math.floor(totalR / validPixels);
      const avgG = Math.floor(totalG / validPixels);
      const avgB = Math.floor(totalB / validPixels);

      // 转换为十六进制
      const hexR = avgR.toString(16).padStart(2, '0');
      const hexG = avgG.toString(16).padStart(2, '0');
      const hexB = avgB.toString(16).padStart(2, '0');

      return `#${hexR}${hexG}${hexB}`.toUpperCase();
    } catch (error) {
      Logger.error('ColorUtils', `Error extracting edge color: ${error}`);
      return '#FFFFFF'; // 默认蓝色
    }
  }

  /**
   * 将十六进制颜色值转换为4x5颜色矩阵
   * @param hexColor 十六进制颜色字符串，支持格式：#RRGGBB, #AARRGGBB
   * @returns 4x5颜色矩阵数组 (20个元素的number数组)
   */
  static hexToColorMatrix(hexColor: string): number[] {
    // 验证输入格式
    if (!/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$/.test(hexColor)) {
      throw new Error('Invalid hex color format. Expected #RRGGBB or #AARRGGBB');
    }

    // 提取颜色通道值
    let rgba = hexColor.substring(1);
    if (rgba.length === 6) {
      rgba = 'FF' + rgba; // 添加默认Alpha通道
    }

    // 解析ARGB通道值(含归一化处理)
    const alpha = parseInt(rgba.substring(0, 2), 16) / 255;
    const red = parseInt(rgba.substring(2, 4), 16) / 255;
    const green = parseInt(rgba.substring(4, 6), 16) / 255;
    const blue = parseInt(rgba.substring(6, 8), 16) / 255;

    /**
     * 构造纯色替换矩阵：
     * [0, 0, 0, 0, red,    → 输出R通道 = 0*R + 0*G + 0*B + 0*A + 目标R
     *  0, 0, 0, 0, green,  → 输出G通道 = 0*R + 0*G + 0*B + 0*A + 目标G
     *  0, 0, 0, 0, blue,   → 输出B通道 = 0*R + 0*G + 0*B + 0*A + 目标B
     *  0, 0, 0, alpha, 0]  → 输出A通道 = 0*R + 0*G + 0*B + alpha*A + 0
     */
    return [
      0, 0, 0, 0, red,
      0, 0, 0, 0, green,
      0, 0, 0, 0, blue,
      0, 0, 0, alpha, 0
    ];
  }
}