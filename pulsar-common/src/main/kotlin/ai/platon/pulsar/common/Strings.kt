package ai.platon.pulsar.common

import kotlin.reflect.KClass

fun readableClassName(obj: Any): String {
    val names = when (obj) {
        is Class<*> -> obj.name.split(".")
        is KClass<*> -> obj.java.name.split(".")
        else -> obj::class.java.name.split(".")
    }

    val size = names.size
    return names.mapIndexed { i, n -> n.takeIf { i >= size - 2 }?:n.substring(0, 1) }.joinToString(".")
}

fun prependReadableClassName(obj: Any, name: String, separator: String = "."): String {
    return "${readableClassName(obj)}$separator$name"
}

fun prependReadableClassName(obj: Any, ident: String, name: String, separator: String): String {
    if (ident.isBlank()) {
        return prependReadableClassName(obj, name, separator)
    }

    val prefix = readableClassName(obj)
    return "$prefix$separator$ident$separator$name"
}
