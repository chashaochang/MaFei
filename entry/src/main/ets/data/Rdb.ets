import { relationalStore } from '@kit.ArkData';
import { RdbConstants } from './RdbConstants';
import { BusinessError } from '@kit.BasicServicesKit';

export default class Rdb {
  private rdbStore: relationalStore.RdbStore | null = null;
  private tableName: string;
  private sqlCreateTable: string;
  private columns: Array<string>;

  constructor(tableName: string, sqlCreateTable: string, columns: Array<string>) {
    this.tableName = tableName;
    this.sqlCreateTable = sqlCreateTable;
    this.columns = columns;
  }

  getRdbStore(context: Context) {
    const STORE_CONFIG: relationalStore.StoreConfig = {
      name: RdbConstants.DB_NAME, // 数据库文件名
      securityLevel: relationalStore.SecurityLevel.S1 // 数据库安全级别
    };

    relationalStore.getRdbStore(context, STORE_CONFIG, async (err, rdb) => {
      if (err) {
        console.error(`Get RdbStore failed, code is ${err.code},message is ${err.message}`);
        return;
      }
      this.rdbStore = rdb;
      let storeVersion = rdb.version;
      // 当数据库创建时，数据库默认版本为0
      if (storeVersion === 0) {
        try {
          await this.rdbStore.executeSql(this.sqlCreateTable);
          console.info(`Get ${RdbConstants.DB_NAME}.${this.tableName} RdbStore successfully.`);
        } catch (e) {
          const err = e as BusinessError;
          console.error(`Failed to execute sql. Code:${err.code}, message:${err.message}`);
        }
        // version = 1
        try {
          await rdb.execute(`ALTER TABLE Server ADD COLUMN server_name TEXT NOT NULL DEFAULT ""`);
          await rdb.execute(`ALTER TABLE Server ADD COLUMN version TEXT NOT NULL DEFAULT ""`);
          await rdb.execute(`ALTER TABLE User ADD COLUMN user_name TEXT NOT NULL DEFAULT ""`);
          storeVersion = 1;
          console.info("Upgrade store version from 0 to 1 success.")
        } catch (e) {
          const err = e as BusinessError;
          console.error(`Failed to execute sql. Code:${err.code}, message:${err.message}`);
        }
      }
      // 版本变化时，会触发Upgrade
      rdb.version = storeVersion;
    });
  }

  private deleteRdbStore(callback: Function = () => {}) {
    let context: Context = getContext(this) as Context;

    relationalStore.deleteRdbStore(context, RdbConstants.DB_NAME, (err: BusinessError) => {
      if (err) {
        console.error(`Delete RdbStore failed, code is ${err.code},message is ${err.message}`);
        return;
      }
      this.rdbStore = null;
      console.info('Delete RdbStore successfully.');
    })
  }

  insertData(data: relationalStore.ValuesBucket, callback: Function = () => {}) {

    let resFlag: boolean = false;
    const valueBucket: relationalStore.ValuesBucket = data;
    if (this.rdbStore) {
      this.rdbStore.insert(this.tableName, valueBucket, (err, ret) => {
        if (err) {
          console.error(`Failed to insert data. Code:${err.code}, message:${err.message}`);
          return;
        }
        console.info(`Succeeded in inserting data. ret:${ret}`);
      });
    }
  }

  deleteData(predicates: relationalStore.RdbPredicates, callback: Function = () => {}) {
    let resFlag: boolean = false;
    if (this.rdbStore) {
      this.rdbStore.delete(predicates, (err, ret) => {
        if (err) {
          console.error(`Delete failed, code is ${err.code},message is ${err.message}`);
          return;
        }
        console.info(`Delete rows: ${ret}`);
      });
    }
  }

  updateData(predicates: relationalStore.RdbPredicates, data: relationalStore.ValuesBucket, callback: Function = () => {}) {

    let resFlag: boolean = false;
    const valueBucket: relationalStore.ValuesBucket = data;
    if (this.rdbStore) {
      this.rdbStore.update(valueBucket, predicates, (err, ret) => {
        if (err) {
          console.error(`Updated failed, code is ${err.code},message is ${err.message}`);
          return;
        }
        console.info(`Updated row count: ${ret}`);
      });
    }
  }

  query(predicates: relationalStore.RdbPredicates, callback: Function = () => {}) {

    if (this.rdbStore) {
      this.rdbStore.query(predicates, this.columns, (err, resultSet) => {
        if (err) {
          console.error(`Failed to query data. Code:${err.code}, message:${err.message}`);
          return;
        }
        console.info(`ResultSet column names: ${resultSet.columnNames}, row count: ${resultSet.rowCount}`);

        if(resultSet.rowCount == -1){
          console.info("rowCount=-1")
        }
      });
    }
  }


  insertDataSync(data: relationalStore.ValuesBucket) : number {
    const valueBucket: relationalStore.ValuesBucket = data;
    if (this.rdbStore) {
      try {
        return this.rdbStore.insertSync(this.tableName, valueBucket);
      } catch (error) {
        return 0
      }
    }
    return 0;
  }

  updateDataSync(predicates: relationalStore.RdbPredicates, data: relationalStore.ValuesBucket) : number {

    const valueBucket: relationalStore.ValuesBucket = data;
    if (this.rdbStore) {
      return this.rdbStore.updateSync(valueBucket, predicates);
    }
    return 0;
  }

  deleteDataSync(predicates: relationalStore.RdbPredicates) : number {
    if (this.rdbStore) {
      try {
        return this.rdbStore.deleteSync(predicates);
      } catch (error) {
        return -1
      }
    }
    return -1
  }

  queryDataSync(predicates: relationalStore.RdbPredicates) :  relationalStore.ResultSet | undefined {

    if (this.rdbStore) {
      return this.rdbStore.querySync(predicates, this.columns);
    }

    return undefined;
  }
}
