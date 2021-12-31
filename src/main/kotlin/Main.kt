import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.time.Instant
import java.util.concurrent.Executors

class Main {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            var dir = ""
            var host = ""
            var user = ""
            var password = ""

            args.forEach {
                when (it) {
                    "-dir" -> dir = args[args.indexOf(it) + 1]
                    "-host" -> host =
                        "jdbc:mysql://${args[args.indexOf(it) + 1]}:3306/?useTimezone=true&serverTimezone=UTC&verifyServerCertificate=false&useSSL=false&allowMultiQueries=true"
                    "-user" -> user = args[args.indexOf(it) + 1]
                    "-password" -> password = args[args.indexOf(it) + 1]
                }
            }

            if (dir.isNotEmpty() && host.isNotEmpty() && user.isNotEmpty() && password.isNotEmpty()) execute(dir, host, user, password)
        }

        private fun execute(dir: String, dbUrl: String, dbUser: String, dbPass: String) {

            val fileList: List<File>

            try {
                fileList = File(dir).listFiles()!!.toList()
            } catch (e: NullPointerException) {
                println("Empty Directory")
                return
            }

            fileList.forEach { routine ->

                val sql = StringBuilder()

                BufferedReader(FileInputStream(routine).reader().buffered()).lines().forEach {
                    sql.appendLine(it)
                }

                val connection = DriverManager.getConnection(dbUrl, dbUser, dbPass)
                connection.setNetworkTimeout(Executors.newFixedThreadPool(1), 300000)

                println("Running file -> ${routine.name}")
                println(sql.toString())

                try {

                    val startTimestamp = Instant.now()
                    val affectedRows = executeSQL(sql.toString(), connection)
                    affectedRows.forEach { x -> println("Query ${(affectedRows.indexOf(x) + 1)} affected $x lines") }

                    println("Script Execution Time -> ${((Instant.now().toEpochMilli() - startTimestamp.toEpochMilli()))} ms")

                } catch (e: Exception) {
                    println(e.message)
                    throw IOException("An error occurred: ${e.message}")
                }

                println("--------------------------------------")

            }

            println("Execution Finished")

        }

        private fun executeSQL(sql: String, connection: Connection): List<Int> {

            val affectedRows: List<Int>

            val queryList = sql.split(";").toMutableList()
            queryList.removeAt(queryList.lastIndex)

            val transact: Statement = connection.createStatement()
            queryList.indices.forEach { i ->
                transact.addBatch(queryList[i])
            }

            try {
                affectedRows = transact.executeBatch().toList()
            } catch (e: Exception) {
                transact.close()
                connection.close()
                println(e.message)
                throw IOException(e.message)
            }

            transact.close()
            connection.close()
            return affectedRows

        }

    }

}