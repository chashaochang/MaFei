import { relationalStore } from "@kit.ArkData"
import Rdb from "./Rdb"
import { ServerDao } from "./ServerDao"
import { UserDao } from "./UserDao"

export class DBManager {
  private serverDao?: ServerDao
  private userDao?: UserDao
  private initPromise?: Promise<void>
  private static instance?: DBManager

  static getInstance(): DBManager {
    if (!DBManager.instance) {
      DBManager.instance = new DBManager()
    }
    return DBManager.instance
  }

  init(context: Context): Promise<void> {
    if (!this.initPromise) {
      const initialization = this.initialize(context)
      this.initPromise = initialization.then(() => {}, (error: Error) => {
        this.initPromise = undefined
        throw error
      })
    }
    return this.initPromise
  }

  private async initialize(context: Context): Promise<void> {
    const rdbStore: relationalStore.RdbStore = await Rdb.initialize(context)
    const serverDao = new ServerDao(rdbStore)
    const userDao = new UserDao(rdbStore)
    this.serverDao = serverDao
    this.userDao = userDao
  }

  getServerDao(): ServerDao {
    if (!this.serverDao) {
      throw new Error('DBManager is not init!')
    }
    return this.serverDao
  }

  getUserDao(): UserDao {
    if (!this.userDao) {
      throw new Error('DBManager is not init!')
    }
    return this.userDao
  }
}
