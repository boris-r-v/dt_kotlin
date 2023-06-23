

fun main(args: Array<String>) {
    println("Hello World!")

    // Try adding program arguments at Run/Debug configuration
    println("Program arguments: ${args.joinToString()}")
    println("*********************************************************")
    val writer = java.io.PrintWriter("./file.csv")
    writer.append("temp_C.coil,temp_C.oil,temp_C.core,temp_C.body\n")
    val dt2 = dt_ht_create("DT-0.6-1000")
    dt2.ht.cloud=0.5
    dt2.ht.external_temp=293.0
    dt2.set_dt_init_temp_degC(75.0)

    set_dt_init_temp_degC(dt2.ht.external_temp-273, dt2)

    //dt2.calc(2400.0, 6200, writer)
    dt2.calc(10.0, 600, writer)
    dt2.calc(5400.0, 30, writer)
    dt2.calc(1000.0, 6000, writer)

    println ("DT: ${dt2.type}")
    println("DT temp final ${dt2.temp}")
    //java.lang.Thread.sleep(5000)

    writer.close()
}