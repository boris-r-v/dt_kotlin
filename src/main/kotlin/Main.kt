

fun pow2( value: Int ): Int{

    return value * value
}

fun main(args: Array<String>) {
    println("Hello World!")

    // Try adding program arguments at Run/Debug configuration
    println("Program arguments: ${args.joinToString()}")
/*
    var temp = DT_temp(293.0, 293.0, 293.0,293.0)
    var hc = DT_heat_transfer(310.0,310.0,310.0,5.35,8.69,800.0,293.0,0.0,1.0)
    var type = DT_type(40.0,390.0,0.0172,288.0, 10.872,0.58, 70.0, 480.0, 0.283,24.3, 1670.0, 47.0, 480.0, 0.760000,0.3,1.3,0.86,0.258,0.9, 2000.0, "DT-0.6-100")
    var dt = DT(temp, type, hc, 0.0)
*/
    var dt = dt_ht_create("DT-0.6-1000")
    dt.ht.cloud=0.0
    dt.ht.external_temp=203.0
    dt.calc_next(1500.0, 10000.0)

    var dt2 = dt_ht_create("DT-0.6-1000")
    dt2.ht.cloud=0.0
    dt2.ht.external_temp=203.0
    dt2.calc_next_merson(1500.0, 10000.0)
}