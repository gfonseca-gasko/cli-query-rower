import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.sql.DriverManager
import java.sql.Statement
import java.time.Instant
import kotlin.system.exitProcess

class Main {

    companion object {

        private var dir = ""
        private var host = ""
        private var user = ""
        private var password = ""

        private var timestamp = Instant.now()
        private var errorCount = 0
        private val errorMsg = StringBuilder()
        private val sql = StringBuilder()
        private lateinit var fileList: List<File>

        @JvmStatic
        fun main(args: Array<String>) {

            try {
                args.forEach {
                    when (it) {
                        "-dir" -> dir = args[args.indexOf(it) + 1]
                        "-host" -> host =
                            "jdbc:mysql://${args[args.indexOf(it) + 1]}:3306/?useTimezone=true&serverTimezone=UTC&verifyServerCertificate=false&useSSL=false&allowMultiQueries=true"
                        "-user" -> user = args[args.indexOf(it) + 1]
                        "-password" -> password = args[args.indexOf(it) + 1]
                    }
                }

                try {
                    fileList = File(dir).listFiles()!!.toList()
                } catch (e: NullPointerException) {
                    println("Empty Directory")
                    return
                }

                if (dir.isNotEmpty() && host.isNotEmpty() && user.isNotEmpty() && password.isNotEmpty()) {
                    execute()
                }

            } catch (e: Exception) {
                println("Invalid parameters")
                exitProcess(1)
            }

        }

        private fun execute() {

            println("Starting")

            fileList.forEach { routine ->
                sql.clear().let {
                    BufferedReader(FileInputStream(routine).reader().buffered()).lines().forEach { sql.appendLine(it) }
                }

                println("\nRunning file -> ${routine.name} \n $sql")

                try {

                    timestamp = Instant.now()
                    val affectedRows = executeSQL(sql.toString())

                    affectedRows.indices.forEach { x -> println("\nQuery ${x + 1} affected ${affectedRows[x]} lines") }
                    println(
                        "\nScript Execution Time -> ${
                            ((Instant.now().toEpochMilli() - timestamp.toEpochMilli()))
                        } ms"
                    )

                    println("\nEnd of ${routine.name}")

                } catch (e: Exception) {

                    println("\nAn error occurred while running file ${routine.name} \nError -> ${e.message} \n")
                    errorCount++
                    errorMsg.appendLine(
                        "An error occurred while running file ${routine.name} \nError -> ${e.message} \nScript Execution Time -> ${
                            ((Instant.now().toEpochMilli() - timestamp.toEpochMilli()))
                        } ms"
                    )

                }


            }

            println("Execution Finished\n")

            if (errorCount > 0) {
                println("Error Resume")
                println(errorMsg.toString())
                exitProcess(1)
            }

            exitProcess(0)

        }

        private fun executeSQL(sql: String): List<Int> {

            val connection = DriverManager.getConnection(host, user, password)
            //connection.setNetworkTimeout(Executors.newFixedThreadPool(1), 1500)

            val affectedRows: List<Int>

            val queryList = sql.split(";").toMutableList()
            queryList.removeAt(queryList.lastIndex)

            val transact: Statement = connection.createStatement()
            transact.queryTimeout = 120

            queryList.indices.forEach { i ->
                transact.addBatch(queryList[i])
            }

            try {

                affectedRows = transact.executeBatch().toList()

            } catch (e: Exception) {

                transact.close()
                connection.close()
                throw IOException(e.message)

            }

            transact.close()
            connection.close()
            return affectedRows

        }

    }

}