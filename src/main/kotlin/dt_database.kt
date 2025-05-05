package src.main.kotlin

import DT_type
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Конструкционные параметры ДТ храняться в базе данных и на их основе создаем экземпляры DT_type
 * Мне надо:
 * 1. Массы масла, обмотки, седрдечника, корпуса
 * 2. Площадь поверхности теплопередачи тококовй обомтки ДТ, которрая погружена в масло без какой лиюо изоляции и медь
 *      обмотки напрямую контактирует с маслом
 * 3. Площади основания (или ширина/длинна основания)
 *      * корпуса - это будет площать которая нагревается солнцем (верхняя крышка), и поверхность с которой тепло уходит
 *          в   землю, дно ДТ
 *      * сердечнкиа - тут нужно чтобы просто посчитать объем (его можно пересчить через зание массы и плотности)
 * 4. Высота
 *      * корпуса - нужно для определения коэфициента теплопередачи в вертикальных стенок
 *      * сердечника
 * 5. Сопротивление обмотки тяговому току в Омах при 20гр.цельсия
 */

/**
 * Класс с константами которые не будем хранить в базе данных
 */
data class HeatModelConstant(
    val oilHC: Double = 1670.0, val coilHC: Double = 370.0, val coreHC: Double = 480.0, val bodyHC: Double = 480.0,
    val bodyGrayness: Double = 0.9
)

/**
 * Данные которые храним в базе данных
 * @param coilM масса обмотки, кг
 * @param coilR сопротивление обмотки, Ом
 * @param coilS площадь поверхности теплоотдачи токовой обмотки,м2 (нужно найти ее площадь сечения и зная объем меди (масса и плотность известны) найти длину обмотки, а котом посчитать периметр (или длину окружности) и умножить на полученную длинну
 * @param coreM масса сердечника, кг
 * @param coreX ширина сердечника, м
 * @param coreY длинна сердечника, м ( coreX*coreY - площадь основания сердечника)
 * @param coreZ высота сердечника, м
 * @param oilM масса масла, кг
 * @param bodyM масса корпуса, кг
 * @param bodyX ширина корпуса, м
 * @param bodyY длинна корпуса, м (bodyX*bodyY - площадь основания корпуса)
 * @param bodyZ высота корпуса, м
 *
 * Как посчитать данные для DT_type
 * core_S = 2*(coreX+coreY)*coreZ + coreX*coreY
 * body_old_S = 2*(bodyX+bodyY)*coreZ
 * body_air_S = 2*(bodyX+bodyY)*bodyZ + bodyX*bodyY - нужно перепроверить как это я посчитал с учетом вертикальных стенок и т.д.
 * body_radiation_S = 2*(bodyX+bodyY)*bodyZ + 2*bodyX*bodyY
 * body_ground_S = bodyX*bodyY
 * body_sun_S = 0.5*(bodyX+bodyY)*bodyZ + bodyX*bodyY
 */
data class DtTableModel(
    val name: String,
    val coilM: Double, val coilR: Double, val coilS: Double,
    val coreM: Double, val coreX: Double, val coreY: Double, val coreZ: Double,
    val oilM: Double,
    val bodyM: Double, val bodyX: Double, val bodyY: Double, val bodyZ: Double,
    val nominalCurrent: Double
)

fun createTable(connection: Connection) {
    val sql = """
        CREATE TABLE IF NOT EXISTS DTS (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            coilM REAL NOT NULL,
            coilR REAL NOT NULL,         
            coilS REAL NOT NULL,
            coreM REAL NOT NULL,
            coreX REAL NOT NULL,
            coreY REAL NOT NULL,
            coreZ REAL NOT NULL,
            oilM REAL NOT NULL,
            bodyM REAL NOT NULL,
            bodyX REAL NOT NULL,
            bodyY REAL NOT NULL,
            bodyZ REAL NOT NULL,
            numinalCurrent REAL NOT NULL
        )
    """.trimIndent()

    connection.createStatement().use { it.execute(sql) }
    println("Table DTS created successfully")
}
fun insertDT(connection: Connection, dt: DtTableModel) {
    val sql = "INSERT INTO DTS(name, coilM, coilR, coilS, coreM, coreX, coreY, coreZ, oilM, bodyM, bodyX, bodyY, bodyZ, nominalCurrent ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    connection.prepareStatement(sql).use { pstmt ->
        pstmt.setString(1, dt.name )
        pstmt.setDouble(2, dt.coilM)
        pstmt.setDouble(3, dt.coilR)
        pstmt.setDouble(4, dt.coilS)
        pstmt.setDouble(5, dt.coreM)
        pstmt.setDouble(6, dt.coreX)
        pstmt.setDouble(7, dt.coreY)
        pstmt.setDouble(8, dt.coreZ)
        pstmt.setDouble(9, dt.oilM)
        pstmt.setDouble(10, dt.bodyM)
        pstmt.setDouble(11, dt.bodyX)
        pstmt.setDouble(12, dt.bodyY)
        pstmt.setDouble(13, dt.bodyZ)
        pstmt.setDouble(14, dt.nominalCurrent)
        pstmt.executeUpdate()
    }
    println("Inserted: $dt")
}
class DtNotFound(message: String) : Exception(message)

fun readDT(connection: Connection, name: String): DtTableModel {
    println("Reading all users:")
    val sql = "SELECT * FROM DTS WHERE name = ? LIMIT 1"

    connection.prepareStatement(sql).use { stmt ->
        stmt.setInt(1, name)
        stmt.executeQuery().use { rs ->
            if (rs.next()) {
                return DtTableModel(
                    name = rs.getString("name"),
                    coilM = rs.getDouble("coilM"),
                    coilR = rs.getDouble("coilR"),
                    coilS = rs.getDouble("coilS"),
                    coreM = rs.getDouble("coreM"),
                    coreX = rs.getDouble("coreX"),
                    coreY = rs.getDouble("coreY"),
                    coreZ = rs.getDouble("coreZ"),
                    oilM = rs.getDouble("oilM"),
                    bodyM = rs.getDouble("bodyM"),
                    bodyX = rs.getDouble("bodyX"),
                    bodyY = rs.getDouble("bodyY"),
                    bodyZ = rs.getDouble("bodyZ"),
                    nominalCurrent = rs.getDouble("nominalCurrent")
                )
            }
            throw DtNotFound("DT with name $name not found")
        }
    }
}

fun getDT(name: String): DT_type {
    val dbPath = "sample.db"
    val url = "jdbc:sqlite:$dbPath"

    try {
        val connection = DriverManager.getConnection(url)
        val dtData = readDT(connection, name)
        val dtConstant: HeatModelConstant = HeatModelConstant()
        return DT_type(
            name = dtData.name,
            coil_M = dtData.coilM,
            coil_R = dtData.coilR,
            coil_S = dtData.coilS,
            core_M = dtData.coreM,
            oil_M = dtData.oilM,
            body_M = dtData.bodyM,
            nominal_current = dtData.nominalCurrent,
            coil_HC = dtConstant.coilHC,
            oil_HC = dtConstant.oilHC,
            core_HC = dtConstant.coreHC,
            body_HC = dtConstant.bodyHC,
            body_grayness = dtConstant.bodyGrayness,
            core_S = 2*(dtData.coreX+dtData.coreY)*dtData.coreZ + dtData.coreX*dtData.coreY,
            body_oil_S = 2*(dtData.bodyX+dtData.bodyY)*dtData.coreZ,
            body_air_S =  1.5*(dtData.bodyX+dtData.bodyY)*dtData.bodyZ + dtData.bodyX*dtData.bodyY,
            body_radiation_S = 2*(dtData.bodyX+dtData.bodyY)*dtData.bodyZ + 2*dtData.bodyX*dtData.bodyY,
            body_ground_S = dtData.bodyX*dtData.bodyY,
            body_sun_S = 0.5*(dtData.bodyX+dtData.bodyY)*dtData.bodyZ + dtData.bodyX*dtData.bodyY
        )

    }
    catch (e: SQLException) {
        println("SQLite error: ${e.message}")
        throw DtNotFound("SQL error while search DT with name $name  ${e.message}")

    }
    catch (e: DtNotFound) {
        println("SQLite error: ${e.message}")
        throw e
    }
}
val type = DT_type( 40.0,390.0,0.0011,0.58,
    70.0, 480.0, 0.283,
    24.3, 1670.0,
    47.0, 480.0, 0.76,0.3,1.3,0.86, 0.258,0.9,2000.0, "DT-0.6-1000")

fun saveKnownDt(){
    val dbPath = "sample.db"
    val url = "jdbc:sqlite:$dbPath"

    try {
        val connection = DriverManager.getConnection(url)
        val dt = DtTableModel(
            name = "DT-0.6-1000",
            coilM = 40.0,
            coilR = 0.0011,
            coilS = 0.58,
            coreM = 70.0,
            coreX = TODO(),
            coreY = TODO(),
            coreZ = TODO(),
            oilM = 24.3,
            bodyM = 47.0,
            bodyX = 0.67,
            bodyY = 0.45,
            bodyZ = 0.38,
            nominalCurrent = 2000
        )

}