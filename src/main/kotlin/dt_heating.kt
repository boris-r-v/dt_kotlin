import kotlin.math.pow
import kotlin.math.log
import kotlin.math.abs

private val MAX_BDF_STEP = 10
class MaxBdfStepExcept(message: String) : Exception(message)
/**
 * Класс для хранения ошибка расчета теплового баланса ДТ на каком-то расчетном шаге
 * Учитывается отдельно ошибки для корпуса, масла, сердечника и обмотки
 */
data class CError (var oil:Double,  var coil:Double, var core:Double, var body:Double){}
/**
 * Класс с данными контструкции дроссель-трансформатора
 * @param coil_HC - теплоемкость материала токовой обмотки ДТ
 * @param coil_M - масса токовой обмотки ДТ
 * @param coil_R - сопротивление обмотки тяговому току при 20Гр Цельсия
 * @param coil_S - площадь поверхности теплоотдачи обмотки
 * @param core_HC - теплопроводность материала сердечника ДТ
 * @param core_M - масса сердечника ДТ
 * @param core_S - плошать теплообмена сердченика ДТ
 * @param oil_HC - теплоемкость масла ДТ
 * @param oil_M - масса масла ДТ
 * @param body_HC - теплоемкость корпуса ДТ
 * @param body_M - масса корпуса ДТ (вместе с крышкой)
 * @param body_air_S - эквивалентная площадь поверхности (с учетом вертикальных и горизонтальных сторон ДТ) учавствующая в конвективном теплообмене с воздухом
 * @param body_grayness - степень черноты поверхности ДТ
 * @param body_ground_S - площадь повержхности ДТ учавствующая в кондуктивном теплообменен с землей (передача тепла от ДТ в землю)
 * @param body_oil_S - плошать теплообмена с маслом
 * @param body_radiation_S - площадь поверхности ДТ с которой возможно излучение тепла в окружающую среду (обычно площадь поверхности ДТ, без учета площади олснования на которм он стоит)
 * @param body_sun_S - площадь ДТ подверженная нагреву солнечным светом
 * @param nominal_current - номинальный ток ДТ (пока не используется)
 * @param name - имя типа ДТ
 */
data class DT_type(
    val coil_M: Double,    val coil_HC: Double,    val coil_R:Double,    val coil_S: Double,
    val core_M: Double,    val core_HC: Double,    val core_S: Double,
    val oil_M: Double,     val oil_HC: Double,
    val body_M: Double,    val body_HC: Double,    val body_oil_S: Double,    val body_air_S: Double,    val body_radiation_S: Double,    val body_ground_S: Double,    val body_sun_S: Double,    val body_grayness: Double,
    val nominal_current: Double,
    val name: String
){}
/**
 * Класс с данными по температуре элементов дроссель-трансформатора
 * @param coil - температура обмотки
 * @param oil  - температура масла
 * @param core - температура сердечника
 * @param body - температура корпуса
 */
data class DT_temp(var coil: Double, var oil: Double, var core: Double, var body: Double ){}
/**
 * Класс с данными по коэффициентам тепрлопередачи и другим данным необходимым для теплового расчета
 * @param h_coil_oil - коэффициент теплопередачи от обмотке маслу, пересчитывается в зависимости от температуры масла
 * @param h_oil_core - коэффициент теплопередачи от масла cthltxybre, пересчитывается в зависимости от температуры масла
 * @param h_oil_body - коэффициент теплопередачи от масла корпусу, пересчитывается в зависимости от температуры масла
 * @param h_body_air - коэффициент теплопередачи от корпуса ДТ в воздух, пересчитывается в зависимости от температуры корпуса
 * @param h_body_ground - коэфыфициент теплопередачи от куорпуса ДТ в землю, не пересчитывается в процессе расчета, константа
 * @param sun_radiation - поток солнечного излучения
 * @param external_temp - температура окружающей среды
 * @param cloud - облачность на небе в долях единицы, где 0-это сплошная обласность, 1-ясно, без облаков
 * @param wind - скороть ветра в м/с
 */
data class DT_heat_transfer ( var h_coil_oil: Double, var h_oil_body: Double, var h_oil_core: Double,
                              var h_body_air: Double, var h_body_ground: Double,
                              var sun_radiation: Double, var external_temp: Double,
                              var cloud: Double, var wind: Double )

/**
 * Функция для пересчета коэффициентов теплопередачи в зависимости от температуры
 */
private fun updateHtParams(dt_temp: DT_temp, dt_ht: DT_heat_transfer) {
        val difT = dt_temp.body - dt_ht.external_temp + 0.001
        dt_ht.h_body_air = 2.1 + 1.2834 * log(difT, 2.73) + 1.51 + 4.2 * dt_ht.wind
        dt_ht.h_coil_oil = 0.4019 * dt_temp.oil + 110.09
        dt_ht.h_oil_body = dt_ht.h_coil_oil
        dt_ht.h_oil_core = dt_ht.h_coil_oil
}

/**
 * Класс расчета теплового баланса ДТ
 * @param temp - температура элементов ДТ
 * @param type - тип ДТ
 * @param ht - значения коэффциентов теплопередачи
 */
class DT(var temp: DT_temp, val type: DT_type, var ht: DT_heat_transfer){
    internal var temp_C = DT_temp(20.0, 20.0, 20.0,20.0)
    private var current_ = 0.0
    private val Q = mutableMapOf<String, Array<Double> >("oil" to Array(2, {0.0}), "coil" to Array(2, {0.0}), "core" to Array(2, {0.0}), "body" to Array(2, {0.0}))
    private val Csb = 5.6e-8

    /**
     * Установить значение тока ДТ
     */
    fun current ( v: Double ){
        current_ = v
    }
    /**
     * Прочитать значение тока ДТ
     */
    fun current(): Double = current_

    /**
     * Функция дифференциального уравнения теплового баланса обмотки
     * @param - температуры: обмотки, масла, сердечника, корпуса
     * @return - расчитанную темепартуру обмотки
     */
    private fun coil_ode( coil_temp: Double, oil_temp: Double, core_temp: Double, body_temp:Double, idx: Int = 0 ): Double
    {
        val f1 = type.coil_R*(1+0.004*(coil_temp-293.0))*current().pow(2)
        val f2 = ht.h_coil_oil*type.coil_S*(coil_temp-oil_temp)
        Q["coil"]!![idx] = f1 - f2
        //println("HEAT FOR $idx COIL ${Q["coil"]?.get(idx)}")
        return (f1 - f2) / (type.coil_M*type.coil_HC)
    }
    /**
     * Функция дифференциального уравнения теплового баланса масла
     * @param - температуры: обмотки, масла, сердечника, корпуса
     * @return - расчитанную темепартуру масла
     */
    private fun oil_ode ( coil_temp: Double, oil_temp: Double, core_temp: Double, body_temp:Double, idx: Int = 0 ): Double
    {
        val f1 = (ht.h_coil_oil*type.coil_S*(coil_temp - oil_temp) )        //heat transfer from coil to oil
        val f2 = (ht.h_oil_body*type.body_oil_S*(oil_temp - body_temp) )    //heat transfer from oil to body
        val f3 = (ht.h_oil_core*type.core_S*(oil_temp - core_temp) )        //heat transfer from oil to core
        Q["oil"]!![idx] = (f1 - f2 - f3)
        //println("HEAT FOR $idx OIL ${Q["oil"]?.get(idx)}")
        return ( f1 - f2 - f3 ) / ( type.oil_M*type.oil_HC )          //oil temp
    }
    /**
     * Функция дифференциального уравнения теплового баланса сердечника
     * @param - температуры: обмотки, масла, сердечника, корпуса
     * @return - расчитанную темепартуру сердечника
     */
    private fun core_ode( coil_temp: Double, oil_temp: Double, core_temp: Double, body_temp:Double, idx: Int = 0 ): Double
    {
        Q["core"]!![idx] = ht.h_oil_core*type.core_S*(oil_temp - core_temp)
        /*println("HEAT $idx FOR CORE ${Q["core"]?.get(idx)}")*/
        return ( ht.h_oil_core*type.core_S*(oil_temp - core_temp) ) / ( type.core_M*type.core_HC )
    }
    /**
     * Функция дифференциального уравнения теплового баланса корпуса
     * @param - температуры: обмотки, масла, сердечника, корпуса
     * @return - расчитанную темепартуру корпуса
     */
    private fun body_ode ( coil_temp: Double, oil_temp: Double, core_temp: Double, body_temp:Double, idx: Int = 0 ): Double
    {
        val f1 = ht.h_body_air*type.body_air_S*(body_temp - ht.external_temp)        //ковекция с корпуса
        val f2 = ht.h_body_ground*type.body_ground_S*(body_temp - ht.external_temp )  //кондукция с корпуса
        val f3 = type.body_grayness*type.body_radiation_S*Csb*(body_temp.pow(4) - ht.external_temp.pow(4 ) ) //излучение с корпуса
        val cooling = f1 + f2 + f3
        val heating = (ht.h_oil_body*type.body_oil_S*(oil_temp - body_temp)) + (type.body_grayness*type.body_sun_S*ht.sun_radiation*ht.cloud)
        Q["body"]!![idx] = heating - cooling
        //println("HEAT FOR $idx BODY ${Q["body"]?.get(idx)}")
        return ( heating - cooling ) / ( type.body_M*type.body_HC )
    }
    /**
      *Расчет теплового баланса своей реализацией многоходового алгоритма численнго моделирования
      * @param i_error - место куда записать значения ощибки на данном шаге расчета
      * @param step - шаг расчета
      * @param max_error - разница температур между предыдушим и текущим расчетным шагом, если ошибка меньше данного числа считаем что расчет сошелся
     */
    private fun my_bdf( i_error: CError, step: Double, max_error: Double = 1E-10 )
    {
        println("Trace my_bdf**************************************")
        val error = CError(100.0,100.0,100.0,100.0)
        val dt_temp = temp
        /*Расчитаем температуру на i+1 шаге*/
        val T_coil = dt_temp.coil + step * coil_ode( dt_temp.coil, dt_temp.oil, dt_temp.core, dt_temp.body )
        val T_oil  = dt_temp.oil  + step * oil_ode(  dt_temp.coil, dt_temp.oil, dt_temp.core, dt_temp.body )
        val T_core = dt_temp.core + step * core_ode( dt_temp.coil, dt_temp.oil, dt_temp.core, dt_temp.body )
        val T_body = dt_temp.body + step * body_ode( dt_temp.coil, dt_temp.oil, dt_temp.core, dt_temp.body )

        coil_ode( T_coil, T_oil, T_core, T_body,1 )
        oil_ode(  T_coil, T_oil, T_core, T_body,1 )
        core_ode( T_coil, T_oil, T_core, T_body,1 )
        body_ode( T_coil, T_oil, T_core, T_body,1 )

        var T_coil3 = dt_temp.coil + step * ((Q["coil"]!!.get(0) + Q["coil"]!!.get(1)) / 2) / (type.coil_M * type.coil_HC)
        var T_oil3 =  dt_temp.oil  + step * ((Q["oil"]!!.get(0) + Q["oil"]!!.get(1)) / 2) / (type.oil_M * type.oil_HC)
        var T_core3 = dt_temp.core + step * ((Q["core"]!!.get(0) + Q["core"]!!.get(1)) / 2) / (type.core_M * type.core_HC)
        var T_body3 = dt_temp.body + step * ((Q["body"]!!.get(0) + Q["body"]!!.get(1)) / 2) / (type.body_M * type.body_HC)

        var cntr = 0
        while (cntr < MAX_BDF_STEP && error.body > max_error && error.oil > max_error && error.coil > max_error) {

            val T_coil4 = T_coil3
            val T_oil4 = T_oil3
            val T_core4 = T_core3
            val T_body4 = T_body3

            coil_ode( T_coil3, T_oil3, T_core3, T_body3,1 )
            oil_ode(  T_coil3, T_oil3, T_core3, T_body3,1 )
            core_ode( T_coil3, T_oil3, T_core3, T_body3,1 )
            body_ode( T_coil3, T_oil3, T_core3, T_body3,1 )

            T_coil3 = dt_temp.coil + step * ((Q["coil"]!!.get(0) + Q["coil"]!!.get(1)) / 2) / (type.coil_M * type.coil_HC)
            T_oil3 =  dt_temp.oil  + step * ((Q["oil"]!!.get(0) + Q["oil"]!!.get(1)) / 2) / (type.oil_M * type.oil_HC)
            T_core3 = dt_temp.core + step * ((Q["core"]!!.get(0) + Q["core"]!!.get(1)) / 2) / (type.core_M * type.core_HC)
            T_body3 = dt_temp.body + step * ((Q["body"]!!.get(0) + Q["body"]!!.get(1)) / 2) / (type.body_M * type.body_HC)
            error.coil = abs(T_coil4 - T_coil3 )
            error.oil =  abs(T_oil4 - T_oil3)
            error.core = abs(T_core4 - T_core3)
            error.body = abs(T_body4 - T_body3)
            println("count $cntr, ERR: $error")
            ++cntr
            //FIX ME если мы сделали 10 итераций а решение не сошлось - то что-то нужно сделать, сейчас оставляем результат последней итерации с ошибкой
        }
        if (cntr == MAX_BDF_STEP){
            throw MaxBdfStepExcept("Solution not convergent")
        }

        temp.coil = T_coil3
        temp.oil = T_oil3
        temp.core = T_core3
        temp.body = T_body3

        temp_C.coil = temp.coil-273.0
        temp_C.oil  = temp.oil-273.0
        temp_C.core = temp.core-273.0
        temp_C.body = temp.body-273.0

        i_error.coil = error.coil
        i_error.oil  = error.oil
        i_error.core = error.core
        i_error.body = error.body
    }

    /**
     * Функция расчета проводящая решение системы дифференциальных уравнений теплового баланса методом BDF
     * @param error - значение ошибки на данном шаге расчета
     * @param step - временной шаг данного расчета в секундах
     */
    private fun calc_next_step( error: CError, step: Double)
    {
        println("calc_next_step $step")
        my_bdf(error, step)
        updateHtParams(temp, ht)
        println ( "calc_next_bdf ERR : $error" )
        println ( "calc_next_bdf Temp: $temp_C" )
        println ( "calc_next_bdf HT  : $ht")
    }

    /**
     * Функция расчета доступная извне,
     * @param current - значение тока на данном временном промежутке
     * @param sec: - длительность данного промежутка в секундах
     */
    fun calc(current: Double, sec: Int, writer: java.io.PrintWriter )
    {
        /*До следующего коментария - для записи в лог*/
        val write_sec = 10
        var prev_write_sec = 0.0
        /*Следующий комментарий*/
        val tbeg = System.currentTimeMillis()
        var time = 0.0
        var step = 1.0
        val step_mult = 1.2
        var isChangeStep = true
        var restep_attempt = 10
        var exept_cntr = 0
        this.current(current)
        val error = CError(0.0,0.0,0.0,0.0)
        var iter = 0
        while ( sec > time )
        {
            try {
                calc_next_step(error, step)
                time += step
                println("study time: $time, temp_C $temp_C ")
            }
            catch (e: MaxBdfStepExcept)
            {
                if  (restep_attempt == 0 )
                {
                    restep_attempt = 10
                    isChangeStep = true
                }
                step = step / step_mult
                if ( --restep_attempt == 0 )
                {
                    isChangeStep = false
                }
                ++exept_cntr
                continue
            }

            if ( time >= prev_write_sec + write_sec )
            {
                prev_write_sec = time
                writer.append("${temp_C.coil}, ${temp_C.oil}, ${temp_C.core}, ${temp_C.body} \n")
            }
            if (isChangeStep)//(error.oil == 0.0 && error.body == 0.0 && error.coil == 0.0 && error.core == 0.0) {
            {
                step *= step_mult
            }
            iter += 1
        }

        val tend = System.currentTimeMillis()
        println ("total steps: $iter calc duration: ${tend-tbeg} exept_cntr $exept_cntr")
    }
}

/**
 * Фабрика создани ДТ
 * 16-Mar-2023 - поддеривается только ДТ-0.2-1000 и ДТ-0.6-1000, для ДТ-0.2-500 и ДТ-0.6-500 не верные все параметры кроме coil_R
 */
fun dt_ht_create(dt_type: String): DT
{
    val temp = DT_temp(273.0, 273.0, 273.0,273.0)
    val hc = DT_heat_transfer(310.0,310.0,310.0,5.35,8.69,800.0,293.0,0.5,1.0)
    if (0 == dt_type.compareTo("DT-0.6-1000") ){
        val type = DT_type(40.0,390.0,0.0011,0.58,70.0, 480.0, 0.283,24.3, 1670.0, 47.0, 480.0, 0.76,0.3,1.3,0.86, 0.258,0.9,2000.0, "DT-0.6-1000")
        return DT(temp, type, hc)
    }
    if (0 == dt_type.compareTo("DT-0.2-1000")  ){
        val type = DT_type(40.0,390.0,0.00088,0.58,70.0, 480.0, 0.283,24.3, 1670.0, 47.0, 480.0, 0.76,0.3,1.3,0.86, 0.258,0.9,2000.0, "DT-0.6-1000")
        return DT(temp, type, hc)
    }
    if (0 == dt_type.compareTo("DT-0.6-500") ){
        val type = DT_type(40.0,390.0,0.00242,0.58,70.0, 480.0, 0.283,24.3, 1670.0, 47.0, 480.0, 0.76,0.3,1.3,0.86, 0.258,0.9,2000.0, "DT-0.6-1000")
        return DT(temp, type, hc)
    }
    if (0 == dt_type.compareTo("DT-0.2-500")  ){
        val type = DT_type(40.0,390.0,0.00143,0.58,70.0, 480.0, 0.283,24.3, 1670.0, 47.0, 480.0, 0.76,0.3,1.3,0.86, 0.258,0.9,2000.0, "DT-0.6-1000")
        return DT(temp, type, hc)
    }
    else{
        throw Exception("Not supported type $dt_type")
    }
}

/**
 * Устанавливает переденную температуру как начальную температуру ДТ
 * Это значит что обмотка, масло, корпус и сердечник буут иметь указанную температуру
 * Также на эту температуру будут пересчитаны коэффициенты теплопередачи
 */
fun set_dt_init_temp_degC(degC: Double, dt: DT)
{
    val degK = degC + 273
    dt.temp.oil = degK
    dt.temp.coil = degK
    dt.temp.core = degK
    dt.temp.body = degK
    updateHtParams(dt.temp, dt.ht)
}