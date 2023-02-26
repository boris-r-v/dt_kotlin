import kotlin.math.pow


data class CError (var oil:Double,  var coil:Double, var core:Double, var body:Double){}
/**
 * This data class for storing data for one DT type
 * i.e. DT-0.6-1000, DT-0.4-1500
 * Used in DT class
 */
data class DT_type(
    val coil_M: Double,    val coil_HC: Double,    val coil_R: Double,    val coil_CSA: Double,    val coil_L: Double,    val coil_S: Double,
    val core_M: Double,    val core_HC: Double,    val core_S: Double,
    val oil_M: Double,    val oil_HC: Double,
    val body_M: Double,    val body_HC: Double,    val body_oil_S: Double,    val body_air_S: Double,    val body_radiation_S: Double,    val body_ground_S: Double,    val body_sun_S: Double,    val body_grayness: Double,
    val nominal_current: Double,
    val name: String
){}
/**
 * Class with current DT temp
 */
data class DT_temp(var coil: Double, var oil: Double, var core: Double, var body: Double ){}
/**
 * Class with current heat transfer coefficients 
 */
class DT_heat_transfer ( var h_coil_oil: Double, var h_oil_body: Double, var h_oil_core: Double,
                         var h_body_air: Double, var h_body_ground: Double,
                         var sun_radiation: Double, var external_temp: Double,
                         var cloud: Double, var wind: Double ) {

    fun getPr(i_degC: Double): Double {
        val degC = i_degC - 273;
        if (degC <= 0) return 866.0;
        if (0 < degC && degC <= 40) return (0.0002291667 * degC.pow(4) - 0.0314166667 * degC.pow(3) + 1.7620833333 * degC.pow(
            2
        ) - 52.9083333 * degC + 866);
        if (40 < degC && degC <= 120) return (0.000001 * 2.5553613053 * degC.pow(4) - 0.0010518065 * degC.pow(3) + 0.169326049 * degC.pow(
            2
        ) - 13.1306351981 * degC + 459.99);
        return 34.9;
    }

    fun getEpsilon(degtK_fluid: Double, degK_solid: Double): Double {
        return (getPr(degtK_fluid) / getPr(degK_solid)).pow(0.25)
    }

    fun updateParams(dt_temp: DT_temp) {
        h_body_air = 2.5 * (dt_temp.body - external_temp).pow(0.25) * (293 / dt_temp.body).pow(0.25) + 4.2 * wind
        val base = 0.5255 * dt_temp.oil - 75.898
        h_coil_oil = base * getEpsilon(dt_temp.oil, dt_temp.coil)
        h_oil_body = base * getEpsilon(dt_temp.oil, dt_temp.body)
        h_oil_core = base * getEpsilon(dt_temp.oil, dt_temp.core)
    }
}

/**
 * Класс расчета теплового баланса ДТ
 */
class DT(var temp: DT_temp, val type: DT_type, var ht: DT_heat_transfer, var current_: Double){
    /**
     * Установить значение тока ДТ
     */
    fun current ( v: Double ){
        current_ = v;
    }

    /**
     * Прочитать значенте тока ДТ
     */
    fun current(): Double = current_;

    /**
     * Функция дифференциального уравнения теплового баланса обмотки
     * @param - температуры: обмотки, масла, сердечника, корпуса
     * @return - расчитанную темепартуру обмотки
     */
    fun coil_ode( coil_temp: Double, oil_temp: Double, core_temp: Double, body_temp:Double ): Double
    {
        val f1 = current().pow(2)*(1+0.04*(coil_temp-293))*type.coil_L*type.coil_R/type.coil_CSA
        val f2 = ht.h_coil_oil*type.coil_S*(coil_temp-oil_temp)
        return (f1 - f2) / (type.coil_M*type.coil_HC)
    }
    /**
     * Функция дифференциального уравнения теплового баланса масла
     * @param - температуры: обмотки, масла, сердечника, корпуса
     * @return - расчитанную темепартуру масла
     */
    fun oil_ode ( coil_temp: Double, oil_temp: Double, core_temp: Double, body_temp:Double ): Double
    {
        val f1 = (ht.h_coil_oil*type.coil_S*(coil_temp - oil_temp) )        //heat transfer from coil to oil
        val f2 = (ht.h_oil_body*type.body_oil_S*(oil_temp - body_temp) )    //heat transfer from oil to body
        val f3 = (ht.h_oil_core*type.core_S*(oil_temp - core_temp) )        //heat transfer from oil to core
        val f4 = (ht.h_body_air*0.1*(oil_temp - ht.external_temp ) )        //head transfer from oil to air under cover
        return (  f1 - f2 - f3 - f4 ) / ( type.oil_M*type.oil_HC )          //oil temp
    }
    /**
     * Функция дифференциального уравнения теплового баланса сердечника
     * @param - температуры: обмотки, масла, сердечника, корпуса
     * @return - расчитанную темепартуру сердечника
     */
    fun core_ode( coil_temp: Double, oil_temp: Double, core_temp: Double, body_temp:Double ): Double
    {
        return ( ht.h_oil_core*type.core_S*(oil_temp - core_temp) ) / ( type.core_M*type.core_HC )
    }
    /**
     * Функция дифференциального уравнения теплового баланса корпуса
     * @param - температуры: обмотки, масла, сердечника, корпуса
     * @return - расчитанную темепартуру корпуса
     */
    fun body_ode ( coil_temp: Double, oil_temp: Double, core_temp: Double, body_temp:Double ): Double
    {
        val Csb = 5.6e-8
        val f1 = ht.h_body_air*type.body_air_S*(body_temp - ht.external_temp)        //ковекция с корпуса
        val f2 = ht.h_body_ground*type.body_ground_S*(body_temp - ht.external_temp )  //кондукция с корпуса
        val f3 = type.body_grayness*type.body_radiation_S*Csb*(body_temp.pow(4) - ht.external_temp.pow(4 ) ) //излучение с корпуса
        val colling = f1 + f2 + f3
        val heating = (ht.h_oil_body*type.body_oil_S*(oil_temp - body_temp)) + (type.body_grayness*type.body_sun_S*ht.sun_radiation*ht.cloud)
        return ( heating - colling ) / ( type.body_M*type.body_HC );
    }
    /**
     * Расчет теплового баланса методом руншге-кнутта 4-5 порядка
     * @param error ошибка на данном шаге рассчета
     * @param step шаг расчета
     * @return Unit(none)
     */
    fun rk45( error: CError, step: Double )
    {
        val dt_temp = temp
        val kcoil0 = step * coil_ode( dt_temp.oil, dt_temp.coil, dt_temp.core, dt_temp.body )
        val koil0  = step * oil_ode(  dt_temp.oil, dt_temp.coil, dt_temp.core, dt_temp.body )
        val kcore0 = step * core_ode( dt_temp.oil, dt_temp.coil, dt_temp.core, dt_temp.body )
        val kbody0 = step * body_ode( dt_temp.oil, dt_temp.coil, dt_temp.core, dt_temp.body )

        val kcoil1_c  = dt_temp.coil + kcoil0/2;
        val koil1_c   = dt_temp.oil + koil0/2
        val kcore1_c  = dt_temp.core + kcore0/2
        val kbody1_c  = dt_temp.body + kbody0/2
        val kcoil1 = step * coil_ode( kcoil1_c, koil1_c, kcore1_c, kbody1_c );
        val koil1  = step * oil_ode ( kcoil1_c, koil1_c, kcore1_c, kbody1_c );
        val kcore1 = step * core_ode( kcoil1_c, koil1_c, kcore1_c, kbody1_c );
        val kbody1 = step * body_ode( kcoil1_c, koil1_c, kcore1_c, kbody1_c );

        val kcoil2_c = dt_temp.coil + (kcoil0 + kcoil1)/4
        val koil2_c  = dt_temp.oil +  (koil0 + koil1)/4
        val kcore2_c = dt_temp.core + (kcore0 + kcore1)/4
        val kbody2_c = dt_temp.body + (kbody0 + kbody1)/4
        val kcoil2 = step * coil_ode( kcoil2_c, koil2_c, kcore2_c, kbody2_c )
        val koil2  = step * oil_ode ( kcoil2_c, koil2_c, kcore2_c, kbody2_c )
        val kcore2 = step * core_ode( kcoil2_c, koil2_c, kcore2_c, kbody2_c )
        val kbody2 = step * body_ode( kcoil2_c, koil2_c, kcore2_c, kbody2_c )

        val kcoil3_c = dt_temp.coil - kcoil1 + 2*kcoil2
        val koil3_c  = dt_temp.oil  - koil1 + 2*koil2
        val kcore3_c = dt_temp.core - kcore1 + 2*kcore2
        val kbody3_c = dt_temp.body - kbody1 + 2*kbody2
        val kcoil3 = step * coil_ode( kcoil3_c, koil3_c, kcore3_c, kbody3_c )
        val koil3  = step * oil_ode ( kcoil3_c, koil3_c, kcore3_c, kbody3_c )
        val kcore3 = step * core_ode( kcoil3_c, koil3_c, kcore3_c, kbody3_c )
        val kbody3 = step * body_ode( kcoil3_c, koil3_c, kcore3_c, kbody3_c )

        val kcoil4_c = dt_temp.coil + (7*kcoil0 + 10*kcoil1 + kcoil3)/27
        val koil4_c  = dt_temp.oil + (7*koil0 + 10*koil1 + koil3)/27
        val kcore4_c = dt_temp.core + (7*kcore0 + 10*kcore1 + kcore3)/27
        val kbody4_c = dt_temp.body + (7*kbody0 + 10*kbody1 + kbody3)/27
        val kcoil4 = step * coil_ode( kcoil4_c, koil4_c, kcore4_c, kbody4_c )
        val koil4  = step * oil_ode ( kcoil4_c, koil4_c, kcore4_c, kbody4_c )
        val kcore4 = step * core_ode( kcoil4_c, koil4_c, kcore4_c, kbody4_c )
        val kbody4 = step * body_ode( kcoil4_c, koil4_c, kcore4_c, kbody4_c )

        val kcoil5_c = dt_temp.coil + (28*kcoil0 - 128*kcoil1 + 546*kcoil2 + 54*kcoil3 - 378*kcoil4)/625
        val koil5_c  = dt_temp.oil +  (28*koil0 - 128*koil1 + 546*koil2 + 54*koil3 - 378*koil4)/625
        val kcore5_c = dt_temp.core + (28*kcore0 - 128*kcore1 + 546*kcore2 + 54*kcore3 - 378*kcore4)/625
        val kbody5_c = dt_temp.body + (28*kbody0 - 128*kbody1 + 546*kbody2 + 54*kbody3 - 378*kbody4)/625
        val kcoil5 = step * coil_ode( kcoil5_c, koil5_c, kcore5_c, kbody5_c )
        val koil5  = step * oil_ode ( kcoil5_c, koil5_c, kcore5_c, kbody5_c )
        val kcore5 = step * core_ode( kcoil5_c, koil5_c, kcore5_c, kbody5_c )
        val kbody5 = step * body_ode( kcoil5_c, koil5_c, kcore5_c, kbody5_c )

        val coil_4ord = dt_temp.coil + ( kcoil0 + 4*kcoil2 + kcoil3 )/6
        val oil_4ord  = dt_temp.oil  +  ( koil0 +  4*koil2 +  koil3  )/6
        val core_4ord = dt_temp.core +  ( kcore0 + 4*kcore2 + kcore3 )/6
        val body_4ord = dt_temp.body +  ( kbody0 + 4*kbody2 + kbody3 )/6

        val coil_5ord = dt_temp.coil + ( 14*kcoil0 + 35*kcoil3 + 162*kcoil4 + 125*kcoil5 )/336
        val oil_5ord  = dt_temp.oil  +  ( 14*koil0 +  35*koil3  + 162*koil4  + 125*koil5 )/336
        val core_5ord = dt_temp.core +  ( 14*kcore0 + 35*kcore3 + 162*kcore4 + 125*kcore5 )/336
        val body_5ord = dt_temp.body +  ( 14*kbody0 + 35*kbody3 + 162*kbody4 + 125*kbody5 )/336


        temp.coil = coil_5ord
        temp.oil = oil_5ord
        temp.core = core_5ord
        temp.body = body_5ord

        error.coil = coil_4ord - coil_5ord;
        error.oil = oil_4ord - oil_5ord;
        error.core = core_4ord - core_5ord;
        error.body = body_4ord - body_5ord;
        println(error)
        println(temp)
    }
    /**
     * Функция управления шагом расчета
     */
    fun update_step( _step: Double,  err: CError): Double
    {
        println("update")
        var step = _step
        if (err.oil < 0.001){
            step = step*0.5
        }
        else{
            step = step*2.0
        }
        return step
    }
    /**
     * Функция расчета температу конструктивных элементов ДТ при условии действия указанного тока, за указанное время
     * @param currect - ток протекающий через ДТ за время sec выраженное в секундах
     * @param sec - время протекания тока current выраенное в секундах
     * @return - Unit
     */
    fun calc_next (_current: Double, _sec: Double)
    {
        /*Пересчитаем коэффициенты теплоотдачи на начальную температуру вычислений*/
        ht.updateParams(temp)
        /*Структура с ошибкой*/
        var error = CError(0.0,0.0,0.0,0.0);
        /*Обновим значения тока в классе DT*/
        this.current(_current)

        var shift_step = 0.1
        var cntr: Double = 0.0
        while (cntr <= _sec) {
            rk45(error, shift_step)
            //shift_step = update_step(shift_step, error)
            println("step_sfter: $shift_step")
            cntr += shift_step
            println( "cntr $cntr" )
     //       println ( error.toString() )
        }
    }
}
fun dt_ht_create(type: String): DT
{
    var temp = DT_temp(293.0, 293.0, 293.0,293.0)
    var hc = DT_heat_transfer(310.0,310.0,310.0,5.35,8.69,800.0,253.0,0.5,1.0)
    if ("DT-0.6-1000" == type){
        var type = DT_type(40.0,390.0,0.0172,288.0, 10.872,0.58, 70.0, 480.0, 0.283,24.3, 1670.0, 47.0, 480.0, 0.760000,0.3,1.3,0.86,0.258,0.9, 2000.0, "DT-0.6-1000")
        return DT(temp, type, hc, 0.0)
    }
    else{
        throw Exception("Not supported type $type")
    }
}
/*
  fun calc_next() {
        var temp = DT_temp(273.0, 273.0, 273.0,273.0)
        var hc = DT_heat_transfer(310.0,310.0,310.0,5.35,8.69,800.0,293.0,0.5,1.0)
        var type = DT_type(40.0,390.0,0.0172,288.0, 10.872,0.58, 70.0, 480.0, 0.283,24.3, 1670.0, 47.0, 480.0, 0.760000,0.3,1.3,0.86,0.258,0.9, 2000.0, "DT-0.6-100")
        var dt = DT(temp, type, hc, 0.0)
        dt.calc_next(2000.0, 5.0)
    }
 */