import relationalStore from '@ohos.data.relationalStore'

// 定义常量
const TABLE_NAME = 'Server'
const ID = 'id'
const HOSTNAME = 'hostname'
const SERVER_NAME = 'server_name'
const VERSION = 'version'
const LAST_USED_TIMESTAMP = 'last_used_timestamp'

// 定义实体类
class ServerEntity {
  id: number = 0
  hostname: string = ''
  servername: string = ''
  version: string = ''
  lastUsedTimestamp: number = 0

  // 构造函数
  constructor(hostname?: string) {
    if (hostname !== undefined) {
      this.hostname = hostname
      this.lastUsedTimestamp = Date.now()
    }
  }

  // 转换为数据库值映射
  toValuesBucket(): relationalStore.ValuesBucket {
    return {
      HOSTNAME: this.hostname,
      SERVER_NAME: this.servername,
      VERSION: this.version,
      LAST_USED_TIMESTAMP: this.lastUsedTimestamp
    }
  }

  // 从数据库结果创建实例
  static fromResultSet(resultSet: relationalStore.ResultSet): ServerEntity {
    const server = new ServerEntity()
    server.id = resultSet.getLong(resultSet.getColumnIndex(ID))
    server.hostname = resultSet.getString(resultSet.getColumnIndex(HOSTNAME))
    server.servername = resultSet.getString(resultSet.getColumnIndex(SERVER_NAME))
    server.version = resultSet.getString(resultSet.getColumnIndex(VERSION))
    server.lastUsedTimestamp = resultSet.getLong(resultSet.getColumnIndex(LAST_USED_TIMESTAMP))
    return server
  }

}

export default ServerEntity