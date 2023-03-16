

fun main(args: Array<String>) {
    println("Hello World!")

    // Try adding program arguments at Run/Debug configuration
    println("Program arguments: ${args.joinToString()}")
    println("*********************************************************")
    val dt2 = dt_ht_create("DT-0.6-1000")
    dt2.ht.cloud=0.0
    dt2.ht.external_temp=273.0
    dt2.calc(2400.0, 3600*5)
    println ("DT: ${dt2.type}")
    println("DT temp final ${dt2.temp}")

}