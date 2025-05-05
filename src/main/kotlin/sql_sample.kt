//package src.main.kotlin
//import java.sql.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

fun main() {
    // Подключение к базе данных (файл будет создан автоматически)
    val dbPath = "sample.db"
    val url = "jdbc:sqlite:$dbPath"

    try {
        // Установка соединения
        val connection = DriverManager.getConnection(url)
        println("Connected to SQLite database")

        // Создание таблицы
        createTable(connection)

        // Вставка данных
        insertData(connection, "Alice", 25)
        insertData(connection, "Bob", 30)
        insertData(connection, "Charlie", 35)

        // Чтение данных
        readData(connection)

        // Обновление данных
        updateData(connection, "Bob", 31)

        // Удаление данных
        deleteData(connection, "Charlie")

        // Проверка изменений
        readData(connection)

        // Закрытие соединения
        connection.close()
    } catch (e: SQLException) {
        println("SQLite error: ${e.message}")
    }
}

fun createTable(connection: Connection) {
    val sql = """
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            age INTEGER
        )
    """.trimIndent()

    connection.createStatement().use { it.execute(sql) }
    println("Table created successfully")
}

fun insertData(connection: Connection, name: String, age: Int) {
    val sql = "INSERT INTO users(name, age) VALUES(?, ?)"

    connection.prepareStatement(sql).use { pstmt ->
        pstmt.setString(1, name)
        pstmt.setInt(2, age)
        pstmt.executeUpdate()
    }
    println("Inserted: $name, $age")
}

fun readData(connection: Connection) {
    println("Reading all users:")
    val sql = "SELECT id, name, age FROM users"

    connection.createStatement().use { stmt ->
        val rs = stmt.executeQuery(sql)
        while (rs.next()) {
            val id = rs.getInt("id")
            val name = rs.getString("name")
            val age = rs.getInt("age")
            println("ID: $id, Name: $name, Age: $age")
        }
    }
}

fun updateData(connection: Connection, name: String, newAge: Int) {
    val sql = "UPDATE users SET age = ? WHERE name = ?"

    connection.prepareStatement(sql).use { pstmt ->
        pstmt.setInt(1, newAge)
        pstmt.setString(2, name)
        val affectedRows = pstmt.executeUpdate()
        println("Updated $affectedRows row(s)")
    }
}

fun deleteData(connection: Connection, name: String) {
    val sql = "DELETE FROM users WHERE name = ?"

    connection.prepareStatement(sql).use { pstmt ->
        pstmt.setString(1, name)
        val affectedRows = pstmt.executeUpdate()
        println("Deleted $affectedRows row(s)")
    }
}
