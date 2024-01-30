package net.clynamic.common

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

interface Service<Request, Model, Update, Id> {
    suspend fun create(request: Request): Id
    suspend fun read(id: Id): Model?
    suspend fun page(page: Int? = null, size: Int? = null): List<Model>
    suspend fun update(id: Id, update: Update)
    suspend fun delete(id: Id)
}

abstract class ServiceTable<Id>(name: String = "") : Table(name) {
    abstract fun selector(id: Id): Op<Boolean>
    abstract fun toId(row: ResultRow): Id
}

abstract class IntServiceTable(name: String = "") : ServiceTable<Int>(name) {
    open val id = integer("id").autoIncrement()
    override val primaryKey: PrimaryKey?
        get() = PrimaryKey(id)

    override fun selector(id: Int): Op<Boolean> = this.id eq id
    override fun toId(row: ResultRow): Int = row[id]
}

abstract class SqlService<Request, Model, Update, Id, TableType : ServiceTable<Id>>(
    database: Database
) : Service<Request, Model, Update, Id> {

    abstract val table: TableType

    init {
        transaction(database) { SchemaUtils.create(table) }
    }

    internal suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    abstract fun toModel(row: ResultRow): Model
    internal fun allToModel(rows: List<ResultRow>): List<Model> {
        return rows.map(::toModel)
    }

    internal fun Query.toModel(): Model? = mapNotNull(::toModel).singleOrNull()
    internal fun Query.allToModel(): List<Model> = allToModel(toList())

    abstract fun fromRequest(statement: UpdateBuilder<*>, request: Request)
    abstract fun fromUpdate(statement: UpdateBuilder<*>, update: Update)

    override suspend fun create(request: Request): Id = dbQuery {
        table.insert {
            fromRequest(it, request)
        }.resultedValues!!.single().let(table::toId)
    }

    override suspend fun read(id: Id): Model? = dbQuery {
        table.select { table.selector(id) }
            .mapNotNull(::toModel)
            .singleOrNull()
    }

    internal suspend fun query(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null
    ): Query = dbQuery {
        val pageSize = size ?: 20
        val pageNumber = page ?: 0

        val query = table.selectAll()

        if (sort != null) {
            val column = table.columns.firstOrNull { it.name.equals(sort, ignoreCase = true) }
            if (column != null) {
                query.orderBy(column to (order ?: SortOrder.DESC))
            }
        }

        query.limit(pageSize, pageSize * pageNumber.toLong())
    }

    suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null
    ): List<Model> = dbQuery {
        query(page, size, sort, order).allToModel()
    }

    override suspend fun page(page: Int?, size: Int?): List<Model> = dbQuery {
        page(page, size, null, null)
    }

    override suspend fun update(id: Id, update: Update): Unit = dbQuery {
        table.update({ table.selector(id) }) {
            fromUpdate(it, update)
        }
    }

    override suspend fun delete(id: Id): Unit = dbQuery {
        table.deleteWhere { table.selector(id) }
    }
}

abstract class IntSqlService<Request, Model, Update, TableType : IntServiceTable>(
    database: Database
) : SqlService<Request, Model, Update, Int, TableType>(database)


class InstantAsISO : ColumnType() {
    override fun sqlType(): String = "VARCHAR"

    override fun valueFromDB(value: Any): Instant {
        return Instant.parse(value as String)
    }

    override fun notNullValueToDB(value: Any): Any {
        if (value is Instant) {
            return value.toString()
        }
        return super.notNullValueToDB(value)
    }
}

fun Table.instant(name: String): Column<Instant> = registerColumn(name, InstantAsISO())

class JsonAsText<T : Any>(private val typeRef: TypeReference<T>) : ColumnType() {
    private val objectMapper = jacksonObjectMapper()

    override fun sqlType(): String = "TEXT"

    override fun valueFromDB(value: Any): Any {
        return when (value) {
            is String -> objectMapper.readValue(value, typeRef)
            else -> value
        }
    }

    override fun notNullValueToDB(value: Any): Any {
        return objectMapper.writeValueAsString(value)
    }
}

fun <T : Any> Table.json(name: String, typeRef: TypeReference<T>): Column<T> =
    registerColumn(name, JsonAsText(typeRef))

class UpdateBuilderSets(private val statement: UpdateBuilder<*>) {
    infix fun <T> Column<T>.set(value: T?) {
        if (value != null) statement[this] = value
    }
}

fun UpdateBuilder<*>.setAll(block: UpdateBuilderSets.() -> Unit) {
    val dsl = UpdateBuilderSets(this)
    dsl.block()
}