package br.gohan.dromedario

import br.gohan.dromedario.presenter.ClientSharedViewModel
import org.koin.dsl.module

// Common module that all platforms can use
val sharedModule = module {
    single { ClientSharedViewModel(client = get()) }
}