import model.SQL
import service.GoogleApi
import service.Mailer
import java.io.IOException
import java.sql.DriverManager
import java.sql.Statement
import java.time.Instant
import kotlin.system.exitProcess

class Main {
    companion object {
        private var SHEET_ID: String = ""
        private var SHEET_RANGE: String = ""
        private var DATABASE_ENDPOINT: String = ""
        private var DATABASE_USER: String = ""
        private var DATABASE_KEY: String = ""
        private var MAIL_USER: String = ""
        private var MAIL_KEY: String = ""
        private var GOOGLE_CONFIG_FILE: String = ""
        private const val DATABASE_PARAMETERS = "useTimezone=true&serverTimezone=UTC&verifyServerCertificate=false&useSSL=false&allowMultiQueries=true"
        private const val HOME_DIR = "/dados"
        private var mailer: Mailer? = null

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                args.forEach {
                    when (it) {
                        "-sheetid" -> SHEET_ID = args[args.indexOf(it) + 1]
                        "-sheetrange" -> SHEET_RANGE = args[args.indexOf(it) + 1]
                        "-dbhost" -> DATABASE_ENDPOINT = "jdbc:mysql://${args[args.indexOf(it) + 1]}:3306/?$DATABASE_PARAMETERS"
                        "-dbuser" -> DATABASE_USER = args[args.indexOf(it) + 1]
                        "-dbkey" -> DATABASE_KEY = args[args.indexOf(it) + 1]
                        "-mailuser" -> MAIL_USER = args[args.indexOf(it) + 1]
                        "-mailkey" -> MAIL_KEY = args[args.indexOf(it) + 1]
                        "-googlekey" -> GOOGLE_CONFIG_FILE = args[args.indexOf(it) + 1]
                    }
                }
                if (SHEET_ID.isEmpty() || SHEET_RANGE.isEmpty() || DATABASE_ENDPOINT.isEmpty() || DATABASE_USER.isEmpty() || DATABASE_KEY.isEmpty() || GOOGLE_CONFIG_FILE.isEmpty()) {
                    throw Exception()
                } else {
                    if (MAIL_USER.isNotEmpty() || MAIL_KEY.isNotEmpty()) mailer = Mailer(MAIL_USER, MAIL_KEY)
                }
            } catch (e: Exception) {
                val errorMessage = StringBuilder()
                errorMessage.append("Invalid Parameters")
                errorMessage.append("Expected parameters")
                errorMessage.append("-sheetid (ID do google sheets)")
                errorMessage.append("-sheetrange (Range da planilha, ex: PLAN!A1:D5)")
                errorMessage.append("-dbhost (Endpoint do banco de dados)")
                errorMessage.append("-dbuser (Usuário do banco de dados)")
                errorMessage.append("-dbkey (Senha do banco de dados)")
                errorMessage.append("-mailuser (Endereço de mail (origem) para envio de notificações)")
                errorMessage.append("-mailkey (Senha do e-mail fornecido)")
                errorMessage.append("-googlekey (Token JSON da API do google)")
                errorMessage.append(e.message)
                exitProcess(1)
            }
            try {
                // Get Monitoring List from Google Spreadsheets
                val googleApi = GoogleApi(HOME_DIR, GOOGLE_CONFIG_FILE)
                val sqlList = googleApi.getQueries(SHEET_ID, SHEET_RANGE)
                execute(sqlList)
            } catch (e: Exception) {
                e.printStackTrace()
                exitProcess(1)
            }
        }

        private fun execute(sqlList: MutableList<SQL>) {
            val errors = StringBuilder()
            println("Iniciando")
            sqlList.forEach { sql ->
                val startInstant = Instant.now()
                if (!sql.enabled) return@forEach
                println("Executando -> ${sql.name} \n ${sql.sqlQuery}")
                try {
                    val affectedRows = executeSQL(sql.sqlQuery)
                    affectedRows.forEach { r -> println("Query ${affectedRows.indexOf(r) + 1} afetou $r linhas") }
                    println("Tempo decorrido -> ${((Instant.now().toEpochMilli() - startInstant.toEpochMilli()))} ms")
                } catch (e: Exception) {
                    println("Ocorreu um erro ao executar ${sql.name}\nErro -> ${e.message}")
                    errors.appendLine("Ocorreu um erro ao executar ${sql.name}\nErro -> ${e.message}\nTempo decorrido -> " + "${((Instant.now().toEpochMilli() - startInstant.toEpochMilli()))} ms")
                    if (sql.mailTo.isNotEmpty() && mailer != null) {
                        mailer!!.send(sql.mailTo, "Rundeck SQL Failed - ${sql.name} - $SHEET_RANGE", "${e.message}")
                    }
                }
            }
            println("Finalizado")
            if (errors.isNotEmpty()) println("Resumo de erros \n$errors")
             exitProcess(0)
        }

        private fun executeSQL(sql: String): MutableList<Long> {
            val connection = DriverManager.getConnection(DATABASE_ENDPOINT, DATABASE_USER, DATABASE_KEY)
            val transact: Statement = connection.createStatement()
            transact.queryTimeout = 120

            val queryList = sql.split(";").toMutableList()
            queryList.removeAt(queryList.lastIndex)
            queryList.indices.forEach { i -> transact.addBatch(queryList[i]) }

            val affectedRows: MutableList<Long>
            try {
                affectedRows = transact.executeLargeBatch().toMutableList()
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