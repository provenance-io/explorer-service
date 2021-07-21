package io.provenance.explorer.domain.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

// Logger Extensions
fun logger(name: String = pkgName()): Logger = LoggerFactory.getLogger(name)
inline fun <reified T : Any> logger(clazz: KClass<T>): Logger = logger(clazz.jvmName)
inline fun <reified T : Any> T.logger(): Logger = logger(T::class)
fun pkgName(): String = object {}::class.java.`package`.name
