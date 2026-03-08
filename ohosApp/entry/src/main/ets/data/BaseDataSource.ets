
/**
 * List 数据源
 * @version 1.0.0
 */
@Observed
export class BaseDataSource<T> implements IDataSource {
  private listeners: DataChangeListener[] = [];
  private listData: Array<T> = []

  public totalCount(): number {
    return this.listData.length;
  }

  public getData(index: number): T | undefined {
    // 检查 index 是否在有效范围内
    if (index < 0 || index >= this.listData.length) {
      console.error(`Index ${index} is out of bounds for listData of length ${this.listData.length}`);
      return undefined;
    }
    // 返回指定索引的数据
    return this.listData[index];
  }

  registerDataChangeListener(listener: DataChangeListener): void {
    if (this.listeners.indexOf(listener) < 0) {
      this.listeners.push(listener);
    }
  }

  unregisterDataChangeListener(listener: DataChangeListener): void {
    const position = this.listeners.indexOf(listener);
    if (position >= 0) {
      this.listeners.splice(position, 1);
    }
  }

  notifyDataReload(): void {
    this.listeners.forEach((listener: DataChangeListener) => {
      listener.onDataReloaded();
    })
  }

  notifyDataAdd(index: number): void {
    this.listeners.forEach((listener: DataChangeListener) => {
      listener.onDataAdd(index);
    })
  }

  notifyDataChange(index: number): void {
    this.listeners.forEach((listener: DataChangeListener) => {
      listener.onDataChange(index);
    })
  }

  notifyDataDelete(index: number): void {
    this.listeners.forEach((listener: DataChangeListener) => {
      listener.onDataDelete(index);
    })
  }

  notifyDataMove(from: number, to: number): void {
    this.listeners.forEach((listener: DataChangeListener) => {
      listener.onDataMove(from, to);
    })
  }

  notifyDatasetChange(operations: DataOperation[]): void {
    this.listeners.forEach(listener => {
      listener.onDatasetChange(operations);
    })
  }
}