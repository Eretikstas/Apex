package app.aaps.pump.atc3.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.atc3.Atc3PumpPlugin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class Atc3Module {

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1140)
    abstract fun bindAtc3PumpPlugin(plugin: Atc3PumpPlugin): PluginBase
}
