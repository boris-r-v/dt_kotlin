package src.main.kotlin

import DT_type
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Конструкционные параметры ДТ храняться в базе данных и на их основе создаем экземпляры DT_type
 * Мне надо:
 * 1. Массы масла, обмотки, седрдечника, корпуса
 * 2. Габаритные размеры корпуса и сердечника
 * 3. Площадь сечения токовой обмотки
 * 4. Сопротивление обмотки тяговому току в Омах при 20гр.цельсия
 *
 * Как определить параметры нужные для модели
 *  Масло:
 *  Знаем: объем заливаемого масла (это из справочника Сорока В.И. Аппаратура жд автоматики)
 *  Нужно: массу залитого масла
 *  1. oilM = oilV * oilDens,
 *
 * Обмотка:
 *  Знаем, сопротивление обмотки и площадь сечения (это из справочника Сорока В.И. Аппаратура жд автоматики):
 *  1. Определяем длину из coilR=coilRo*L/coilS => L=coilR*coilS/coilRo
 *  2. Определяем радиус цилиндра с такой же площадью, что и у обмотки, S = Pi*R^2 => R = (S/Pi)^0.5
 *  3. Определяем длину окружности радиусом R = 2 * R * Pi
 *  4. Определяем площадь поверхности цилиндра S = L * R
 *  Нужно знать, для расчета:
 *  1. площадь поверхности обмотки с которой идет теплопередача в масло
 *  2. массу тоже можем рассчитать
 *  3. сопротивление обмотки тяговому току - это задаем, так что нужно только площадь.
 *  Рассчитать объем обмотки и узнать ее массу
 *  1. Определяем объем обмотки V = coilS * L
 *  2. Определяем массу coilM = V * coilDens
 *
 *  Корпус
 *  Знаем: габаритные размеры ДхШхВ, из справочника Сорока В.И. Аппаратура жд автоматики
 *  Примем толщину стенки порядка 1см или 0.01м
 *  Нужно посчитать объем метала корпуса, взять поправку на выступающие детали и умножить на плотность
 *  1. V1 = Д * Ш * В
 *  2. V2 = (Д-1см) * (Ш-1см) * (В-1см)
 *  3. V = (V1-V2) * 1.1
 *
 *  Сердечник:
 *  Надо:
 *      1. массу - рассчитаем как массу сухого ДТ минус массу обмотки и корпуса
 *      2. габаритные размеры - придется мерить руками или искать в чертежах
 *  Массу сухого ДТ знаем из справочника Сорока В.И. Аппаратура жд автоматики
 */

/**
 * Класс с константами которые не будем хранить в базе данных
 * @param oilHC удельная теплоемкость масла (трансформаторного ), Дж/(кг·К)
 * @param oilDens плотность трансформаторного масла, кг/м^3 равно 840-890 кг/м^3, берем среднее 865
 * @param coilHC удельная теплоемкость обмотки (медь), Дж/(кг·К)
 * @param coilRo удельное сопротивление обмотки (медь) при 20 грЦ, Ом*м равное 1.68 × 10⁻⁸ Ом·м
 * @param coilDens плотность материала обмотки (медь), кг/м^3
 * @param coreHC удельная теплоемкость сердечника (чугун), Дж/(кг·К)
 * @param bodyHC удельная теплоемкость корпуса (сталь), Дж/(кг·К)
 * @param bodyDens плотность чугуна, кг/м^3 (7874 кг/м^3)
 * @param bodyWallThickness примерная толщина стенки дроссель-трансформатора, м (~0.01м, 10см)
 * @param bodyMassCoefficient коэффициент массы корпуса учитывающий выступающие детали, долиЕдиницы (1.1)
 * @param bodyGrayness степень черноты корпуса, долиЕдиницы (0,9)
 *
 */
data class HeatModelConstant(
    val oilHC: Double = 1670.0, val oilDens: Double = 865.0,
    val coilHC: Double = 370.0, val coilDens: Double = 8960.0, val coilRo: Double = 0.0000000168,
    val coreHC: Double = 460.0,
    val bodyHC: Double = 500.0, val bodyDens: Double = 7874.0, val bodyWallThickness: Double = 0.01,
    val bodyMassCoefficient: Double = 1.1, val bodyGrayness: Double = 0.9

)

/**
 * Данные которые храним в базе данных
 * @param name
 * @param oilV объем трансформаторного масла, л (0,001 м^3)
 * @param dtM масса дроссель-трансформатора без масла, кг
 * @param coilR сопротивление токовой обмотки, Ом
 * @param coilS площадь сечения токовой обмотки, mm2
 * @param coreX ширина сердечника, м
 * @param coreY длинна сердечника, м (coreX*coreY - площадь основания сердечника)
 * @param coreZ высота сердечника, м
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
 *
 */
data class DtTableModel(
    val name: String,
    val oilV: Double,
    val dtM: Double,
    val coilR: Double, val coilS: Double,
    val coreX: Double, val coreY: Double, val coreZ: Double,
    val bodyX: Double, val bodyY: Double, val bodyZ: Double,
    val nominalCurrent: Double
)

fun createTable(connection: Connection) {
    val sql = """
        CREATE TABLE IF NOT EXISTS DTS (
            name TEXT NOT NULL UNIQUE,
            oilV REAL NOT NULL,
            dtM REAL NOT NULL,
            coilR REAL NOT NULL,         
            coilS REAL NOT NULL,
            coreX REAL NOT NULL,
            coreY REAL NOT NULL,
            coreZ REAL NOT NULL,
            bodyX REAL NOT NULL,
            bodyY REAL NOT NULL,
            bodyZ REAL NOT NULL,
            numinalCurrent REAL NOT NULL
        );
    """.trimIndent()

    connection.createStatement().use { it.execute(sql) }
    println("Table DTS created successfully")
}
fun insertDT(connection: Connection, dt: DtTableModel) {
    val sql = "INSERT INTO DTS(name, oilV, dtM, coilR, coilS, coreX, coreY, coreZ, bodyX, bodyY, bodyZ, nominalCurrent ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    connection.prepareStatement(sql).use { pstmt ->
        pstmt.setString(1, dt.name )
        pstmt.setDouble(2, dt.oilV)
        pstmt.setDouble(3, dt.dtM)
        pstmt.setDouble(4, dt.coilR)
        pstmt.setDouble(5, dt.coilS)
        pstmt.setDouble(6, dt.coreX)
        pstmt.setDouble(7, dt.coreY)
        pstmt.setDouble(8, dt.coreZ)
        pstmt.setDouble(9, dt.bodyX)
        pstmt.setDouble(10, dt.bodyY)
        pstmt.setDouble(11, dt.bodyZ)
        pstmt.setDouble(12, dt.nominalCurrent)
        pstmt.executeUpdate()
    }
    println("Inserted: $dt")
}
class DtNotFound(message: String) : Exception(message)

fun readDT(connection: Connection, name: String): DtTableModel {
    println("Reading all users:")
    val sql = "SELECT * FROM DTS WHERE name = ? LIMIT 1"

    connection.prepareStatement(sql).use { stmt ->
        stmt.setString(1, name)
        stmt.executeQuery().use { rs ->
            if (rs.next()) {
                return DtTableModel(
                    name = rs.getString("name"),
                    oilV = rs.getDouble("oilV"),
                    dtM = rs.getDouble("dtM"),
                    coilR = rs.getDouble("coilR"),
                    coilS = rs.getDouble("coilS"),
                    coreX = rs.getDouble("coreX"),
                    coreY = rs.getDouble("coreY"),
                    coreZ = rs.getDouble("coreZ"),
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
        //Расчет параметров токовой обмотки ДТ
        val coilLen: Double = dtData.coilR*dtData.coilS/dtConstant.coilRo   //расчетная длина обмотки
        val coilEqvRadius: Double = sqrt(dtData.coilS/PI)               //радиус круга эквивалентной площади
        val coilEqvC: Double = 2*PI*coilEqvRadius                           //длина окружности эквалентного цилинда
        val coilM: Double = dtData.coilS*coilLen*dtConstant.coilDens        //масса обмотки
        val oilM: Double = dtData.oilV*0.001*dtConstant.oilDens             //масса масла
        val bodyV1: Double = dtData.bodyX * dtData.bodyY * dtData.coreZ
        val bodyV2: Double = (dtData.bodyX-dtConstant.bodyWallThickness) * (dtData.bodyY-dtConstant.bodyWallThickness) * (dtData.coreZ-dtConstant.bodyWallThickness)
        val bodyM: Double = abs(bodyV1-bodyV2)*dtConstant.bodyMassCoefficient*dtConstant.bodyDens   //масса корпуса
        val coreM: Double = dtData.dtM - (coilM+bodyM)
        assert(coreM < 0) { "Масса сердечника ДТ получилась меньше нуля, массаДТ-(массаОбмотки+массаКорпуса) ${dtData.dtM} - ($coilM + $bodyM)" }
        return DT_type(
            name = dtData.name,
            coil_M = coilM,
            coil_R = dtData.coilR,
            coil_S = coilEqvC*coilLen,
            core_M = coreM,
            oil_M = oilM,
            body_M = bodyM,
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
            oilV = 28.0,
            dtM = 157.0,
            coilR = 0.0011,
            coilS = 243.1,
            coreX = TODO(),
            coreY = TODO(),
            coreZ = TODO(),
            bodyX = 0.67,
            bodyY = 0.45,
            bodyZ = 0.38,
            nominalCurrent = 2000.0
        )
    }
    catch (e: Exception){
        println("Ошибка: ${e.message}")
    }
}
