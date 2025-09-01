package br.gohan.dromedario

import br.gohan.dromedario.presenter.ClientSharedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Common module that all platforms can use
val sharedModule = module {

    viewModel { ClientSharedViewModel(
        client = get()
    ) }
}