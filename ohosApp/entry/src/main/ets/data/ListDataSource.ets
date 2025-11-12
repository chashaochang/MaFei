/**
 * LazyLoad Class
 */
import { ArrayList } from '@kit.ArkTS';

/**
 * List 数据源
 * @version 1.0.0
 */
@Observed
export class ListDataSource<T> implements IDataSource {
  private listeners: DataChangeListener[] = [];
  private listData: ArrayList<T> = new ArrayList();
  private dataLengthChangeListener: ListDataSourceListener[] = [];

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

  public getDataList(): ArrayList<T> {
    return this.listData;
  }

  public addDataByIndex(index: number, value: T): void {
    this.listData.insert(value!, index);
    this.notifyDataLengthChange();
  }

  public addData(value: T): void {
    this.listData.add(value);
    this.notifyDataLengthChange();
  }

  public addArray(values: T[]): void {
    for (let index = 0; index < values.length; index++) {
      this.addData(values[index])
    }
  }

  public removeData(value: T): void {
    this.listData.remove(value);
    this.notifyDataLengthChange();
  }

  public clearDataAndNotify(): void {
    this.listData.clear();
    this.notifyDataLengthChange();
  }

  public replaceData(values: ArrayList<T>): void {
    this.listData.clear();
    if (values !== undefined && values !== null && !values.isEmpty()) {
      for (let valuesElement of values) {
        this.listData.add(valuesElement);
      }
    }
    this.notifyDataLengthChange();
  }

  public notifyDataLengthChange(): void {
    this.dataLengthChangeListener.forEach((listener: ListDataSourceListener) => {
      listener.onDataLengthChange(this.totalCount());
    })
  }

  public registerDataLengthChangeListener(listener: ListDataSourceListener): void {
    if (this.dataLengthChangeListener.indexOf(listener) < 0) {
      this.dataLengthChangeListener.push(listener);
    }
  }

  public unregisterDataLengthChangeListener(listener: ListDataSourceListener): void {
    const position = this.dataLengthChangeListener.indexOf(listener);
    if (position >= 0) {
      this.dataLengthChangeListener.splice(position, 1);
    }
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

export interface ListDataSourceListener {

  /**
   * 数组长度变化
   * @param length 当前长度
   */
  onDataLengthChange: (length: number) => void;
}
