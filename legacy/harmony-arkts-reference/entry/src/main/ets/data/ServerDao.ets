import Rdb from "./Rdb";
import { RdbConstants } from "./RdbConstants";
import { relationalStore } from "@kit.ArkData";
import ServerEntity from "./ServerEntity";

export class ServerDao {
  private serverTable: Rdb

  constructor(rdbStore: relationalStore.RdbStore) {
    this.serverTable = new Rdb(rdbStore, RdbConstants.TABLE_SERVER.tableName, RdbConstants.TABLE_SERVER.columns)
  }

  insertDataSync(valuesBucket: relationalStore.ValuesBucket): number {
    return this.serverTable.insertDataSync(valuesBucket)
  }

  deleteDataSync(valuesBucket: relationalStore.ValuesBucket): number {
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_SERVER.tableName)
    predicates.equalTo('id', valuesBucket.id)
    return this.serverTable.deleteDataSync(predicates)
  }

  updateDataSync(valuesBucket: relationalStore.ValuesBucket): number {
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_SERVER.tableName)
    predicates.equalTo('id', valuesBucket.id)
    return this.serverTable.updateDataSync(predicates, valuesBucket)
  }

  queryDataSync(valuesBucket: relationalStore.ValuesBucket): relationalStore.ResultSet | undefined {
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_SERVER.tableName)
    predicates.equalTo('id', valuesBucket.id)
    return this.serverTable.queryDataSync(predicates)
  }

  getServer(serverId: number): ServerEntity | null {
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_SERVER.tableName)
    predicates.equalTo('id', serverId)
    let resultSet = this.serverTable.queryDataSync(predicates);
    let result: ServerEntity | null = null;
    if (resultSet) {
      while (resultSet.goToNextRow()) {
        result = ServerEntity.fromResultSet(resultSet)
      }
      return result
    }
    return null;
  }

  getServerByHostname(hostname: string): ServerEntity | null {
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_SERVER.tableName)
    predicates.equalTo('hostname', hostname)
    let resultSet = this.serverTable.queryDataSync(predicates);
    let result: ServerEntity | null = null;
    if (resultSet) {
      while (resultSet.goToNextRow()) {
        result = ServerEntity.fromResultSet(resultSet)
      }
      return result
    }
    return null;
  }

  insert(hostname: string, serverName: string, version: string): number {
    let server = new ServerEntity(hostname)
    server.servername = serverName
    server.version = version
    return this.serverTable.insertDataSync(server.toValuesBucket())
  }

  getAllServers(): ServerEntity[] {
    let predicates: relationalStore.RdbPredicates =
      new relationalStore.RdbPredicates(RdbConstants.TABLE_SERVER.tableName)
    let resultSet = this.serverTable.queryDataSync(predicates)
    let res: ServerEntity[] = []
    if (resultSet) {
      while (resultSet.goToNextRow()) {
        let item: ServerEntity = ServerEntity.fromResultSet(resultSet)
        res.push(item)
      }
    }
    return res
  }
}
