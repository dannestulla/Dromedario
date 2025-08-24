package br.gohan.dromedario

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
