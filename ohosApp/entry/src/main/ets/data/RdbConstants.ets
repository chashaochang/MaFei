interface RdbTableInterface {
  tableName: string;
  sqlCreate: string;
  columns: string[];
}

export class RdbConstants {
  /** 数据库名  */
  static readonly DB_NAME = 'jellyFin.db';
  /** 表名、建表sql语句、列名 */
  static readonly VERSION = 1;
  // server
  static readonly TABLE_SERVER: RdbTableInterface = {
    tableName: 'Server',
    sqlCreate: `CREATE TABLE IF NOT EXISTS Server (
      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      hostname TEXT NOT NULL UNIQUE,
      server_name TEXT NOT NULL,
      version TEXT NOT NULL,
      last_used_timestamp INTEGER NOT NULL
      )`,
    columns: ['id', 'hostname', 'server_name', 'version', 'last_used_timestamp']
  };
  // user
  static readonly TABLE_USER: RdbTableInterface = {
    tableName: 'User',
    sqlCreate: 'CREATE TABLE IF NOT EXISTS `User` (' +
      '`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ' +
      '`server_id` INTEGER NOT NULL, ' +
      '`user_id` INTEGER NOT NULL, ' +
      '`access_token` TEXT NOT NULL, ' +
      '`user_name` TEXT NOT NULL, ' +
      '`last_login_timestamp` INTEGER NOT NULL' +
      ')',
    columns: ['id', 'server_id', 'user_id', 'access_token', 'user_name', 'last_login_timestamp']
  };
}