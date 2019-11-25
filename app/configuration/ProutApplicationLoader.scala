package configuration

import play.api.ApplicationLoader.Context
import play.api.{ApplicationLoader, LoggerConfigurator}

class ProutApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
    new ProutApplicationComponents(context).application
  }
}