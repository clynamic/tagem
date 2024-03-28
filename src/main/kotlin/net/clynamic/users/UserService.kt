package net.clynamic.users

import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.PageOptions
import net.clynamic.common.setAll
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement

class UsersService(database: Database) :
    IntSqlService<UserRequest, User, UserUpdate, UsersService.Users, UserPageOptions>(database) {
    object Users : IntServiceTable() {
        override fun getIdColumn(): Column<Int> = integer("id")
        val name = varchar("name", 32)
        val rank = enumeration<UserRank>("rank")
        val strikes = integer("strikes")
        val isBanned = bool("is_banned")
    }

    override val table: Users
        get() = Users

    override fun toModel(row: ResultRow): User {
        return User(
            id = row[Users.id],
            name = row[Users.name],
            rank = row[Users.rank],
            strikes = row[Users.strikes],
            isBanned = row[Users.isBanned]
        )
    }

    override fun fromUpdate(statement: UpdateStatement, update: UserUpdate) {
        statement.setAll {
            Users.name set update.name
            Users.rank set update.rank
            Users.strikes set update.strikes
            Users.isBanned set update.isBanned
        }
    }

    override fun fromRequest(statement: InsertStatement<*>, request: UserRequest) {
        statement.setAll {
            Users.id set request.id
            Users.name set request.name
            Users.rank set request.rank
            Users.strikes set request.strikes
            Users.isBanned set request.isBanned
        }
    }
}

typealias UserPageOptions = PageOptions