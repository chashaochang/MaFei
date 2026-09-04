import { relationalStore } from '@kit.ArkData';
import { BusinessError } from '@kit.BasicServicesKit';
import { RdbConstants } from './RdbConstants';

export default class Rdb {
  private static initializationPromise?: Promise<relationalStore.RdbStore>;
  private rdbStore: relationalStore.RdbStore;
  private tableName: string;
  private columns: Array<string>;

  constructor(rdbStore: relationalStore.RdbStore, tableName: string, columns: Array<string>) {
    this.rdbStore = rdbStore;
    this.tableName = tableName;
    this.columns = columns;
  }

  static initialize(context: Context): Promise<relationalStore.RdbStore> {
    if (!Rdb.initializationPromise) {
      const initialization = Rdb.openAndMigrate(context);
      Rdb.initializationPromise = initialization.then((rdbStore: relationalStore.RdbStore) => rdbStore,
        (error: Error) => {
          Rdb.initializationPromise = undefined;
          throw error;
        });
    }
    return Rdb.initializationPromise;
  }

  private static async openAndMigrate(context: Context): Promise<relationalStore.RdbStore> {
    const storeConfig: relationalStore.StoreConfig = {
      name: RdbConstants.DB_NAME,
      securityLevel: relationalStore.SecurityLevel.S1
    };
    let stage = 'open';
    let rdbStore: relationalStore.RdbStore | null = null;
    let transactionStarted = false;
    try {
      rdbStore = await relationalStore.getRdbStore(context, storeConfig);
      stage = 'version-check';
      if (rdbStore.version > RdbConstants.VERSION) {
        throw new Error(`Database version ${rdbStore.version} is newer than supported version ${RdbConstants.VERSION}.`);
      }

      stage = 'begin';
      rdbStore.beginTransaction();
      transactionStarted = true;

      stage = 'query-server';
      const serverColumns = Rdb.getTableColumns(rdbStore, RdbConstants.TABLE_SERVER.tableName);
      stage = 'query-user';
      const userColumns = Rdb.getTableColumns(rdbStore, RdbConstants.TABLE_USER.tableName);

      stage = 'create-server';
      rdbStore.executeSync(RdbConstants.TABLE_SERVER.sqlCreate);
      stage = 'create-user';
      rdbStore.executeSync(RdbConstants.TABLE_USER.sqlCreate);

      if (serverColumns.length > 0 && serverColumns.indexOf('server_name') === -1) {
        stage = 'alter-server-name';
        rdbStore.executeSync("ALTER TABLE Server ADD COLUMN server_name TEXT NOT NULL DEFAULT ''");
      }
      if (serverColumns.length > 0 && serverColumns.indexOf('version') === -1) {
        stage = 'alter-server-version';
        rdbStore.executeSync("ALTER TABLE Server ADD COLUMN version TEXT NOT NULL DEFAULT ''");
      }

      if (userColumns.length > 0 && userColumns.indexOf('user_name') === -1) {
        stage = 'alter-user-name';
        rdbStore.executeSync("ALTER TABLE `User` ADD COLUMN user_name TEXT NOT NULL DEFAULT ''");
      }

      stage = 'commit';
      rdbStore.commit();
      transactionStarted = false;

      stage = 'verify-server';
      const migratedServerColumns = Rdb.getTableColumns(rdbStore, RdbConstants.TABLE_SERVER.tableName);
      Rdb.assertTableColumns(RdbConstants.TABLE_SERVER.tableName, migratedServerColumns,
        RdbConstants.TABLE_SERVER.columns);
      stage = 'verify-user';
      const migratedUserColumns = Rdb.getTableColumns(rdbStore, RdbConstants.TABLE_USER.tableName);
      Rdb.assertTableColumns(RdbConstants.TABLE_USER.tableName, migratedUserColumns,
        RdbConstants.TABLE_USER.columns);

      stage = 'set-version';
      if (rdbStore.version < RdbConstants.VERSION) {
        rdbStore.version = RdbConstants.VERSION;
      }
      console.info(`Initialized ${RdbConstants.DB_NAME} at version ${RdbConstants.VERSION}.`);
      return rdbStore;
    } catch (error) {
      const initializationError = error as BusinessError;
      const throwableError = error as Error;
      if (transactionStarted && rdbStore) {
        try {
          rdbStore.rollBack();
        } catch (rollbackError) {
          console.error(`Failed to roll back ${RdbConstants.DB_NAME}: ${JSON.stringify(rollbackError)}`);
        }
      }
      console.error(`Database initialization failed at ${stage}. Code:${initializationError.code}, ` +
        `message:${initializationError.message}`);
      throw throwableError;
    }
  }

  private static getTableColumns(rdbStore: relationalStore.RdbStore, tableName: string): Array<string> {
    const resultSet = rdbStore.querySqlSync(`PRAGMA table_info(\`${tableName}\`)`);
    const columns: Array<string> = [];
    try {
      const nameColumnIndex = resultSet.getColumnIndex('name');
      while (resultSet.goToNextRow()) {
        columns.push(resultSet.getString(nameColumnIndex));
      }
    } finally {
      resultSet.close();
    }
    return columns;
  }

  private static assertTableColumns(tableName: string, actualColumns: Array<string>,
    expectedColumns: Array<string>): void {
    const missingColumns = expectedColumns.filter((column: string) => actualColumns.indexOf(column) === -1);
    if (missingColumns.length > 0) {
      throw new Error(`${tableName} is missing required columns: ${missingColumns.join(', ')}`);
    }
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
