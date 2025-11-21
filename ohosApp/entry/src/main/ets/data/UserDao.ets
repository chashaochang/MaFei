import Rdb from "./Rdb";
import { RdbConstants } from "./RdbConstants";
import { relationalStore } from "@kit.ArkData";
import ServerEntity from "./ServerEntity";
import UserEntity from "./UserEntity";
import { when } from "@ohos/hypium";
import { ApiClient } from "../network/ApiClient";

export class UserDao {
  private userTable = new Rdb(
    RdbConstants.TABLE_USER.tableName,
    RdbConstants.TABLE_USER.sqlCreate,
    RdbConstants.TABLE_USER.columns
  )

  constructor(context: Context) {
    this.userTable.getRdbStore(context)
  }

  insertDataSync(valuesBucket: relationalStore.ValuesBucket): number {
    return this.userTable.insertDataSync(valuesBucket)
  }

  deleteDataSync(valuesBucket: relationalStore.ValuesBucket): number {
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_USER.tableName)
    predicates.equalTo('id', valuesBucket['ID'])
    return this.userTable.deleteDataSync(predicates)
  }

  updateDataSync(valuesBucket: relationalStore.ValuesBucket): number {
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_USER.tableName)
    predicates.equalTo('id', valuesBucket['ID'])
    return this.userTable.updateDataSync(predicates, valuesBucket)
  }

  queryDataSync(valuesBucket: relationalStore.ValuesBucket): relationalStore.ResultSet | undefined {
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_USER.tableName)
    predicates.equalTo('id', valuesBucket['ID'])
    return this.userTable.queryDataSync(predicates)
  }

  queryAllDataSync(): UserEntity[] {
    let users: UserEntity[] = []
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_USER.tableName)
    let res = this.userTable.queryDataSync(predicates)
    if (res) {
      try {
        while (res.goToNextRow()) {
          let user = UserEntity.fromResultSet(res)
          users.push(user)
        }
      } catch (error) {

      }
    }
    return users
  }

  upsert(serverId: number, userId: string, accessToken: string, username?: string): number {
    // 查询现有用户
    const user = this.getByUserId(serverId, userId)
    if (user) {
      // 如果用户存在，更新访问令牌
      user.accessToken = accessToken
      if (username) {
        user.username = username
      }
      this.updateDataSync(user.toValuesBucket())
      return user.id
    } else {
      return this.insertDataSync({
        SERVER_ID: serverId,
        USER_ID: userId,
        ACCESS_TOKEN: accessToken,
        USER_NAME: username!,
        LAST_LOGIN_TIMESTAMP: Date.now()
      })
    }
  }

  // 根据服务器ID和用户ID获取用户
  getByUserId(serverId: number, userId: string): UserEntity | null {
    try {
      // 实际实现取决于数据存储方式，这里以伪代码示意
      let predicates: relationalStore.RdbPredicates =
        new relationalStore.RdbPredicates(RdbConstants.TABLE_USER.tableName)
      predicates.equalTo('server_id', serverId)
      predicates.equalTo('user_id', userId)
      let resultSet: relationalStore.ResultSet | undefined = this.userTable.queryDataSync(predicates)
      let result: UserEntity | null = null
      if (resultSet) {
        while (resultSet.goToNextRow()) {
          result = UserEntity.fromResultSet(resultSet)
        }
      }
      return result
    } catch (error) {
      console.error('Get user by ID failed', error);
      return null;
    }
  }

  //相同服务器不同域名有可能有相同的userId
  deleteUser() {
    if (ApiClient.Instance().userId !== undefined) {
      this.deleteDataSync({
        USER_ID: ApiClient.Instance().userId
      })
    }
  }
}