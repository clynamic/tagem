package net.clynamic.common

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.media.Schema
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
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

abstract class Page<T> {
    @get:Schema(required = true)
    abstract val items: List<T>

    @get:Schema(required = true)
    abstract val total: Long

    @get:Schema(required = true)
    abstract val page: Int

    @get:Schema(required = true)
    abstract val pages: Int
}

data class ServicePage<T>(
    override val items: List<T>,
    override val total: Long,
    override val page: Int,
    override val pages: Int,
) : Page<T>()

abstract class PageOptionsBase<T : PageOptionsBase<T>> {
    abstract val page: Int?
    abstract val size: Int?
    abstract val sort: String?
    abstract val order: SortOrder?
    abstract val limited: Boolean
    open val defaultPage: Int = 1
    open val defaultSize: Int = 40

    abstract fun duplicate(
        page: Int? = this.page,
        size: Int? = this.size,
        sort: String? = this.sort,
        order: SortOrder? = this.order,
        limited: Boolean = this.limited,
    ): T
}

val PageOptionsBase<*>.pageOrDefault
    get() = max(1, this.page ?: this.defaultPage)
val PageOptionsBase<*>.sizeOrDefault
    get() = max(0, this.size ?: this.defaultSize)
val PageOptionsBase<*>.orderOrDefault
    get() = this.order ?: SortOrder.DESC
val PageOptionsBase<*>.offset
    get() = (this.sizeOrDefault * (this.pageOrDefault - 1)).toLong()

data class PageOptions(
    override val page: Int? = null,
    override val size: Int? = null,
    override val sort: String? = null,
    override val order: SortOrder? = null,
    override val limited: Boolean = true,
) : PageOptionsBase<PageOptions>() {
    override fun duplicate(
        page: Int?,
        size: Int?,
        sort: String?,
        order: SortOrder?,
        limited: Boolean,
    ) = copy(
        page = page,
        size = size,
        sort = sort,
        order = order,
        limited = limited,
    )
}

interface Service<Request, Model, Update, Id, PageOptions> {
    suspend fun create(request: Request): Id
    suspend fun read(id: Id): Model = readOrNull(id) ?: throw NoSuchRecordException(id)
    suspend fun readOrNull(id: Id): Model?
    suspend fun page(options: PageOptions): Page<Model>
    suspend fun update(id: Id, update: Update)
    suspend fun delete(id: Id)
}

class NoSuchRecordException(id: Any?, type: String? = null) :
    NoSuchElementException("No ${type ?: "record"} found for id: $id")

abstract class ServiceTable<Id>(name: String = "") : Table(name) {
    abstract fun selector(id: Id): Op<Boolean>
    abstract fun toId(row: ResultRow): Id
}

abstract class IntServiceTable(name: String = "") : ServiceTable<Int>(name) {
    val id: Column<Int>
        get() = _id

    // We need to use a backing field to allow for overriding the id column
    // And it cannot be late init because we need access to the Table functions
    @Suppress("LeakingThis")
    private var _id: Column<Int> = getIdColumn()

    protected open fun getIdColumn(): Column<Int> = integer("id").autoIncrement()

    override val primaryKey: PrimaryKey?
        get() = PrimaryKey(id)

    override fun selector(id: Int): Op<Boolean> = this.id eq id
    override fun toId(row: ResultRow): Int = row[id]
}

abstract class SqlService<Request, Model, Update, Id, TableType : ServiceTable<Id>, PageOptions : PageOptionsBase<PageOptions>>(
    database: Database,
) : Service<Request, Model, Update, Id, PageOptions> {

    abstract val table: TableType

    init {
        transaction(database) { SchemaUtils.create(table) }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    abstract fun toModel(row: ResultRow): Model

    internal fun Query.toModel(): Model? = toModelList().singleOrNull()
    internal fun Query.toModelList(): List<Model> = mapNotNull(::toModel)

    open suspend fun query(options: PageOptions): Query = dbQuery {
        val query = table.selectAll()

        options.sort?.let { sort ->
            val column = table.columns.firstOrNull { it.name.equals(sort, ignoreCase = true) }
            if (column != null) {
                query.orderBy(column to options.orderOrDefault)
            }
        }

        if (options.limited) {
            query.limit(options.sizeOrDefault, options.offset)
        }

        query
    }

    override suspend fun page(options: PageOptions): Page<Model> = dbQuery {
        val items = query(options).toModelList()
        val count = query(options.duplicate(limited = false)).count()
        ServicePage(
            items = items,
            total = count,
            page = options.pageOrDefault,
            pages = if (items.isEmpty()) 0 else
                ceil((count / items.size).toDouble()).toInt()
        )
    }

    abstract fun fromRequest(statement: InsertStatement<*>, request: Request)
    abstract fun fromUpdate(statement: UpdateStatement, update: Update)

    override suspend fun create(request: Request): Id = dbQuery {
        table.insert {
            fromRequest(it, request)
        }.resultedValues!!.single().let(table::toId)
    }

    override suspend fun read(id: Id): Model =
        readOrNull(id) ?: throw NoSuchRecordException(id, table.tableName)

    override suspend fun readOrNull(id: Id): Model? = dbQuery {
        table.select { table.selector(id) }
            .mapNotNull(::toModel)
            .singleOrNull()
    }

    override suspend fun update(id: Id, update: Update): Unit = dbQuery {
        table.update({ table.selector(id) }) {
            fromUpdate(it, update)
        }.let { if (it == 0) throw NoSuchRecordException(id, table.tableName) }
    }

    override suspend fun delete(id: Id): Unit = dbQuery {
        table.deleteWhere { table.selector(id) }
            .let { if (it == 0) throw NoSuchRecordException(id, table.tableName) }
    }
}

abstract class IntSqlService<Request, Model, Update, TableType : IntServiceTable, PageType : PageOptionsBase<PageType>>(
    database: Database,
) : SqlService<Request, Model, Update, Int, TableType, PageType>(database)

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

class UpdateStatementSets(private val statement: UpdateStatement) {
    infix fun <T> Column<T>.set(value: T?) {
        if (value != null) statement[this] = value
    }
}

fun UpdateStatement.setAll(block: UpdateStatementSets.() -> Unit) {
    val dsl = UpdateStatementSets(this)
    dsl.block()
}

class InsertStatementSets(private val statement: InsertStatement<*>) {
    infix fun <T> Column<T>.set(value: T) {
        statement[this] = value
    }
}

fun InsertStatement<*>.setAll(block: InsertStatementSets.() -> Unit) {
    val dsl = InsertStatementSets(this)
    dsl.block()
}