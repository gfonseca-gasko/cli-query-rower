package service

import org.springframework.stereotype.Service
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.collections.set

@Service
class Mailer(val mailFrom: String, val mailPassword: String) {

    private val props = Properties()
    private val session = Session.getDefaultInstance(props, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(mailFrom, mailPassword)
        }
    })
    val mimeMessage = MimeMessage(session)

    init {
        session.debug = false
        putIfMissing(props, "mail.smtp.host", "smtp.gmail.com")
        putIfMissing(props, "mail.smtp.port", "587")
        putIfMissing(props, "mail.smtp.auth", "true")
        putIfMissing(props, "mail.smtp.starttls.enable", "true")
    }

    private fun putIfMissing(props: Properties, key: String, value: String) {
        if (!props.containsKey(key)) {
            props[key] = value
        }
    }

    fun send(emailTo: String, subject: String, message: String) {
        try {
            mimeMessage.setFrom(InternetAddress(mailFrom))
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailTo, false))
            mimeMessage.setText(message)
            mimeMessage.subject = subject
            mimeMessage.sentDate = Date()

            val smtpTransport = session.getTransport("smtp")
            smtpTransport.connect()
            smtpTransport.sendMessage(mimeMessage, mimeMessage.allRecipients)
            smtpTransport.close()

        } catch (messagingException: MessagingException) {
            messagingException.printStackTrace()
        }
    }

}