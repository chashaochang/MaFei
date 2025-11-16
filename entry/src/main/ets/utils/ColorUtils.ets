export class ColorUtils {
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