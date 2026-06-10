package no.nav.tsm.core

import io.ktor.server.plugins.di.*
import kotlin.reflect.KClass
inline fun <reified T : Any> DependencyRegistry.provideDynamic(
    local: KClass<out T>,
    cloud: KClass<out T>,
) {
    provide<T> {
        create(when (resolve<Environment>().runtime.env) {
            RuntimeEnvironments.LOCAL -> local
            else -> cloud
        })
    }
}
