import relationalStore from '@ohos.data.relationalStore'

// 定义常量
const TABLE_NAME = 'Danmu'
const ID = 'id'
const EPISODE_ID = 'episode_id'
const DATA = 'data'
const TIMESTAMP = 'timestamp'

// 定义实体类
class DanmuEntity {
  id: number = 0
  episode_id: number = 0
  data: string | null = null
  timestamp: number = 0

  // 构造函数
  constructor(
    episodeId?: number,
    data?: string,
  ) {
    if (episodeId !== undefined && data !== undefined) {
      this.episode_id = episodeId
      this.data = data
      this.timestamp = Date.now()
    }
  }

  // 创建表的SQL语句
  static getCreateTableSql(): string {
    return `
      CREATE TABLE IF NOT EXISTS ${TABLE_NAME} (
        ${ID} INTEGER PRIMARY KEY AUTOINCREMENT,
        ${EPISODE_ID} INTEGER NOT NULL,
        ${DATA} TEXT NOT NULL,
        ${TIMESTAMP} INTEGER NOT NULL,
        UNIQUE(${EPISODE_ID})
      )
    `
  }

  // 转换为数据库值映射
  toValuesBucket(): relationalStore.ValuesBucket {
    return {
      EPISODE_ID: this.episode_id,
      DATA: this.data,
      TIMESTAMP: this.timestamp
    }
  }

  // 从数据库结果创建实例
  static fromResultSet(resultSet: relationalStore.ResultSet): DanmuEntity {
    const user = new DanmuEntity()
    user.id = resultSet.getLong(resultSet.getColumnIndex(ID))
    user.episode_id = resultSet.getLong(resultSet.getColumnIndex(EPISODE_ID))
    user.data = resultSet.getString(resultSet.getColumnIndex(DATA))
    user.timestamp = resultSet.getLong(resultSet.getColumnIndex(TIMESTAMP))
    return user
  }
}

export default DanmuEntity