package src.main.kotlin

import DT_type

/**
 * Конструкционные параметры ДТ зраняться в базе данных и на их основе создаем экземпляры DT_type
 * Мне надо:
 * 1. Массы масла, обмотки, седрдечника, корпуса
 * 2. Площадь поверхности теплопередачи тококовй обомтки ДТ, которрая погружена в масло без какой лиюо изоляции и медь
 *      обмотки напрямую контактирует с маслом
 * 3. Площади основания (или ширина/длинна основания)
 *      * корпуса - это будет площать которая нагревается солнцем (верхняя крышка), и поверхность с которой тепло уходит
 *          землю, дно ДТ
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
 * @param coilS площадь поверхности теплоотдачи токовой обмотки (нужно найти ее площадь сечения и зная объем меди (масса и плотность известны) найти длину обмотки, а котом посчитать периметр (или длину окружности) и умножить на полученную длинну
 * @param coreM масса сердечника
 * @param coreX ширина сердечника
 * @param coreY длинна сердечника ( coreX*coreY - площадь основания сердечника)
 * @param coreZ высота сердечника
 * @param oilM масса масла
 * @param bodyM масса корпуса
 * @param bodyX ширина корпуса
 * @param bodyY длинна корпуса (bodyX*bodyY - площадь основания корпуса)
 * @param bodyZ высота корпуса
 *
 * Как посчитать данные для DT_type
 * core_S = 2*(coreX+coreY)*coreZ + coreX*coreY
 * body_old_S = 2*(bodyX+bodyY)*coreZ
 * body_air_S = 2*(bodyX+bodyY)+bodyZ + bodyX*bodyY - нужно перепроверить как это я посчитал с учетом вертикальных стенок и т.д.
 * body_radiation_S = 2*(bodyX+bodyY)+bodyZ + bodyX*bodyY
 * body_ground_S = bodyX*bodyY
 */
data class TableModel(
    val coilM: Double, val coilR: Double, val coilS: Double,
    val coreM: Double, val coreX: Double, val coreY: Double, val coreZ: Double,
    val oilM: Double,
    val bodyM: Double, val bodyX: Double, val bodyY: Double, val bodyZ: Double,
    val nominalCurrent: Double
)

val type = DT_type( 40.0,390.0,0.0011,0.58,
                    70.0, 480.0, 0.283,
                    24.3, 1670.0,
                    47.0, 480.0, 0.76,0.3,1.3,0.86, 0.258,0.9,2000.0, "DT-0.6-1000")
