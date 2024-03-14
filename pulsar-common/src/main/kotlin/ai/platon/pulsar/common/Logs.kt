package ai.platon.pulsar.common

import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

fun <T : Any> getLogger(clazz: KClass<T>): Logger = getLogger(clazz, "")

fun <T : Any> getLogger(clazz: KClass<T>, postfix: String): Logger = LoggerFactory.getLogger(clazz.java.name + postfix)

fun getLogger(target: Any): Logger = getLogger(target, "")

fun getLogger(target: Any, postfix: String): Logger = when (target) {
    is Logger -> target
    is KClass<*> -> LoggerFactory.getLogger(target.java.name + postfix)
    is Class<*> -> LoggerFactory.getLogger(target.name + postfix)
    is String -> LoggerFactory.getLogger(target + postfix)
    else -> LoggerFactory.getLogger(target::class.java.name + postfix)
}

fun getTracer(target: Any): Logger? = if (target is Logger) {
    target.takeIf { it.isTraceEnabled }
} else {
    getLogger(target).takeIf { it.isTraceEnabled }
}

fun getRandomLogger(): Logger = LoggerFactory.getLogger(RandomStringUtils.randomAlphabetic(8))

fun catastrophicError(t: Throwable, message: String, vararg args: Any?) {
    System.err.println("Logging system crashed.")
    System.err.println("Failed to log warning message: $message")
    t.printStackTrace()
}

fun warn(target: Any, message: String, vararg args: Any?) {
    getLogger(target).warn(message, *args)
}

fun warn(target: Any, t: Throwable, message: String, vararg args: Any?) {
    getLogger(target).warn(message, t, *args)
}

fun warnInterruptible(target: Any, t: Throwable) = warnInterruptible(target, t, t.stringify())

fun warnInterruptible(target: Any, t: Throwable, message: String, vararg args: Any?) {
    try {
        getLogger(target).warn(message, *args)
    } catch (t2: Throwable) {
        catastrophicError(t2, message, *args)
    }
    
    if (t is InterruptedException) {
        // Preserve interrupt status
        Thread.currentThread().interrupt()
    }
}

fun warnUnexpected(target: Any, t: Throwable) = warnUnexpected(target, t, t.stringify())

fun warnUnexpected(target: Any, t: Throwable, message: String, vararg args: Any?) {
    try {
        val logger = getLogger(target)
        val message1 = """
The exception is unexpected, it's best managed within custom code, such as event handlers.
        """.trimIndent()
        logger.warn("$message1\n$message", *args)
    } catch (t2: Throwable) {
        catastrophicError(t2, message, *args)
    }
    
    if (t is InterruptedException) {
        // Preserve interrupt status
        Thread.currentThread().interrupt()
    }
}

/**
 * Log a warning message for a close method that throws an exception.
 *
 * It's very common for the close method to suppress exceptions and just log it, but some exceptions should be
 * carefully handled, such as InterruptedException. It's strongly advised to not have the close method throw
 * InterruptedException, but it's not guaranteed, so we suppress it and log a warning message if it happens.
 *
 * @param target the object that is being closed
 * @param t the exception thrown by the close method
 * */
fun warnForClose(target: Any, t: Throwable) = warnForClose(target, t, t.stringify())

/**
 * Log a warning message for a close method that throws an exception.
 *
 * It's very common for the close method to suppress exceptions and just log it, but some exceptions should be
 * carefully handled, such as InterruptedException. It's strongly advised to not have the close method throw
 * InterruptedException, but it's not guaranteed, so we suppress it and log a warning message if it happens.
 *
 * @param target the object that is being closed
 * @param t the exception thrown by the close method
 * @param message the message to log
 * */
fun warnForClose(target: Any, t: Throwable, message: String, vararg args: Any?) {
    var logger: Logger? = null
    try {
        logger = getLogger(target)
        logger.warn(message, *args)
    } catch (t2: Throwable) {
        catastrophicError(t2, message, *args)
    }

    if (t is InterruptedException) {
        // Preserve interrupt status
        Thread.currentThread().interrupt()
        
        val message2 = """
                 * <p><em>Implementers of AutoClosable interface are strongly advised
                 * to not have the {@code close} method throw {@link
                 * InterruptedException}.</em>
                 *
                 * This exception interacts with a thread's interrupted status,
                 * and runtime misbehavior is likely to occur if an {@code
                 * InterruptedException} is {@linkplain Throwable#addSuppressed
                 * suppressed}.
                 *
                 * More generally, if it would cause problems for an
                 * exception to be suppressed, the {@code AutoCloseable.close}
                 * method should not throw it.
        """
        
        logger?.warn(message2)
        logger?.warn(t.stringify())
    }
}
